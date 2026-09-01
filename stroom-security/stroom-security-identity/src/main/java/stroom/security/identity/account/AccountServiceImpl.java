/*
 * Copyright 2020 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.security.identity.account;

import stroom.security.api.SecurityContext;
import stroom.security.api.UserIdentityFactory;
import stroom.security.identity.authenticate.PasswordValidator;
import stroom.security.identity.config.IdentityConfig;
import stroom.security.identity.shared.Account;
import stroom.security.identity.shared.AccountAction;
import stroom.security.identity.shared.AccountChange;
import stroom.security.identity.shared.AccountResultPage;
import stroom.security.identity.shared.CreateAccountRequest;
import stroom.security.identity.shared.FindAccountRequest;
import stroom.security.shared.AppPermission;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.PermissionException;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserDesc;

import com.google.common.base.Strings;
import jakarta.inject.Inject;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.Optional;

public class AccountServiceImpl implements AccountService {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AccountServiceImpl.class);

    private final AccountDao accountDao;
    private final SecurityContext securityContext;
    private final IdentityConfig config;
    private final UserIdentityFactory userIdentityFactory;


    @Inject
    AccountServiceImpl(final AccountDao accountDao,
                       final SecurityContext securityContext,
                       final IdentityConfig config,
                       final UserIdentityFactory userIdentityFactory) {
        this.accountDao = accountDao;
        this.securityContext = securityContext;
        this.config = config;
        this.userIdentityFactory = userIdentityFactory;
    }

//    @Override
//    public boolean isEnabled() {
//        return shouldProvideNames();
//    }
//
//    @Override
//    public int getPriority() {
//        return 2;
//    }
//
//    @Override
//    public List<UserName> findAssociates(final FindUserNameCriteria criteria) {
//        if (securityContext.hasAppPermission(AppPermissionEnum.MANAGE_USERS_PERMISSION)) {
//            return findUserNames(criteria);
//        } else {
//            // No perms so can't see any accounts as accounts do not belong to groups
//            return Collections.emptyList();
//        }
//    }
//
//    private List<UserName> findUserNames(final FindUserNameCriteria criteria) {
//
//        // Only the internal IDP uses Accounts, so no point hitting it for other IDPs
//        if (shouldProvideNames()) {
//            final FindAccountRequest request = new FindAccountRequest(
//                    criteria.getPageRequest(),
//                    criteria.getSortList(),
//                    criteria.getQuickFilterInput());
//
//            final AccountResultPage result = search(request);
//
//            final List<UserName> list = result.getValues()
//                    .stream()
//                    .map(this::mapAccountToUserName)
//                    .toList();
//
//            return new ResultPage<>(list, result.getPageResponse());
//        } else {
//            return new ResultPage<>(Collections.emptyList());
//        }
//    }
//
//    @Override
//    public Optional<UserName> getBySubjectId(final String subjectId) {
////        if (shouldProvideNames()) {
////            return accountDao.get(subjectId)
////                    .map(this::mapAccountToUserName);
////        } else {
////            return Optional.empty();
////        }
//        // This UserNameProvider is only used for getting lists of names to be
//        // unioned with other providers
//        return Optional.empty();
//    }
//
//    @Override
//    public Optional<UserName> getByDisplayName(final String displayName) {
//        // Accounts have no concept of displayName so just get by userId
////        return getBySubjectId(displayName);
//
//        // This UserNameProvider is only used for getting lists of names to be
//        // unioned with other providers
//        return Optional.empty();
//    }
//
//    @Override
//    public Optional<UserName> getByUuid(final String userUuid) {
//        // This UserNameProvider is only used for getting lists of names to be
//        // unioned with other providers + accounts don't have stroom UUIDs
//        return Optional.empty();
//    }
//
//    private UserName mapAccountToUserName(final Account account) {
//        return new SimpleUserName(
//                account.getUserId(),
//                account.getUserId(), // use user id for both name and displayName
//                account.getFullName());
//    }
//
//    private boolean shouldProvideNames() {
//        return IdpType.INTERNAL_IDP.equals(openIdConfigurationProvider.get().getIdentityProviderType());
//    }

    @Override
    public AccountResultPage list() {
        checkPermission();
        return accountDao.list();
    }

    @Override
    public ResultPage search(final FindAccountRequest request) {
        checkPermission();
        return accountDao.search(request);
    }

    @Override
    public Account create(final CreateAccountRequest request, final boolean enforcePasswordPolicy) {
        checkPermission();
        validateCreateRequest(request, enforcePasswordPolicy);
        final Account account = buildAccountObject(request);
        final Account persistedAccount = accountDao.create(account, request.getPassword());

        // Create a corresponding stroom user for the account
        userIdentityFactory.ensureUserIdentity(createUserIdentity(account));

        return persistedAccount;
    }

    @NotNull
    private Account buildAccountObject(final CreateAccountRequest request) {
        final long now = System.currentTimeMillis();
        final String userIdForAudit = securityContext.getUserIdentityForAudit();
        final Account account = new Account();
        account.setCreateTimeMs(now);
        account.setCreateUser(userIdForAudit);
        account.setUpdateTimeMs(now);
        account.setUpdateUser(userIdForAudit);
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
        // Deliberately left as they default, and deliberately not on CreateAccountRequest either: neither
        // is a state an account can be created in. A lock is only ever applied by repeated wrong passwords,
        // and inactivity only by the account maintenance job when an account goes unused.
        return account;
    }

    private UserDesc createUserIdentity(final Account account) {
        if (account == null) {
            return null;
        } else {
            return UserDesc.builder(account.getUserId())
                    .fullName(account.getFullName())
                    .build();
        }
    }

    @Override
    public Optional<Account> read(final int accountId) {
        final Optional<Account> optionalUser = accountDao.get(accountId);
        if (optionalUser.isPresent()) {
            // We only need to check auth permissions if the user is trying to access a different user.
            if (!securityContext.hasAppPermission(AppPermission.MANAGE_USERS_PERMISSION)) {
                final Account foundAccount = optionalUser.get();
                final String loggedInUserSubjectId = securityContext.getUserRef().getSubjectId();
                final boolean isUserAccessingThemselves = loggedInUserSubjectId.equals(foundAccount.getUserId());
                if (!isUserAccessingThemselves) {
                    // Answered as though the account is not there, because refusing it any other way tells
                    // the caller that it is. Account ids are sequential integers and this is reachable by
                    // any signed-in user, so a refusal distinguishable from "no such account" lets them map
                    // which ids are live by walking them. The caller learns nothing they did not bring.
                    //
                    // The attempt is still recorded: the resource logs the view event either way, and the
                    // reason for the refusal is here in the debug log.
                    LOGGER.debug(() -> LogUtil.message(
                            "User {} tried to read account id {}, which belongs to {}",
                            loggedInUserSubjectId, accountId, foundAccount.getUserId()));
                    return Optional.empty();
                }
            }
        }
        return optionalUser;
    }

    @Override
    public Optional<Account> read(final String userId) {
        checkPermission();
        return accountDao.get(userId);
    }

    @Override
    public void update(final AccountChange change, final int accountId) {
        checkPermission();
        validateChange(change, accountId);

        final Account existingAccount = accountDao.get(accountId)
                .orElseThrow(() -> new RuntimeException("Account with id = " + accountId + " not found"));

        accountDao.applyChange(
                accountId,
                change,
                securityContext.getUserIdentityForAudit(),
                System.currentTimeMillis());

        // Read the account back rather than assuming what the change left behind, so the work below acts on
        // what was actually stored.
        final Account updatedAccount = accountDao.get(accountId)
                .orElseThrow(() -> new RuntimeException("Account with id = " + accountId + " not found"));

        // Change the account password if the change includes a new one. The force-change flag is passed
        // through rather than left to default, because setting a password and requiring the user to change
        // it at next sign in is one save in the UI - and an administrator who does both now knows the
        // credential, so silently clearing the flag would leave the user on it indefinitely.
        if (!Strings.isNullOrEmpty(change.getPassword())
            && change.getPassword().equals(change.getConfirmPassword())) {
            accountDao.changePassword(
                    updatedAccount.getUserId(),
                    change.getPassword(),
                    updatedAccount.isForcePasswordChange());
        }

        // If the full name has changed we need to update the corresponding stroom user. userId cannot change
        // for an existing user and displayName is the same as userId, so the name is all there is to carry.
        if (!Objects.equals(existingAccount.getFullName(), updatedAccount.getFullName())) {
            userIdentityFactory.ensureUserIdentity(createUserIdentity(updatedAccount));
        }
    }

    @Override
    public void delete(final int accountId) {
        checkPermission();
        accountDao.delete(accountId);
    }

    private void validateCreateRequest(final CreateAccountRequest request, final boolean enforcePasswordPolicy) {
        if (request == null) {
            throw new RuntimeException("Null request");
        } else {
            if (Strings.isNullOrEmpty(request.getUserId())) {
                throw new RuntimeException("No user id has been provided");
            }

            if (request.getPassword() != null || request.getConfirmPassword() != null) {
                if (enforcePasswordPolicy) {
                    PasswordValidator.validateLength(request.getPassword(),
                            config.getPasswordPolicyConfig().getMinimumPasswordLength());
                    PasswordValidator.validateStrength(request.getPassword(),
                            config.getPasswordPolicyConfig().getMinimumPasswordStrength());
                }
                PasswordValidator.validateConfirmation(request.getPassword(), request.getConfirmPassword());
            }

            validateEmailIsNotInUse(request.getEmail(), null);
        }
    }

    /**
     * Email addresses identify an account for 'forgot password', so they must be unique. The database
     * enforces this, but a bare duplicate key error is not something to show an administrator. An account
     * may have no email address at all, in which case there is nothing to clash with.
     */
    private void validateEmailIsNotInUse(final String email, final Integer accountIdBeingUpdated) {
        if (Strings.isNullOrEmpty(email)) {
            return;
        }

        accountDao.getByEmail(email)
                .filter(existing -> !Objects.equals(existing.getId(), accountIdBeingUpdated))
                .ifPresent(existing -> {
                    throw new RuntimeException(
                            "The email address '" + email + "' is already used by another account");
                });
    }

    private void validateChange(final AccountChange change, final int accountId) {
        if (change == null) {
            throw new RuntimeException("Null change");
        }

        // A change that asks for a state and its opposite has no meaningful outcome, so refuse it rather
        // than letting the order the actions happen to be applied in decide what the account ends up as.
        for (final AccountAction action : change.getActions()) {
            action.getOpposite()
                    .filter(change::hasAction)
                    .ifPresent(opposite -> {
                        throw new RuntimeException(
                                "A change cannot ask to " + action + " and " + opposite + " an account");
                    });
        }

        if (change.getPassword() != null || change.getConfirmPassword() != null) {
            PasswordValidator.validateLength(change.getPassword(),
                    config.getPasswordPolicyConfig().getMinimumPasswordLength());
            PasswordValidator.validateStrength(change.getPassword(),
                    config.getPasswordPolicyConfig().getMinimumPasswordStrength());
            PasswordValidator.validateConfirmation(change.getPassword(), change.getConfirmPassword());
        }

        // Exclude the account being updated, which is allowed to keep its own address.
        validateEmailIsNotInUse(change.getEmail(), accountId);
    }

    private void checkPermission() {
        if (!securityContext.hasAppPermission(AppPermission.MANAGE_USERS_PERMISSION)) {
            throw new PermissionException(
                    securityContext.getUserRef(), "You do not have permission to manage users");
        }
    }
}
