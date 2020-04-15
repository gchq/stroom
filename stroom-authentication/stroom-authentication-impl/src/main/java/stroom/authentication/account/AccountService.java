package stroom.authentication.account;

import com.google.common.base.Strings;
import org.apache.commons.lang3.tuple.Pair;
import org.jooq.JSONFormat;
import org.jooq.Result;
import org.mindrot.jbcrypt.BCrypt;
import stroom.authentication.exceptions.ConflictException;
import stroom.event.logging.api.StroomEventLoggingService;
import stroom.security.api.SecurityContext;
import stroom.security.shared.PermissionException;
import stroom.security.shared.PermissionNames;
import stroom.util.shared.ResultPage;

import javax.inject.Inject;
import javax.ws.rs.BadRequestException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Optional;

public class AccountService {
    private AccountDao accountDao;
    private SecurityContext securityContext;
    private StroomEventLoggingService stroomEventLoggingService;

    @Inject
    AccountService(final AccountDao accountDao,
                   final SecurityContext securityContext,
                   final StroomEventLoggingService stroomEventLoggingService) {
        this.accountDao = accountDao;
        this.securityContext = securityContext;
        this.stroomEventLoggingService = stroomEventLoggingService;
    }

    public ResultPage<Account> getAll() {
        checkPermission();
        stroomEventLoggingService.createAction("GetAllUsers", "Read all users");
        return accountDao.getAll();
    }

    public Optional<Account> get(int userId) {
        final String loggedInUser = securityContext.getUserId();

        Optional<Account> optionalUser = accountDao.get(userId);
        if (optionalUser.isPresent()) {
            Account foundAccount = optionalUser.get();
            // We only need to check auth permissions if the user is trying to access a different user.
            final boolean isUserAccessingThemselves = loggedInUser.equals(foundAccount.getEmail());
            boolean canManageUsers = securityContext.hasAppPermission(PermissionNames.MANAGE_USERS_PERMISSION);
            if (!isUserAccessingThemselves && !canManageUsers) {
                throw new RuntimeException("Unauthorized");
            }

            stroomEventLoggingService.createAction("GetById", "Get a user by ID");

        }
        return optionalUser;
    }

    public Optional<Account> get(String email) {
        checkPermission();
        return accountDao.get(email);
    }

    public void update(Account account, int userId) {
        checkPermission();

//        // Update Stroom user
//        Optional<Account> optionalUser = accountDao.get(userId);
//        Account foundAccount = optionalUser.get();
//        boolean isEnabled = account.isEnabled();
//        stroom.security.shared.User userToUpdate = securityUserService.getUserByName(foundAccount.getEmail());
//        userToUpdate.setEnabled(isEnabled);
//        securityUserService.update(userToUpdate);

        final String loggedInUser = securityContext.getUserId();
        account.setUpdateUser(loggedInUser);
        account.setUpdateTimeMs(System.currentTimeMillis());
        account.setId(userId);
        accountDao.update(account);

        stroomEventLoggingService.createAction("UpdateUser",
                "Toggle whether a token is enabled or not.");
    }

    public void deleteUser(int userId) {
        checkPermission();
        accountDao.delete(userId);
        stroomEventLoggingService.createAction("DeleteUser",
                "Delete a user by ID");
    }

    public int create(Account account) {
        checkPermission();

        // Validate
        final String userId = securityContext.getUserId();
        Pair<Boolean, String> validationResults = isValidForCreate(account);
        boolean isUserValid = validationResults.getLeft();
        if (!isUserValid) {
            throw new BadRequestException(validationResults.getRight());
        }
//        if (accountDao.exists(account.getEmail())) {
//            throw new ConflictException(AccountValidationError.USER_ALREADY_EXISTS.getMessage());
//        }

        account.setCreateTimeMs(System.currentTimeMillis());
        account.setCreateUser(userId);
        account.setLoginCount(0);
        // Set enabled by default.
        account.setEnabled(true);

        int newUserId = accountDao.create(account).getId();

        stroomEventLoggingService.createAction("CreateUser", "Create a user");
        return newUserId;
    }

    public static Pair<Boolean, String> isValidForCreate(Account account) {
        ArrayList<AccountValidationError> validationErrors = new ArrayList<>();

        if (account == null) {
            validationErrors.add(AccountValidationError.NO_USER);
        } else {
            if (Strings.isNullOrEmpty(account.getEmail())) {
                validationErrors.add(AccountValidationError.NO_NAME);
            }

//            if (Strings.isNullOrEmpty(account.getPassword())) {
//                validationErrors.add(AccountValidationError.NO_PASSWORD);
//            }
        }

        String validationMessages = validationErrors.stream()
                .map(AccountValidationError::getMessage)
                .reduce((validationMessage1, validationMessage2) -> validationMessage1 + validationMessage2).orElse("");
        boolean isValid = validationErrors.size() == 0;
        return Pair.of(isValid, validationMessages);
    }

    public ResultPage<Account> search(String email) {
        checkPermission();
        ResultPage<Account> foundUsers = accountDao.searchUsersForDisplay(email);
//        String users = foundUsers.formatJSON((new JSONFormat())
//                .header(false)
//                .recordFormat(JSONFormat.RecordFormat.OBJECT));
        stroomEventLoggingService.createAction("SearchUsers", "Search for users by email");
        return foundUsers;
    }

    private void checkPermission() {
        if (!securityContext.hasAppPermission(PermissionNames.MANAGE_USERS_PERMISSION)) {
            throw new PermissionException(securityContext.getUserId(), "You do not have permission to manage users");
        }
    }

    /**
     * This is not the same as getPasswordHash(). That's a getting for the model property,
     * but this method actually creates a new hash.
     */
    public String createPasswordHash(final String password) {
        return BCrypt.hashpw(password, BCrypt.gensalt());
    }
}
