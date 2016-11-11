package usecases;

import datasources.elasticsearch.ElasticSearchDatasource;
import models.Application;
import usecases.models.LineChart;
import usecases.models.StatCard;

import javax.inject.Inject;
import java.util.concurrent.CompletionStage;

public class GetFramePerSecond extends GetLineChart {
    @Inject
    public GetFramePerSecond(ElasticSearchDatasource elasticSearchDatasource) {
        super(elasticSearchDatasource);
    }

    public CompletionStage<StatCard> execute(Application application) {
        return super.executeSingleStat(application, "FramesPerSecond.p10").thenApply(lineChart -> {
            double average = lineChart.getValues().stream().mapToDouble(a -> a).average().orElseGet(() -> 0.0);
            return new StatCard("Frame Per Second", average, null, lineChart);
        });
    }
}
