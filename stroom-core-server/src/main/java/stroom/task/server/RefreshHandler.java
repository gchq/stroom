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

package stroom.task.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.util.StringUtils;
import stroom.node.server.StroomPropertyService;
import stroom.security.Insecure;
import stroom.task.shared.RefreshAction;
import stroom.util.shared.SharedString;
import stroom.util.spring.StroomScope;

import javax.inject.Inject;

@TaskHandlerBean(task = RefreshAction.class)
@Scope(value = StroomScope.TASK)
@Insecure
class RefreshHandler extends AbstractTaskHandler<RefreshAction, SharedString> {
    public static final String STROOM_MAINTENANCE_MESSAGE = "stroom.maintenanceMessage";

    private static final Logger LOGGER = LoggerFactory.getLogger(RefreshHandler.class);

    private final StroomPropertyService stroomPropertyService;

    @Inject
    RefreshHandler(final StroomPropertyService stroomPropertyService) {
        this.stroomPropertyService = stroomPropertyService;
    }

    @Override
    public SharedString exec(final RefreshAction action) {
        LOGGER.debug("exec() - %s %s", action.getUserId(), action.getSessionId());

        final String msg = getMaintenanceMessage();
        if (StringUtils.hasText(msg)) {
            return SharedString.wrap(msg);
        }
        return null;
    }

    private String getMaintenanceMessage() {
        return stroomPropertyService.getProperty(STROOM_MAINTENANCE_MESSAGE);
    }
}
