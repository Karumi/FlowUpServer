package datasources.grafana;

import models.Application;
import models.User;

import java.util.concurrent.CompletionStage;

public interface DashboardsClient {
    CompletionStage<User> createUser(User user);

    CompletionStage<Application> createOrg(Application application);

    CompletionStage<Application> addUserToOrganisation(User user, Application application);

    CompletionStage<User> deleteUserInDefaultOrganisation(User user);

    CompletionStage<Application> createDatasource(Application application);
}
