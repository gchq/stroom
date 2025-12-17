/*
 * Copyright 2016-2025 Crown Copyright
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
import stroom.security.identity.shared.AccountResultPage;
import stroom.security.identity.shared.CreateAccountRequest;
import stroom.security.identity.shared.FindAccountRequest;
import stroom.security.identity.shared.UpdateAccountRequest;
import stroom.security.shared.AppPermission;
import stroom.util.shared.PermissionException;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserDesc;

import com.google.common.base.Strings;
import jakarta.inject.Inject;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.Optional;

public class AccountServiceImpl implements AccountService {

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
    public Account create(final CreateAccountRequest request) {
        checkPermission();
        validateCreateRequest(request);
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
                    throw new RuntimeException("Unauthorized");
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
    public void update(final UpdateAccountRequest request, final int accountId) {
        checkPermission();
        validateUpdateRequest(request);

        final Account existingAccount = accountDao.get(accountId)
                .orElseThrow(() -> new RuntimeException("Account with id = " + accountId + " not found"));

//        // Update Stroom user
//        Optional<Account> optionalUser = accountDao.get(userId);
//        Account foundAccount = optionalUser.get();
//        boolean isEnabled = account.isEnabled();
//        stroom.security.shared.User userToUpdate = securityUserService.getUserByName(foundAccount.getEmail());
//        userToUpdate.setEnabled(isEnabled);
//        securityUserService.update(userToUpdate);

        final Account account = request.getAccount();
        account.setUpdateUser(securityContext.getUserIdentityForAudit());
        account.setUpdateTimeMs(System.currentTimeMillis());

        // If we are reactivating account then set the reactivated time.
        if ((account.isEnabled() && !existingAccount.isEnabled()) ||
            (!account.isInactive() && existingAccount.isInactive()) ||
            (!account.isLocked() && existingAccount.isLocked())) {
            account.setReactivatedMs(System.currentTimeMillis());
        }

        account.setId(accountId);
        accountDao.update(account);

        // Change the account password if the update request includes a new password.
        if (!Strings.isNullOrEmpty(request.getPassword())
            && request.getPassword().equals(request.getConfirmPassword())) {
            accountDao.changePassword(account.getUserId(), request.getPassword());
        }

        // If the fullName has changed we need to update the corresponding stroom user.
        // userId obviously can't change and displayName is same as userId
        if (!Objects.equals(existingAccount.getFirstName(), account.getFullName())) {
            userIdentityFactory.ensureUserIdentity(createUserIdentity(account));
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
        if (!securityContext.hasAppPermission(AppPermission.MANAGE_USERS_PERMISSION)) {
            throw new PermissionException(
                    securityContext.getUserRef(), "You do not have permission to manage users");
        }
    }
}
