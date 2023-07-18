package stroom.security.identity.account;

import stroom.security.api.SecurityContext;
import stroom.security.identity.authenticate.PasswordValidator;
import stroom.security.identity.config.IdentityConfig;
import stroom.security.openid.api.IdpType;
import stroom.security.openid.api.OpenIdConfiguration;
import stroom.security.shared.FindUserNameCriteria;
import stroom.security.shared.PermissionNames;
import stroom.security.shared.UserNameProvider;
import stroom.util.shared.PermissionException;
import stroom.util.shared.ResultPage;
import stroom.util.shared.SimpleUserName;
import stroom.util.shared.UserName;

import com.google.common.base.Strings;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Provider;

public class AccountServiceImpl implements AccountService, UserNameProvider {

    private final AccountDao accountDao;
    private final SecurityContext securityContext;
    private final IdentityConfig config;
    private final Provider<OpenIdConfiguration> openIdConfigurationProvider;


    @Inject
    AccountServiceImpl(final AccountDao accountDao,
                       final SecurityContext securityContext,
                       final IdentityConfig config,
                       final Provider<OpenIdConfiguration> openIdConfigurationProvider) {
        this.accountDao = accountDao;
        this.securityContext = securityContext;
        this.config = config;
        this.openIdConfigurationProvider = openIdConfigurationProvider;
    }

    @Override
    public boolean isEnabled() {
        return shouldProvideNames();
    }

    @Override
    public ResultPage<UserName> findUserNames(final FindUserNameCriteria criteria) {

        // Only the internal IDP uses Accounts, so no point hitting it for other IDPs
        if (shouldProvideNames()) {
            final SearchAccountRequest request = new SearchAccountRequest(
                    criteria.getPageRequest(),
                    criteria.getSortList(),
                    criteria.getQuickFilterInput());

            final AccountResultPage result = search(request);

            final List<UserName> list = result.getValues()
                    .stream()
                    .map(this::mapAccountToUserName)
                    .collect(Collectors.toList());

            return new ResultPage<>(list, result.getPageResponse());
        } else {
            return new ResultPage<>(Collections.emptyList());
        }
    }

    @Override
    public Optional<UserName> getBySubjectId(final String subjectId) {
        if (shouldProvideNames()) {
            return accountDao.get(subjectId)
                    .map(this::mapAccountToUserName);
        } else {
            return Optional.empty();
        }
    }

    @Override
    public Optional<UserName> getByDisplayName(final String displayName) {
        // Accounts have no concept of displayName so just get by userId
        return getBySubjectId(displayName);
    }

    private UserName mapAccountToUserName(final Account account) {
        return new SimpleUserName(
                account.getUserId(),
                account.getUserId(), // use user id for both name and displayName
                account.getFullName());
    }

    private boolean shouldProvideNames() {
        return IdpType.INTERNAL_IDP.equals(openIdConfigurationProvider.get().getIdentityProviderType());
    }

    @Override
    public AccountResultPage list() {
        checkPermission();
        return accountDao.list();
    }

    @Override
    public AccountResultPage search(final SearchAccountRequest request) {
        checkPermission();
        return accountDao.search(request);
    }

    @Override
    public Account create(final CreateAccountRequest request) {
        checkPermission();
        validateCreateRequest(request);

        // Validate
        final String userId = securityContext.getSubjectId();

        final long now = System.currentTimeMillis();

        final Account account = new Account();
        account.setCreateTimeMs(now);
        account.setCreateUser(userId);
        account.setUpdateTimeMs(now);
        account.setUpdateUser(userId);
        account.setFirstName(request.getFirstName());
        account.setLastName(request.getLastName());
        account.setUserId(request.getUserId());
        account.setEmail(request.getEmail());
        account.setComments(request.getComments());
        account.setForcePasswordChange(request.isForcePasswordChange());
        account.setNeverExpires(request.isNeverExpires());
        account.setLoginCount(0);
        // Set enabled by default.
        account.setEnabled(true);

        return accountDao.create(account, request.getPassword());
    }

