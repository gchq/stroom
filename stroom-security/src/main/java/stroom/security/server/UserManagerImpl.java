/*
 * Copyright 2016 Crown Copyright
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

package stroom.security.server;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.Period;
import stroom.jobsystem.server.JobTrackedSchedule;
import stroom.node.server.StroomPropertyService;
import stroom.security.Insecure;
import stroom.security.shared.FindUserCriteria;
import stroom.security.shared.UserStatus;
import stroom.util.logging.StroomLogger;
import stroom.util.spring.StroomSimpleCronSchedule;

import javax.inject.Inject;

@Component
public class UserManagerImpl implements UserManager {
    public static final String DAYS_TO_UNUSED_ACCOUNT_EXPIRY = "stroom.daysToUnusedAccountExpiry";
    public static final String DAYS_TO_ACCOUNT_EXPIRY = "stroom.daysToAccountExpiry";
    public static final long MS_IN_DAY = 1000 * 60 * 60 * 24;
    private static final StroomLogger LOGGER = StroomLogger.getLogger(UserManagerImpl.class);
    private final UserService userService;
    private final StroomPropertyService stroomPropertyService;

    @Inject
    UserManagerImpl(final UserService userService, final StroomPropertyService stroomPropertyService) {
        this.userService = userService;
        this.stroomPropertyService = stroomPropertyService;
    }

    @StroomSimpleCronSchedule(cron = "0 4 *")
    @JobTrackedSchedule(jobName = "Disable Unused Acccounts", advanced = false, description = "Job to disable unused accounts")
    @Insecure
    public void disableUnusedAccounts() {
        final Integer daysToAccountExpiry = getProperty(DAYS_TO_ACCOUNT_EXPIRY);
        if (daysToAccountExpiry != null) {
            final FindUserCriteria findUserCriteria = new FindUserCriteria();
            findUserCriteria.setLoginValidPeriod(
                    new Period(null, System.currentTimeMillis() - (daysToAccountExpiry * MS_IN_DAY)));
            disableUnusedAccounts(findUserCriteria);
        }
        final Integer daysToUnusedAccountExpiry = getProperty(DAYS_TO_UNUSED_ACCOUNT_EXPIRY);
        if (daysToUnusedAccountExpiry != null) {
            final FindUserCriteria findUserCriteria = new FindUserCriteria();
            findUserCriteria.setLoginValidPeriod(
                    new Period(null, System.currentTimeMillis() - (daysToUnusedAccountExpiry * MS_IN_DAY)));
            findUserCriteria.setLastLoginPeriod(Period.createNullPeriod());
            disableUnusedAccounts(findUserCriteria);
        }
    }

    private Integer getProperty(final String name) {
        Integer value = null;
        try {
            final String str = stroomPropertyService.getProperty(name);
            if (StringUtils.hasText(str)) {
                value = Integer.parseInt(str);
            }
        } catch (final RuntimeException ex) {
            LOGGER.error("getProperty(%s)", name, ex);
        }
        return value;
    }

    public void disableUnusedAccounts(final FindUserCriteria findUserCriteria) {
        findUserCriteria.setUserStatus(UserStatus.ENABLED);
        final BaseResultList<User> list = userService.find(findUserCriteria);
        list.stream().forEach(user -> {
            // Only do this if the account is supposed to never expire
            if (user.isLoginExpiry()) {
                user.updateStatus(UserStatus.DISABLED);
                LOGGER.info("disableUnusedAccounts() - Disabling %s", user.getName());
                try {
                    userService.save(user);
                } catch (final RuntimeException ex) {
                    LOGGER.error("disableUnusedAccounts() - Error Disabling %s", user.getName(), ex);
                }
            }
        });
    }
}
