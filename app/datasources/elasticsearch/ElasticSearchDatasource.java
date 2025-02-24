package datasources.elasticsearch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.spotify.futures.CompletableFutures;
import datasources.sqs.SQSClient;
import models.Application;
import org.jetbrains.annotations.NotNull;
import play.Configuration;
import play.Logger;
import play.libs.F;
import play.libs.Json;
import usecases.InsertResult;
import usecases.MetricsDatasource;
import usecases.SingleStatQuery;
import usecases.models.*;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

import static java.util.concurrent.CompletableFuture.completedFuture;

public class ElasticSearchDatasource implements MetricsDatasource {

    private static final String FLOWUP = "flowup";
    private static final String DELIMITER = "-";
    private static final String EMPTY_STRING = "";
    private final ElasticsearchClient elasticsearchClient;
    private final SQSClient sqsClient;
    private final Integer minRequestListSize;
    private final boolean queueSmallRequestEnabled;
    private final boolean dryRunSQSMessagesEnabled;

    @Inject
    public ElasticSearchDatasource(ElasticsearchClient elasticsearchClient, @Named("elasticsearch") Configuration elasticsearchConf, SQSClient sqsClient) {
        this.elasticsearchClient = elasticsearchClient;
        this.sqsClient = sqsClient;
        this.minRequestListSize = elasticsearchConf.getInt("min_request_list_size", 0);
        this.queueSmallRequestEnabled = elasticsearchConf.getBoolean("queue_small_request_enabled", false);
        this.dryRunSQSMessagesEnabled = elasticsearchConf.getBoolean("dry_run_queue_enabled", true);
    }

    @Override
    public CompletionStage<InsertResult> writeDataPoints(Report report, Application application) {
        List<IndexRequest> indexRequestList = populateIndexRequest(report, application);

        if (indexRequestList.size() < minRequestListSize && queueSmallRequestEnabled) {
            String jsonPayload = Json.toJson(indexRequestList).toString();
            if (sqsClient.hasMessageBodyAValidLength(jsonPayload)) {
                sqsClient.sendMessage(jsonPayload);
                if (this.dryRunSQSMessagesEnabled) {
                    return postBulkIndexRequests(indexRequestList);
                } else {
                    return completedFuture(InsertResult.successEmpty());
                }
            } else {
                return postBulkIndexRequests(indexRequestList);
            }
        } else {
            return postBulkIndexRequests(indexRequestList);
        }
    }

    public CompletionStage<Void> deleteOldDataPoints() {
        CompletionStage<Optional<SearchResponse>> response = elasticsearchClient.getOldDataPoints();
        return response.thenCompose(searchResponse -> {
            if (!searchResponse.isPresent()) {
                Logger.info("No old data points found");
                return CompletableFuture.completedFuture(null);
            }

            Hits hits = searchResponse.get().getHits();
            List<DeleteRequest> indexesToDelete = mapToDeleteRequests(hits);
            return elasticsearchClient.deleteBulk(indexesToDelete).thenCompose(deleteResponse -> {
                if (hits.getTotal() > hits.getHits().size()) {
                    return deleteOldDataPoints();
                }
                return CompletableFuture.completedFuture(null);
            });
        });
    }

    private CompletionStage<InsertResult> postBulkIndexRequests(List<IndexRequest> indexRequestList) {
        if (!indexRequestList.isEmpty()) {
            return elasticsearchClient.postBulk(indexRequestList).thenApply(this::processBulkResponse);
        } else {
            return completedFuture(InsertResult.successEmpty());
        }
    }

    private ObjectNode mapSource(DataPoint datapoint) {
        ObjectNode source = Json.newObject()
                .put("@timestamp", datapoint.getTimestamp().getTime());


        for (F.Tuple<String, Value> measurement : datapoint.getMeasurements()) {
            if (measurement._2 != null) {
                if (measurement._2 instanceof BasicValue) {
                    BasicValue basicValue = (BasicValue) measurement._2;
                    source.put(measurement._1, basicValue.getValue());
                } else if (measurement._2 instanceof StatisticalValue) {
                    StatisticalValue basicValue = (StatisticalValue) measurement._2;
                    ObjectNode statisticalValue = Json.newObject()
                            .put("mean", basicValue.getMean())
                            .put("p10", basicValue.getP10())
                            .put("p90", basicValue.getP90());
                    source.set(measurement._1, statisticalValue);
                }
            }
        }

        for (F.Tuple<String, String> tag : datapoint.getTags()) {
            source.put(tag._1, tag._2);
        }
        return source;
    }

    private List<IndexRequest> populateIndexRequest(Report report, Application application) {
        return report.getMetrics().stream().map(metric ->
                metric.getDataPoints().stream().map(datapoint -> {
                    IndexRequest indexRequest = new IndexRequest(indexName(report.getAppPackage(), application.getOrganization().getId().toString()), metric.getName());

                    ObjectNode source = mapSource(datapoint);

                    indexRequest.setSource(source);
                    return indexRequest;
                }).collect(Collectors.toList())).reduce(new ArrayList<>(), (indexRequests, indexRequests2) -> {
            indexRequests.addAll(indexRequests2);
            return indexRequests;
        });
    }

