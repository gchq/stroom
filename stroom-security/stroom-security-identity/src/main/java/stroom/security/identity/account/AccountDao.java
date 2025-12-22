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

import stroom.security.identity.authenticate.CredentialValidationResult;
import stroom.security.identity.shared.Account;
import stroom.security.identity.shared.AccountResultPage;
import stroom.security.identity.shared.FindAccountRequest;
import stroom.util.shared.ResultPage;

import java.time.Duration;
import java.util.Optional;

public interface AccountDao {

    AccountResultPage list();

    ResultPage<Account> search(FindAccountRequest request);

    Account create(Account account, String password);

    Account tryCreate(final Account account, final String password);

    Optional<Integer> getId(String userId);

    Optional<Account> get(String userId);

    Optional<Account> get(int id);

    void update(Account account);

    void delete(int id);

    void recordSuccessfulLogin(String userId);

    CredentialValidationResult validateCredentials(String username, String password);

    boolean incrementLoginFailures(String userId);

    void changePassword(String userId, String newPassword);

    void resetPassword(String userId, String newPassword);

    Boolean needsPasswordChange(String userId,
                                Duration mandatoryPasswordChangeDuration,
                                boolean forcePasswordChangeOnFirstLogin);

    int deactivateNewInactiveUsers(Duration neverUsedAccountDeactivationThreshold);

    int deactivateInactiveUsers(Duration unusedAccountDeactivationThreshold);
}
