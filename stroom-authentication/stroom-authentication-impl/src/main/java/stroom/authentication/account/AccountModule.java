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
 */

package stroom.authentication.account;

import stroom.security.api.ProcessingUserIdentityProvider;
import stroom.util.guice.RestResourcesBinder;

import com.google.inject.AbstractModule;

public final class AccountModule extends AbstractModule {
    @Override
    protected void configure() {
        install(new AccountTaskJobsModule());

        bind(AccountService.class).to(AccountServiceImpl.class);
        bind(AccountEventLog.class).to(AccountEventLogImpl.class);
        bind(ProcessingUserIdentityProvider.class).to(ProcessingUserIdentityProviderImpl.class);

        RestResourcesBinder.create(binder())
                .bind(AccountResourceImpl.class);
    }
}
