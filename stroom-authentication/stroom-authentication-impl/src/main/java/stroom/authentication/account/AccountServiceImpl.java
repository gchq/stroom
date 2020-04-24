package stroom.authentication.account;

import com.google.common.base.Strings;
import org.apache.commons.lang3.tuple.Pair;
import stroom.security.api.SecurityContext;
import stroom.security.shared.PermissionException;
import stroom.security.shared.PermissionNames;
import stroom.util.shared.ResultPage;

import javax.inject.Inject;
import javax.ws.rs.BadRequestException;
import java.util.ArrayList;
import java.util.Optional;

public class AccountServiceImpl implements AccountService {
    private final AccountDao accountDao;
    private final SecurityContext securityContext;

    @Inject
    AccountServiceImpl(final AccountDao accountDao,
                       final SecurityContext securityContext) {
        this.accountDao = accountDao;
        this.securityContext = securityContext;
    }

    public ResultPage<Account> list() {
        checkPermission();

        return accountDao.list();
    }

    public ResultPage<Account> search(final String email) {
        checkPermission();

        return accountDao.searchUsersForDisplay(email);
    }

    public Account create(final CreateAccountRequest request) {
        checkPermission();

        // Validate
        final String userId = securityContext.getUserId();
        Pair<Boolean, String> validationResults = isValidForCreate(request);
        boolean isUserValid = validationResults.getLeft();
        if (!isUserValid) {
            throw new BadRequestException(validationResults.getRight());
        }
//        if (accountDao.exists(account.getEmail())) {
//            throw new ConflictException(AccountValidationError.USER_ALREADY_EXISTS.getMessage());
//        }

        final long now = System.currentTimeMillis();

        final Account account = new Account();
        account.setCreateTimeMs(now);
        account.setCreateUser(userId);
        account.setUpdateTimeMs(now);
        account.setUpdateUser(userId);
        account.setFirstName(request.getFirstName());
        account.setLastName(request.getLastName());
        account.setEmail(request.getEmail());
        account.setComments(request.getComments());
        account.setForcePasswordChange(request.isForcePasswordChange());
        account.setNeverExpires(request.isNeverExpires());
        account.setLoginCount(0);
        // Set enabled by default.
        account.setEnabled(true);

        return accountDao.create(account, request.getPassword());
    }

    public Optional<Account> read(final int accountId) {
        final String loggedInUser = securityContext.getUserId();

        Optional<Account> optionalUser = accountDao.get(accountId);
        if (optionalUser.isPresent()) {
            Account foundAccount = optionalUser.get();
            // We only need to check auth permissions if the user is trying to access a different user.
            final boolean isUserAccessingThemselves = loggedInUser.equals(foundAccount.getEmail());
            boolean canManageUsers = securityContext.hasAppPermission(PermissionNames.MANAGE_USERS_PERMISSION);
            if (!isUserAccessingThemselves && !canManageUsers) {
                throw new RuntimeException("Unauthorized");
            }
        }
        return optionalUser;
    }

    public Optional<Account> read(final String email) {
        checkPermission();
        return accountDao.get(email);
    }

    public void update(final Account account, final int accountId) {
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
        account.setId(accountId);
        accountDao.update(account);

    }

    public void delete(final int accountId) {
        checkPermission();
        accountDao.delete(accountId);
    }

    public static Pair<Boolean, String> isValidForCreate(final CreateAccountRequest account) {
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

    private void checkPermission() {
        if (!securityContext.hasAppPermission(PermissionNames.MANAGE_USERS_PERMISSION)) {
            throw new PermissionException(securityContext.getUserId(), "You do not have permission to manage users");
        }
    }
}
