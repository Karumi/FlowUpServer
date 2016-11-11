package usecases;

import datasources.elasticsearch.ElasticSearchDatasource;
import models.Application;
import usecases.models.StatCard;
import usecases.models.Threshold;

import javax.inject.Inject;
import java.util.concurrent.CompletionStage;

public class GetFramePerSecond extends GetLineChart {
    @Inject
    public GetFramePerSecond(ElasticSearchDatasource elasticSearchDatasource) {
        super(elasticSearchDatasource);
    }

    public CompletionStage<StatCard> execute(Application application) {
        return super.execute(application, "FramesPerSecond.p10", "Frames per second", "%");
    }

    @Override
    Threshold getThreshold(Double average) {
        Threshold threshold;
        if (average != null) {
            if (average > 50) {
                threshold = Threshold.OK;
            } else if (average > 40) {
                threshold = Threshold.WARNING;
            } else {
                threshold = Threshold.SEVERE;
            }
        } else {
            threshold = Threshold.NO_DATA;
        }
        return threshold;
    }
}
