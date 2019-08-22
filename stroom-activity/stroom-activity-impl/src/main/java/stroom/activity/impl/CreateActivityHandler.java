/*
 * Copyright 2018 Crown Copyright
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

package stroom.activity.impl;

import stroom.activity.api.ActivityService;
import stroom.activity.shared.Activity;
import stroom.activity.shared.CreateActivityAction;
import stroom.event.logging.api.DocumentEventLog;
import stroom.security.api.SecurityContext;
import stroom.task.api.AbstractTaskHandler;

import javax.inject.Inject;

public class CreateActivityHandler extends AbstractTaskHandler<CreateActivityAction, Activity> {
    private final ActivityService activityService;
    private final DocumentEventLog entityEventLog;
    private final SecurityContext securityContext;

    @Inject
    CreateActivityHandler(final ActivityService activityService,
                          final DocumentEventLog entityEventLog,
                          final SecurityContext securityContext) {
        this.activityService = activityService;
        this.entityEventLog = entityEventLog;
        this.securityContext = securityContext;
    }

    @Override
    public Activity exec(final CreateActivityAction action) {
        return securityContext.secureResult(() -> {
            Activity result;

            try {
                result = activityService.create();
                entityEventLog.create(result, null);
            } catch (final RuntimeException e) {
                entityEventLog.create(new Activity(), e);
                throw e;
            }

            return result;
        });
    }
}
