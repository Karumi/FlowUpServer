package usecases.repositories;

import com.feth.play.module.pa.user.AuthUser;
import com.feth.play.module.pa.user.EmailIdentity;
import datasources.database.OrganizationDatasource;
import datasources.database.UserDatasource;
import usecases.DashboardsClient;
import models.Organization;
import models.User;
import usecases.EmailSender;

import javax.inject.Inject;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class UserRepository {
    private final UserDatasource userDatasource;
    private final OrganizationDatasource organizationDatasource;
    private final DashboardsClient dashboardsClient;
    private final EmailSender emailSender;

    @Inject
    public UserRepository(UserDatasource userDatasource, OrganizationDatasource organizationDatasource, DashboardsClient dashboardsClient, EmailSender emailSender) {
        this.userDatasource = userDatasource;
        this.organizationDatasource = organizationDatasource;
        this.dashboardsClient = dashboardsClient;
        this.emailSender = emailSender;
    }

    public CompletionStage<User> create(AuthUser authUser) {
        boolean isActive = this.existsOrganizationByEmail(authUser);
        User user = userDatasource.create(authUser, isActive);
        CompletionStage<Boolean> sendEmailCompletionStage = sendSigningUpEmail(user);

        CompletionStage<User> dashboardsCompletionStage = joinOrganization(user).thenCompose(this::setupDashboards);

        return CompletableFuture.allOf(
                sendEmailCompletionStage.toCompletableFuture(),
                dashboardsCompletionStage.toCompletableFuture()
        ).thenApply(aVoid -> user);
    }

    private CompletionStage<User> joinOrganization(final User user) {
        return CompletableFuture.supplyAsync(() -> {
            Organization organization = findOrCreateOrganization(user);
            if (organization != null) {
                return joinOrganization(user, organization);
            }
            return user;
        });
    }

    private CompletionStage<User> setupDashboards(User user) {
        return dashboardsClient.createUser(user).thenCompose(userWithGrafana -> {
                return joinApplicationDashboards(userWithGrafana, userWithGrafana.getOrganizations().get(0));
            });
    }

    private CompletionStage<Boolean> sendSigningUpEmail(User user) {
        if (!user.isActive()) {
            return emailSender.sendSigningUpDisabledMessage(user);
        } else {
            return emailSender.sendSignUpApprovedMessage(user);
        }
    }

    private CompletionStage<User> joinApplicationDashboards(User user, Organization organization) {
        if (organization.getApplications().isEmpty()) {
            return CompletableFuture.completedFuture(user);
        }
        return addUserToApplications(user, organization).thenCompose(aVoid -> {
            return dashboardsClient.switchUserContext(user, organization.getApplications().get(0)).thenCompose(application -> {
                return dashboardsClient.deleteUserInDefaultOrganisation(user);
            });
        });
    }

    private CompletableFuture<Void> addUserToApplications(User user, Organization organization) {
        CompletableFuture[] completionStages = organization.getApplications().stream().map(application -> {
            return dashboardsClient.addUserToOrganisation(user, application);
        }).toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(completionStages);
    }

    private Organization findOrCreateOrganization(User user) {
        String email = user.getEmail();
        if (email != null) {
            String[] split = email.split("@");
            if (split.length == 2) {
                String domain = split[1];
                Organization organization;
                if ("gmail.com".equals(domain) || "googlemail.com".equals(domain)) {
                    organization = organizationDatasource.create(split[0]);
                } else {
                    String googleAccount = "@" + domain;
                    organization = organizationDatasource.findByGoogleAccount(googleAccount);
                    if (organization == null) {
                        organization = organizationDatasource.create(domain, googleAccount);
                    }
                }
                return organization;
            }
        }
        return null;
    }

    private boolean existsOrganizationByEmail(AuthUser authUser) {
        if (authUser instanceof EmailIdentity) {
            EmailIdentity identity = (EmailIdentity) authUser;

            String[] split = identity.getEmail().split("@");
            if (split.length == 2) {
                String domain = split[1];
                String googleAccount = "@" + domain;
                Organization organization = organizationDatasource.findByGoogleAccount(googleAccount);
                return organization != null;
            }
        }
        return false;
    }

    private User joinOrganization(User user, Organization organization) {
        user.setOrganizations(Collections.singletonList(organization));
        user.update();
        return user;
    }

    public CompletionStage<Boolean> activateByUserId(UUID userId) {
        User user = User.find.byId(userId);
        if (user != null) {
            return emailSender.sendSignUpApprovedMessage(user).thenApply(emailSent -> {
                if (emailSent) {
                    user.setActive(true);
                    user.save();
                }
                return emailSent;
            });
        }
        return CompletableFuture.completedFuture(false);
    }
}