    private InsertResult processBulkResponse(BulkResponse bulkResponse) {
        List<InsertResult.MetricResult> items = new ArrayList<>();
        for (BulkItemResponse item : bulkResponse.getItems()) {
            String name = item.getIndex().replace(FLOWUP + DELIMITER, "");
            ActionWriteResponse.ShardInfo shardInfo = item.getResponse().getShardInfo();
            int successful;
            if (shardInfo != null) {
                successful = shardInfo.getSuccessful();
            } else {
                successful = 0;
            }
            items.add(new InsertResult.MetricResult(EMPTY_STRING, successful));
        }

        if (bulkResponse.hasFailures()) {
            Logger.error(bulkResponse.toString());
        }

        return new InsertResult(bulkResponse.isError(), bulkResponse.hasFailures(), items);
    }

    public CompletionStage<LineChart> singleStat(SingleStatQuery singleStatQuery) {
        long gteEpochMillis = singleStatQuery.getFrom().toEpochMilli();
        long lteEpochMillis = singleStatQuery.getTo().toEpochMilli();

        SearchQuery searchQuery = prepareSearchQuery(singleStatQuery.getApplication(), gteEpochMillis, lteEpochMillis, singleStatQuery.getField(), singleStatQuery.getQueryStringValue());

        return elasticsearchClient.multiSearch(Collections.singletonList(searchQuery)).thenApply(this::processMSearchResponse);
    }

    @Override
    public CompletionStage<List<LineChart>> statGroupBy(SingleStatQuery singleStatQuery, String groupBy) {
        long gteEpochMillis = singleStatQuery.getFrom().toEpochMilli();
        long lteEpochMillis = singleStatQuery.getTo().toEpochMilli();

        SearchQuery searchQuery = prepareSearchQueryGroupBy(singleStatQuery.getApplication(), gteEpochMillis, lteEpochMillis, singleStatQuery.getField(), singleStatQuery.getQueryStringValue(), groupBy);

        return elasticsearchClient.multiSearch(Collections.singletonList(searchQuery)).thenApply(this::processMSearchGroupByResponse);
    }

    @Override
    public CompletionStage<Boolean> processSQS() {
        List<IndexRequest> indexRequestList = new ArrayList<>();
        return sqsClient.receiveMessages(messages -> {
            messages.forEach(message -> {
                for (JsonNode jsonNode : Json.parse(message)) {
                    IndexRequest indexRequest = new IndexRequest(Json.fromJson(jsonNode.get("action"), IndexAction.class));
                    indexRequest.setSource(jsonNode.get("source"));
                    indexRequestList.add(indexRequest);
                }
            });

            if (this.dryRunSQSMessagesEnabled) {
                return completedFuture(true);
            } else {
                return this.postBulkIndexRequests(indexRequestList).thenApply(insertResult ->
                        !insertResult.isError() && !insertResult.isHasFailures());
            }
        });
    }

    private LineChart processMSearchResponse(MSearchResponse mSearchResponse) {
        JsonNode aggregations = getFirstAggregations(mSearchResponse);
        if (aggregations != null) {
            return processLineChartAggregation(aggregations);
        }

        return new LineChart(Collections.emptyList(), Collections.emptyList());
    }

    private List<LineChart> processMSearchGroupByResponse(MSearchResponse mSearchResponse) {
        List<LineChart> lineCharts = new ArrayList<>();
        JsonNode aggregations = getFirstAggregations(mSearchResponse);
        if (aggregations != null) {
            for (JsonNode bucket : aggregations.get("3").get("buckets")) {
                LineChart lineChart = processLineChartAggregation(bucket);
                lineChart.setName(bucket.get("key").asText());
                lineCharts.add(lineChart);
            }
        }
        return lineCharts;
    }

    private LineChart processLineChartAggregation(JsonNode aggregations) {
        List<String> keys = new ArrayList<>();
        List<Double> values = new ArrayList<>();
        JsonNode jsonNode = aggregations.get("2");
        if (jsonNode != null) {
            for (JsonNode bucket : jsonNode.get("buckets")) {
                keys.add(bucket.get("key").asText());
                JsonNode value = bucket.get("1").get("value");
                values.add(value instanceof NullNode ? null : value.asDouble());
            }
        }
        return new LineChart(keys, values);
    }

    private JsonNode getFirstAggregations(MSearchResponse mSearchResponse) {
        if (mSearchResponse.getResponses().size() > 0) {
            SearchResponse searchResponse = mSearchResponse.getResponses().get(0);
            return searchResponse.getAggregations();
        }
        return null;
    }

    @NotNull
    private SearchQuery prepareSearchQuery(Application application, long gteEpochMillis, long lteEpochMillis, String field, String queryStringValue) {
        SearchBody searchBody = getSearchBody(gteEpochMillis, lteEpochMillis, queryStringValue);

        Aggregation aggsObject = getSingleStatAggregation(gteEpochMillis, lteEpochMillis, field);
        searchBody.setAggs(AggregationMap.singleton("2", aggsObject));

        return getSearchQuery(application, searchBody);
    }

