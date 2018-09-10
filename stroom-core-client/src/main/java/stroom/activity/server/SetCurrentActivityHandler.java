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

import event.logging.Event;
import event.logging.Event.EventDetail.Update;
import event.logging.MultiObject;
import event.logging.Object;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import stroom.activity.shared.Activity;
import stroom.activity.shared.SetCurrentActivityAction;
import stroom.logging.CurrentActivity;
import stroom.logging.PurposeUtil;
import stroom.logging.StroomEventLoggingService;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.TaskHandlerBean;
import stroom.util.spring.StroomScope;

import javax.annotation.Resource;

@TaskHandlerBean(task = SetCurrentActivityAction.class)
@Scope(StroomScope.PROTOTYPE)
public class SetCurrentActivityHandler extends AbstractTaskHandler<SetCurrentActivityAction, Activity> {
    private static final Logger LOGGER = LoggerFactory.getLogger(SetCurrentActivityHandler.class);

    @Resource
    private CurrentActivity currentActivity;
    @Resource
    private StroomEventLoggingService eventLoggingService;

    @Override
    public Activity exec(final SetCurrentActivityAction action) {
        try {
            final Activity beforeActivity = currentActivity.getActivity();
            final Activity afterActivity = action.getActivity();

            currentActivity.setActivity(afterActivity);

            final Event event = eventLoggingService.createAction("Set Activity", "User has changed activity");

            final Update update = new Update();
            update.setBefore(convertActivity(beforeActivity));
            update.setAfter(convertActivity(afterActivity));

            event.getEventDetail().setUpdate(update);
            eventLoggingService.log(event);

        } catch (final Exception e) {
            LOGGER.error(e.getMessage(), e);
        }

        return action.getActivity();
    }

    private MultiObject convertActivity(final Activity activity) {
        if (activity == null ||
                activity.getDetails() == null ||
                activity.getDetails().getProperties() == null ||
                activity.getDetails().getProperties().size() == 0) {
            return null;
        }

        final Object object = new Object();
        object.setType("Activity");
        PurposeUtil.addData(object.getData(), activity);

        final MultiObject multiObject = new MultiObject();
        multiObject.getObjects().add(object);

        return multiObject;
    }
}
