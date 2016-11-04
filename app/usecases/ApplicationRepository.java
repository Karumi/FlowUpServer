package usecases;

import datasources.database.ApiKeyDatasource;
import datasources.database.ApplicationDatasource;
import datasources.grafana.DashboardsClient;
import models.ApiKey;
import models.Application;
import play.cache.CacheApi;

import javax.inject.Inject;

import static java.util.stream.Collectors.toList;

class ApplicationRepository {
    private final ApiKeyDatasource apiKeyDatasource;
    private final ApplicationDatasource applicationDatasource;
    private final DashboardsClient dashboardsClient;
    private final CacheApi cacheApi;

    @Inject
    ApplicationRepository(ApiKeyDatasource apiKeyDatasource, ApplicationDatasource applicationDatasource, DashboardsClient dashboardsClient, CacheApi cacheApi) {
        this.apiKeyDatasource = apiKeyDatasource;
        this.applicationDatasource = applicationDatasource;
        this.dashboardsClient = dashboardsClient;
        this.cacheApi = cacheApi;
    }

    boolean exist(String apiKey, String appPackage) {
        String cacheKey = getCacheKey(apiKey, appPackage);
        return cacheApi.getOrElse(cacheKey, () -> applicationDatasource.existByApiKeyAndAppPackage(apiKey, appPackage));
    }

    private String getCacheKey(String apiKey, String appPackage) {
        return "exist-" + apiKey + "-" + appPackage;
    }

    Application create(String apiKeyValue, String appPackage) {
        ApiKey apiKey = apiKeyDatasource.findByApiKeyValue(apiKeyValue);

        Application application = applicationDatasource.create(appPackage, apiKey);

        String cacheKey = getCacheKey(apiKeyValue, appPackage);
        cacheApi.set(cacheKey, true);

        dashboardsClient.createOrg(application).thenApply(grafanaResponse -> {

            dashboardsClient.createDatasource(application);

            return application.getOrganization().getMembers().stream().map(user -> {
                return dashboardsClient.addUserToOrganisation(user, application).thenApply(grafanaResponse1 -> {
                    return dashboardsClient.deleteUserInDefaultOrganisation(user);
                });
            }).collect(toList());
        });

        return application;
    }
}