    private SearchQuery prepareSearchQueryGroupBy(Application application, long gteEpochMillis, long lteEpochMillis, String field, String queryStringValue, String groupBy) {
        SearchBody searchBody = getSearchBody(gteEpochMillis, lteEpochMillis, queryStringValue);

        Aggregation aggsObject = getSingleStatGroupByAggregation(gteEpochMillis, lteEpochMillis, field, groupBy);
        searchBody.setAggs(AggregationMap.singleton("3", aggsObject));

        return getSearchQuery(application, searchBody);
    }

    private Aggregation getSingleStatGroupByAggregation(long gteEpochMillis, long lteEpochMillis, String field, String groupBy) {
        Aggregation aggsObject = new Aggregation();

        TermsAggregation termsAggregation = new TermsAggregation();
        termsAggregation.setField(groupBy);
        termsAggregation.setSize(4);
        termsAggregation.setOrder(Collections.singletonMap("_term", "desc"));
        aggsObject.setTerms(termsAggregation);
        aggsObject.setAggs(AggregationMap.singleton("2", getSingleStatAggregation(gteEpochMillis, lteEpochMillis, field)));

        return aggsObject;
    }

    @NotNull
    private SearchBody getSearchBody(long gteEpochMillis, long lteEpochMillis, String queryStringValue) {
        SearchBody searchBody = new SearchBody();
        searchBody.setSize(0);
        SearchBodyQuery query = new SearchBodyQuery();
        SearchBodyQueryFiltered filtered = new SearchBodyQueryFiltered();
        SearchBodyQueryFilteredQuery filteredQuery = new SearchBodyQueryFilteredQuery();
        QueryString queryString = new QueryString();
        queryString.setAnalyzeWildcard(true);
        queryString.setQuery(queryStringValue);
        filteredQuery.setQueryString(queryString);
        filtered.setQuery(filteredQuery);
        ObjectNode filter = Json.newObject();
        JsonNode range = Json.newObject()
                .set("range", Json.newObject()
                        .set("@timestamp", Json.newObject()
                                .put("gte", gteEpochMillis)
                                .put("lte", lteEpochMillis)
                                .put("format", "epoch_millis")));
        filter.set("bool", Json.newObject().set("must", Json.newArray().add(range)));
        filtered.setFilter(filter);
        query.setFiltered(filtered);
        searchBody.setQuery(query);
        return searchBody;
    }

    @NotNull
    private SearchQuery getSearchQuery(Application application, SearchBody searchBody) {
        SearchQuery searchQuery = new SearchQuery();

        SearchIndex searchIndex = new SearchIndex();
        searchIndex.setIndex(indexName(application.getAppPackage(), application.getOrganization().getId().toString()));
        searchIndex.setIgnoreUnavailable(true);
        searchIndex.setSearchType("count");
        searchQuery.setSearchIndex(searchIndex);
        searchQuery.setSearchBody(searchBody);
        return searchQuery;
    }

    @NotNull
    private Aggregation getSingleStatAggregation(long gteEpochMillis, long lteEpochMillis, String field) {
        Aggregation aggsObject = new Aggregation();

        DateHistogramAggregation dateHistogram = new DateHistogramAggregation(
                "4m",
                "@timestamp",
                0,
                "epoch_millis",
                new ExtendedBounds(gteEpochMillis, lteEpochMillis));
        aggsObject.setDateHistogram(dateHistogram);

        Aggregation aggregation = new Aggregation();
        aggregation.setAvg(new AvgAggregation(field));
        aggsObject.setAggs(AggregationMap.singleton("1", aggregation));
        return aggsObject;
    }

    @NotNull
    public static String indexName(String appPackage, String organizationId) {
        // iOS packages are stored with a " - iOS" suffix so we are stripping it out when accessing elasticsearch
        String normalizedAppPackage = appPackage.endsWith(Application.IOS_APPLICATION_SUFFIX)
                ? appPackage.substring(0, appPackage.length() - Application.IOS_APPLICATION_SUFFIX.length())
                : appPackage;
        return String.join(DELIMITER, FLOWUP, organizationId, normalizedAppPackage).toLowerCase();
    }

    private List<DeleteRequest> mapToDeleteRequests(Hits hits) {
        List<DeleteRequest> deletes = new LinkedList<>();
        for (JsonNode hit : hits.getHits()) {
            String index = hit.get("_index").asText();
            String type = hit.get("_type").asText();
            String id = hit.get("_id").asText();
            deletes.add(new DeleteRequest(index, type, id));
        }
        return deletes;
    }

    public CompletionStage<Void> deleteEmptyIndexes() {
        return elasticsearchClient.getIndexes().thenCompose(indexList -> {
            indexList = indexList.stream()
                    .filter(Index::isEmpty)
                    .collect(Collectors.toList());
            if (indexList.isEmpty()) {
                return CompletableFuture.completedFuture(null);
            }
            List<CompletionStage<Void>> deletes = indexList.stream()
                    .map(index -> elasticsearchClient.deleteIndex(index.getName()))
                    .collect(Collectors.toList());
            return CompletableFutures.allAsList(deletes).thenApply(result -> null);
        });
    }
}
