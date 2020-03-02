package stroom.authentication.resources.user.v1;

import com.google.common.base.Strings;
import org.apache.commons.lang3.tuple.Pair;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.JSONFormat;
import org.jooq.Result;
import stroom.authentication.exceptions.ConflictException;
import stroom.authentication.impl.db.UserDao;
import stroom.event.logging.api.StroomEventLoggingService;
import stroom.security.api.SecurityContext;
import stroom.security.shared.PermissionException;
import stroom.security.shared.PermissionNames;

import javax.inject.Inject;
import javax.ws.rs.BadRequestException;

import static stroom.auth.db.Tables.USERS;

public class UserService {
    private UserDao userDao;
    private SecurityContext securityContext;
    private StroomEventLoggingService stroomEventLoggingService;

    @Inject
    UserService(final UserDao userDao,
                final SecurityContext securityContext,
                final StroomEventLoggingService stroomEventLoggingService){
        this.userDao = userDao;
        this.securityContext = securityContext;
        this.stroomEventLoggingService = stroomEventLoggingService;
    }

    public String getAllAsJson() {
        checkPermission();
        stroomEventLoggingService.createAction("GetAllUsers", "Read all users");
        return userDao.getAll();
    }

    public int create(User user){
        checkPermission();

        // Validate
        final String userId = securityContext.getUserId();
        Pair<Boolean, String> validationResults = User.isValidForCreate(user);
        boolean isUserValid = validationResults.getLeft();
        if (!isUserValid) {
            throw new BadRequestException(validationResults.getRight());
        }
        if (userDao.exists(user.getEmail())) {
            throw new ConflictException(UserValidationError.USER_ALREADY_EXISTS.getMessage());
        }

        // Set a default TODO: do this in the dao
        if (Strings.isNullOrEmpty(user.getState())) {
            user.setState(User.UserState.ENABLED.getStateText());
        }
        int newUserId = userDao.create(user, userId);

        stroomEventLoggingService.createAction("CreateUser", "Create a user");
        return newUserId;
    }

    public String search(String email){
        checkPermission();
        Result foundUsers = userDao.searchUsersForDisplay(email);
        String users = foundUsers.formatJSON((new JSONFormat())
            .header(false)
            .recordFormat(JSONFormat.RecordFormat.OBJECT));
        stroomEventLoggingService.createAction("SearchUsers", "Search for users by email");
        return users;
    }

    private void checkPermission() {
        if(!securityContext.hasAppPermission(PermissionNames.MANAGE_USERS_PERMISSION)) {
            throw new PermissionException(securityContext.getUserId(), "You do not have permission to manage users");
        }
    }

    private static Boolean doesUserAlreadyExist(DSLContext database, String email) {
        int countOfSameName = database
                .selectCount()
                .from(USERS)
                .where(new Condition[]{USERS.EMAIL.eq(email)})
                .fetchOne(0, Integer.TYPE);

        return countOfSameName > 0;
    }

}
