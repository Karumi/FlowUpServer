import com.google.inject.AbstractModule;
import com.google.inject.name.Names;
import datasources.elasticsearch.ElasticSearchDatasource;
import datasources.grafana.DashboardsClient;
import datasources.grafana.GrafanaClient;
import play.Configuration;
import play.Environment;
import usecases.MetricsDatasource;

public class Module extends AbstractModule {


    private final Environment environment;
    private final Configuration configuration;

    public Module(
            Environment environment,
            Configuration configuration) {
        this.environment = environment;
        this.configuration = configuration;
    }

    protected void configure() {
        Configuration elasticsearchConf = configuration.getConfig("elasticsearch");
        bind(Configuration.class)
                .annotatedWith(Names.named("elasticsearch"))
                .toInstance(elasticsearchConf);

        Configuration grafanaConf = configuration.getConfig("grafana");
        bind(Configuration.class)
                .annotatedWith(Names.named("grafana"))
                .toInstance(grafanaConf);

        Configuration airbrakeConf = configuration.getConfig("airbrake");
        bind(Configuration.class)
                .annotatedWith(Names.named("airbrake"))
                .toInstance(airbrakeConf);

        bind(MetricsDatasource.class)
                .to(ElasticSearchDatasource.class)
                .asEagerSingleton();

        bind(DashboardsClient.class)
                .to(GrafanaClient.class)
                .asEagerSingleton();
    }
}
