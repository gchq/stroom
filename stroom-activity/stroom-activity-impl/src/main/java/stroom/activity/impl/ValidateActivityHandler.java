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
import stroom.activity.shared.ActivityValidationResult;
import stroom.activity.shared.ValidateActivityAction;
import stroom.task.api.AbstractTaskHandler;

import javax.inject.Inject;

class ValidateActivityHandler extends AbstractTaskHandler<ValidateActivityAction, ActivityValidationResult> {
    private final ActivityService activityService;

    @Inject
    ValidateActivityHandler(final ActivityService activityService) {
        this.activityService = activityService;
    }

    @Override
    public ActivityValidationResult exec(final ValidateActivityAction action) {
        return activityService.validate(action.getActivity());
    }
}
