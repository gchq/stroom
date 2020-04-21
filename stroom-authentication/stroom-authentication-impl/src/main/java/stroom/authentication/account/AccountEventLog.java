/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.authentication.account;

import stroom.util.shared.ResultPage;

public interface AccountEventLog {
    void list(ResultPage<Account> result, Throwable ex);

    void search(String email, ResultPage<Account> result, Throwable ex);

    void create(CreateAccountRequest request, Account result, Throwable ex);

    void read(int accountId, Account result, Throwable ex);

    void read(String email, Account result, Throwable ex);

    void update(Account account, int accountId, Throwable ex);

    void delete(int accountId, Throwable ex);
}
