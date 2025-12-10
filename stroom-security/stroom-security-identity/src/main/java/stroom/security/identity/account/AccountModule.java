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

import stroom.job.api.ScheduledJobsBinder;
import stroom.security.api.ServiceUserFactory;
import stroom.security.openid.api.IdpType;
import stroom.util.RunnableWrapper;
import stroom.util.guice.GuiceUtil;
import stroom.util.guice.RestResourcesBinder;

import com.google.inject.AbstractModule;
import jakarta.inject.Inject;

public final class AccountModule extends AbstractModule {

    @Override
    protected void configure() {

        bind(AccountService.class).to(AccountServiceImpl.class);

        GuiceUtil.buildMapBinder(binder(), IdpType.class, ServiceUserFactory.class)
                .addBinding(IdpType.INTERNAL_IDP, InternalServiceUserFactory.class);

        RestResourcesBinder.create(binder())
                .bind(AccountResourceImpl.class);

        ScheduledJobsBinder.create(binder())
                .bindJobTo(AccountMaintenance.class, jobBuilder -> jobBuilder
                        .name("Account Maintenance")
                        .description("Maintain user accounts such as disabling unused ones.")
                        .frequencySchedule("1d"));
    }

    private static class AccountMaintenance extends RunnableWrapper {

        @Inject
        AccountMaintenance(final AccountMaintenanceTask accountMaintenanceTask) {
            super(accountMaintenanceTask::exec);
        }
    }
}
