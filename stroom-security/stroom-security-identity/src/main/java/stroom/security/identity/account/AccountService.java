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

import stroom.security.identity.shared.Account;
import stroom.security.identity.shared.AccountResultPage;
import stroom.security.identity.shared.CreateAccountRequest;
import stroom.security.identity.shared.FindAccountRequest;
import stroom.security.identity.shared.UpdateAccountRequest;
import stroom.util.shared.ResultPage;

import java.util.Optional;

public interface AccountService {

    AccountResultPage list();

    ResultPage<Account> search(FindAccountRequest request);

    Account create(CreateAccountRequest request);

    Optional<Account> read(int accountId);

    Optional<Account> read(String userId);

    void update(UpdateAccountRequest request, int accountId);

    void delete(int accountId);
}
