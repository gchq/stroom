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

import event.logging.Banner;
import event.logging.Event;
import event.logging.ObjectOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import stroom.activity.shared.AcknowledgeSplashAction;
import stroom.logging.StroomEventLoggingService;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.TaskHandlerBean;
import stroom.util.shared.SharedBoolean;
import stroom.util.spring.StroomScope;

import javax.annotation.Resource;

@TaskHandlerBean(task = AcknowledgeSplashAction.class)
@Scope(StroomScope.PROTOTYPE)
public class AcknowledgeSplashHandler extends AbstractTaskHandler<AcknowledgeSplashAction, SharedBoolean> {
    private static final Logger LOGGER = LoggerFactory.getLogger(AcknowledgeSplashHandler.class);

    @Resource
    private StroomEventLoggingService eventLoggingService;

    @Override
    public SharedBoolean exec(final AcknowledgeSplashAction action) {
        try {
            final Event event = eventLoggingService.createAction("Acknowledge Splash", "User has acknowledged the splash screen");

            final Banner banner = new Banner();
            banner.setMessage(action.getMessage());
            banner.setVersion(action.getVersion());

            final ObjectOutcome view = new ObjectOutcome();
            view.getObjects().add(banner);
            event.getEventDetail().setView(view);

            eventLoggingService.log(event);

            return SharedBoolean.wrap(true);

        } catch (final Exception e) {
            LOGGER.error(e.getMessage(), e);
        }

        return SharedBoolean.wrap(false);
    }
}
