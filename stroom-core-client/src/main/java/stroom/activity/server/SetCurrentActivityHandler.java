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
import stroom.activity.shared.Activity;
import stroom.activity.shared.SetCurrentActivityAction;
import stroom.logging.CurrentActivity;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.TaskHandlerBean;
import stroom.util.spring.StroomScope;

import javax.annotation.Resource;

@TaskHandlerBean(task = SetCurrentActivityAction.class)
@Scope(StroomScope.PROTOTYPE)
public class SetCurrentActivityHandler extends AbstractTaskHandler<SetCurrentActivityAction, Activity> {
    @Resource
    private CurrentActivity currentActivity;

    @Override
    public Activity exec(final SetCurrentActivityAction action) {
        currentActivity.setActivity(action.getActivity());
        return action.getActivity();
    }
}
