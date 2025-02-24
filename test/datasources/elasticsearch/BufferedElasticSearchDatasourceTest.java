package datasources.elasticsearch;

import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import controllers.api.DataPointMapper;
import controllers.api.ReportRequest;
import models.Application;
import models.Platform;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import usecases.InsertResult;
import usecases.models.DataPoint;
import usecases.models.Metric;
import usecases.models.Report;
import utils.WithFlowUpApplication;
import utils.WithResources;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.function.IntFunction;
import java.util.stream.IntStream;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.verify;
import static play.inject.Bindings.bind;


@RunWith(MockitoJUnitRunner.class)
public class BufferedElasticSearchDatasourceTest extends WithFlowUpApplication implements WithResources {

    @Mock
    private AmazonSQS amazonSQS;

    @Captor
    private ArgumentCaptor<SendMessageRequest> sendMessageRequestArgumentCaptor;


    @Override
    protected play.Application provideApplication() {
        return getGuiceApplicationBuilder()
                .overrides(bind(AmazonSQS.class).toInstance(amazonSQS))
                .configure("elasticsearch.min_request_list_size", 6)
                .configure("elasticsearch.queue_small_request_enabled", true)
                .configure("elasticsearch.dry_run_queue_enabled", false)
                .build();
    }

    @Test
    public void givenAUniqueReportRequestWhenWriteDataPointsIsCalledThenReportIsBufferedAndReturnEmptyList() throws ExecutionException, InterruptedException {
        Report report = givenAReportWithXMetrics(1);
        Application application = givenAnyApplicationWithOrganizationId(UUID.fromString("9cdc0b15-bdb0-4209-a3d2-3bc7012d9793"));
        setupSuccessfulElasticsearchClient();
        ElasticSearchDatasource elasticSearchDatasource = app.injector().instanceOf(ElasticSearchDatasource.class);

        CompletionStage<InsertResult> insertResultCompletionStage = elasticSearchDatasource.writeDataPoints(report, application);
        InsertResult insertResult = insertResultCompletionStage.toCompletableFuture().get();

        verify(amazonSQS).sendMessage(sendMessageRequestArgumentCaptor.capture());
        assertEquals(givenJsonSerializedIndexRequests(), sendMessageRequestArgumentCaptor.getValue().getMessageBody());
        assertEquals(insertResult, InsertResult.successEmpty());
    }

    @Test
    public void givenAReportRequestWithTwoRecollectWhenWriteDataPointsICalledThenReportIsWritten() throws ExecutionException, InterruptedException {
        Report report = givenAReportWithXMetrics(2);
        Application application = givenAnyApplication();
        setupSuccessfulElasticsearchClient();
        ElasticSearchDatasource elasticSearchDatasource = app.injector().instanceOf(ElasticSearchDatasource.class);

        CompletionStage<InsertResult> insertResultCompletionStage = elasticSearchDatasource.writeDataPoints(report, application);
        InsertResult insertResult = insertResultCompletionStage.toCompletableFuture().get();

        assertTrue(insertResult.getItems().size() > 0);
    }

    @NotNull
    private Report givenAReportWithXMetrics(int nbMetrics) {
        String organizationIdentifier = "3e02e6b9-3a33-4113-ae78-7d37f11ca3bf";

        ReportRequest reportRequest = resourceFromFile("androidsdk/simpleReportRequestBody.json", ReportRequest.class);

        DataPointMapper dataPointMapper = new DataPointMapper();

        List<Metric> metrics = new ArrayList<>();
        metrics.add(new Metric("network_data", getAccumulatedDataPoints(nbMetrics, value -> dataPointMapper.mapNetwork(reportRequest))));
        metrics.add(new Metric("ui_data", getAccumulatedDataPoints(nbMetrics, value -> dataPointMapper.mapUi(reportRequest))));
        metrics.add(new Metric("cpu_data", getAccumulatedDataPoints(nbMetrics, value -> dataPointMapper.mapCpu(reportRequest))));
        metrics.add(new Metric("memory_data", getAccumulatedDataPoints(nbMetrics, value -> dataPointMapper.mapMemory(reportRequest))));
        metrics.add(new Metric("disk_data", getAccumulatedDataPoints(nbMetrics, value -> dataPointMapper.mapDisk(reportRequest))));
        return new Report(organizationIdentifier, "io.flowup.app", metrics, new Report.Metadata(false, false), Platform.ANDROID);
    }

    private List<DataPoint> getAccumulatedDataPoints(int nbMetrics, IntFunction<List<DataPoint>> mapper) {
        return IntStream.range(0, nbMetrics)
                .mapToObj(mapper)
                .reduce(new ArrayList<>(), (dataPoints, dataPoints2) -> {
                    dataPoints.addAll(dataPoints2);
                    return dataPoints;
                });
    }
}
