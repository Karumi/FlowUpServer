package datasources.database;

import com.feth.play.module.pa.user.AuthUser;
import com.feth.play.module.pa.user.EmailIdentity;
import com.feth.play.module.pa.user.NameIdentity;
import models.LinkedAccount;
import models.User;

import java.util.Collections;
import java.util.UUID;

public class UserDatasource {

    public User create(AuthUser authUser) {
        return this.create(authUser, true);
    }

    public User create(AuthUser authUser, boolean active) {
        final User user = new User();
        user.setActive(active);
        user.setLinkedAccounts(Collections.singletonList(LinkedAccount
                .create(authUser)));

        if (authUser instanceof EmailIdentity) {
            final EmailIdentity identity = (EmailIdentity) authUser;
            user.setEmail(identity.getEmail());
            user.setEmailValidated(false);
        }

        if (authUser instanceof NameIdentity) {
            final NameIdentity identity = (NameIdentity) authUser;
            final String name = identity.getName();
            if (name != null) {
                user.setName(name);
            }
        }

        user.save();
        return user;
    }

    public User findById(UUID userId) {
        return User.find.byId(userId);
    }

    public void setActive(User user) {
        user.setActive(true);
        user.save();
    }
}
