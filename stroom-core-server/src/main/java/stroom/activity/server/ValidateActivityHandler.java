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

package stroom.activity.server;

import org.springframework.context.annotation.Scope;
import stroom.activity.shared.ActivityValidationAction;
import stroom.activity.shared.ActivityValidationResult;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.TaskHandlerBean;
import stroom.util.spring.StroomScope;

import javax.annotation.Resource;

@TaskHandlerBean(task = ActivityValidationAction.class)
@Scope(StroomScope.PROTOTYPE)
public class ValidateActivityHandler extends AbstractTaskHandler<ActivityValidationAction, ActivityValidationResult> {
    @Resource
    private ActivityService activityService;

    @Override
    public ActivityValidationResult exec(final ActivityValidationAction action) {
        return activityService.validate(action.getActivity());
    }

}