    @Override
    public Optional<Account> read(final int accountId) {
        final String loggedInUser = securityContext.getSubjectId();

        Optional<Account> optionalUser = accountDao.get(accountId);
        if (optionalUser.isPresent()) {
            Account foundAccount = optionalUser.get();
            // We only need to check auth permissions if the user is trying to access a different user.
            final boolean isUserAccessingThemselves = loggedInUser.equals(foundAccount.getUserId());
            boolean canManageUsers = securityContext.hasAppPermission(PermissionNames.MANAGE_USERS_PERMISSION);
            if (!isUserAccessingThemselves && !canManageUsers) {
                throw new RuntimeException("Unauthorized");
            }
        }
        return optionalUser;
    }

    @Override
    public Optional<Account> read(final String email) {
        checkPermission();
        return accountDao.get(email);
    }

    @Override
    public void update(final UpdateAccountRequest request, final int accountId) {
        checkPermission();
        validateUpdateRequest(request);

        final Optional<Account> existingAccount = accountDao.get(accountId);
        if (existingAccount.isEmpty()) {
            throw new RuntimeException("Account with id = " + accountId + " not found");
        }

//        // Update Stroom user
//        Optional<Account> optionalUser = accountDao.get(userId);
//        Account foundAccount = optionalUser.get();
//        boolean isEnabled = account.isEnabled();
//        stroom.security.shared.User userToUpdate = securityUserService.getUserByName(foundAccount.getEmail());
//        userToUpdate.setEnabled(isEnabled);
//        securityUserService.update(userToUpdate);

        final String loggedInUser = securityContext.getSubjectId();
        final Account account = request.getAccount();
        account.setUpdateUser(loggedInUser);
        account.setUpdateTimeMs(System.currentTimeMillis());

        // If we are reactivating account then set the reactivated time.
        if ((account.isEnabled() && !existingAccount.get().isEnabled()) ||
                (!account.isInactive() && existingAccount.get().isInactive()) ||
                (!account.isLocked() && existingAccount.get().isLocked())) {
            account.setReactivatedMs(System.currentTimeMillis());
        }

        account.setId(accountId);
        accountDao.update(account);

        // Change the account password if the update request includes a new password.
        if (!Strings.isNullOrEmpty(request.getPassword())
                && request.getPassword().equals(request.getConfirmPassword())) {
            accountDao.changePassword(account.getUserId(), request.getPassword());
        }
    }

    @Override
    public void delete(final int accountId) {
        checkPermission();
        accountDao.delete(accountId);
    }

    private void validateCreateRequest(final CreateAccountRequest request) {
        if (request == null) {
            throw new RuntimeException("Null request");
        } else {
            if (Strings.isNullOrEmpty(request.getUserId())) {
                throw new RuntimeException("No user id has been provided");
            }

            if (request.getPassword() != null || request.getConfirmPassword() != null) {
                PasswordValidator.validateLength(request.getPassword(),
                        config.getPasswordPolicyConfig().getMinimumPasswordLength());
                PasswordValidator.validateComplexity(request.getPassword(),
                        config.getPasswordPolicyConfig().getPasswordComplexityRegex());
                PasswordValidator.validateConfirmation(request.getPassword(), request.getConfirmPassword());
            }
        }
    }

    private void validateUpdateRequest(final UpdateAccountRequest request) {
        if (request == null) {
            throw new RuntimeException("Null request");
        } else {
            if (request.getAccount() == null || request.getAccount().getId() == null) {
                throw new RuntimeException("No user id has been provided");
            }

            if (request.getPassword() != null || request.getConfirmPassword() != null) {
                PasswordValidator.validateLength(request.getPassword(),
                        config.getPasswordPolicyConfig().getMinimumPasswordLength());
                PasswordValidator.validateComplexity(request.getPassword(),
                        config.getPasswordPolicyConfig().getPasswordComplexityRegex());
                PasswordValidator.validateConfirmation(request.getPassword(), request.getConfirmPassword());
            }
        }
    }

    private void checkPermission() {
        if (!securityContext.hasAppPermission(PermissionNames.MANAGE_USERS_PERMISSION)) {
            throw new PermissionException(
                    securityContext.getUserIdentityForAudit(), "You do not have permission to manage users");
        }
    }
}
