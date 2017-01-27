package controllers.admin;

import be.objectify.deadbolt.java.actions.Group;
import be.objectify.deadbolt.java.actions.Restrict;
import controllers.Secured;
import models.User;
import play.libs.concurrent.HttpExecutionContext;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.Results;
import play.mvc.Security;
import usecases.ActivateUser;

import javax.inject.Inject;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

@Security.Authenticated(Secured.class)
@Restrict(@Group("admin"))
public class UserController extends Controller {

    private final ActivateUser activateUser;
    private final HttpExecutionContext ec;

    @Inject
    public UserController(ActivateUser activateUser, HttpExecutionContext ec) {
        this.activateUser = activateUser;
        this.ec = ec;
    }

    /**
     * This result directly redirect to application home.
     */
    public Result GO_HOME = Results.redirect(
            controllers.admin.routes.UserController.list(0, "name", "asc", "")
    );

    /**
     * Handle default path requests, redirect to computers list
     */
    public Result index() {
        return GO_HOME;
    }

    /**
     * Display the paginated list of computers.
     *
     * @param page   Current page number (starts from 0)
     * @param sortBy Column to be sorted
     * @param order  Sort order (either asc or desc)
     * @param filter Filter applied on computer names
     */
    public Result list(int page, String sortBy, String order, String filter) {
        return ok(
                views.html.admin.user.list.render(
                        User.page(page, 10, sortBy, order, filter),
                        sortBy, order, filter
                )
        );
    }

    /**
     * Handle user deletion
     */
    public Result delete(String id) {
        UUID uuid = UUID.fromString(id);
        User.find.ref(uuid).delete();
        flash("success", "User has been deleted");
        return GO_HOME;
    }

    public CompletionStage<Result> activate(String id) {
        UUID uuid = UUID.fromString(id);
        return activateUser.execute(uuid).thenApplyAsync(isActive -> {
            if (isActive) {
                flash("success", "User has been activated");
            } else {
                flash("failure", "Something wrong happen!");
            }
            return GO_HOME;
        }, ec.current());
    }
}
