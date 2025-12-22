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

import stroom.security.identity.config.PasswordPolicyConfig;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.util.logging.LogUtil;
import stroom.util.time.StroomDuration;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
class AccountMaintenanceTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(AccountMaintenanceTask.class);

    private final Provider<PasswordPolicyConfig> passwordPolicyConfigProvider;
    private final AccountDao accountDao;
    private final TaskContextFactory taskContextFactory;

    @Inject
    AccountMaintenanceTask(final Provider<PasswordPolicyConfig> passwordPolicyConfigProvider,
                           final AccountDao accountDao,
                           final TaskContextFactory taskContextFactory) {
        this.passwordPolicyConfigProvider = passwordPolicyConfigProvider;
        this.accountDao = accountDao;
        this.taskContextFactory = taskContextFactory;
    }

    public void exec() {
        final TaskContext taskContext = taskContextFactory.current();

        LOGGER.info("Checking for accounts that are not being used.");
        taskContext.info(() -> "Checking for accounts that are not being used.");

        final StroomDuration neverUsedAgeThreshold = passwordPolicyConfigProvider.get()
                .getNeverUsedAccountDeactivationThreshold();
        final int numberOfInactiveNewAccounts = accountDao.deactivateNewInactiveUsers(
                neverUsedAgeThreshold.getDuration());
        LOGGER.info("Deactivated {} new user account(s) that have been inactive for {} or more.",
                numberOfInactiveNewAccounts, neverUsedAgeThreshold);
        taskContext.info(() -> LogUtil.message(
                "Deactivated {} new user account(s) that have been inactive for {} or more.",
                numberOfInactiveNewAccounts,
                neverUsedAgeThreshold));

        final StroomDuration unusedAgeThreshold = passwordPolicyConfigProvider.get()
                .getUnusedAccountDeactivationThreshold();
        final int numberOfInactiveAccounts = accountDao.deactivateInactiveUsers(unusedAgeThreshold.getDuration());
        LOGGER.info("Deactivated {} user account(s) that have been inactive for {} or more.",
                numberOfInactiveAccounts, unusedAgeThreshold);
        taskContext.info(() -> LogUtil.message(
                "Deactivated {} user account(s) that have been inactive for {} or more.",
                numberOfInactiveAccounts,
                unusedAgeThreshold));

        // TODO password change checks
    }
}
