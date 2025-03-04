package service;

import com.feth.play.module.pa.PlayAuthenticate;
import com.feth.play.module.pa.service.AbstractUserService;
import com.feth.play.module.pa.user.AuthUser;
import com.feth.play.module.pa.user.AuthUserIdentity;
import models.User;
import play.Logger;
import usecases.CreateUser;
import usecases.repositories.UserRepository;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.concurrent.ExecutionException;

@Singleton
public class FlowUpUserService extends AbstractUserService {

    private final CreateUser createUser;

    @Inject
    public FlowUpUserService(PlayAuthenticate auth, UserRepository userRepository, CreateUser createUser) {
        super(auth);
        this.createUser = createUser;
    }

    private Object createUserSync(AuthUser authUser) {
        try {
            User user = createUser.execute(authUser).toCompletableFuture().get();
            return user.getId();
        } catch (InterruptedException | ExecutionException e) {
            Logger.error(e.getMessage());
            return null;
        }
    }

    @Override
    public Object save(AuthUser authUser) {
        final boolean isLinked = User.existsByAuthUserIdentity(authUser);
        if (!isLinked) {
            return createUserSync(authUser);
        } else {
            // we have this user already, so return null
            return null;
        }
    }

    @Override
    public Object getLocalIdentity(final AuthUserIdentity identity) {
        // For production: Caching might be a good idea here...
        // ...and dont forget to sync the cache when users get deactivated/deleted
        final User u = User.findByAuthUserIdentity(identity);
        if(u != null) {
            return u.getId();
        } else {
            return null;
        }
    }

    @Override
    public AuthUser merge(final AuthUser newUser, final AuthUser oldUser) {
        if (!oldUser.equals(newUser)) {
            User.merge(oldUser, newUser);
        }
        return oldUser;
    }

    @Override
    public AuthUser link(final AuthUser oldUser, final AuthUser newUser) {
        User.addLinkedAccount(oldUser, newUser);
        return null;
    }

}