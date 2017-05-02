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

package stroom.dispatch.server;

import org.springframework.stereotype.Component;
import stroom.dispatch.client.DispatchService;
import stroom.dispatch.shared.Action;
import stroom.entity.server.util.EntityServiceExceptionUtil;
import stroom.entity.shared.EntityServiceException;
import stroom.security.SecurityContext;
import stroom.servlet.HttpServletRequestHolder;
import stroom.task.server.TaskHandlerBean;
import stroom.task.server.TaskHandlerBeanRegistry;
import stroom.task.server.TaskManager;
import stroom.util.logging.StroomLogger;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.SharedObject;
import stroom.util.shared.UserTokenUtil;
import stroom.util.task.TaskIdFactory;

import javax.inject.Inject;

@Component(DispatchServiceImpl.BEAN_NAME)
public class DispatchServiceImpl implements DispatchService {
    public static final String BEAN_NAME = "dispatchService";

    private static final StroomLogger LOGGER = StroomLogger.getLogger(DispatchServiceImpl.class);

    private final TaskHandlerBeanRegistry taskHandlerBeanRegistry;
    private final TaskManager taskManager;
    private final SecurityContext securityContext;
    private final transient HttpServletRequestHolder httpServletRequestHolder;

    @Inject
    public DispatchServiceImpl(final TaskHandlerBeanRegistry taskHandlerBeanRegistry, final TaskManager taskManager,
                               final SecurityContext securityContext, final HttpServletRequestHolder httpServletRequestHolder) {
        this.taskHandlerBeanRegistry = taskHandlerBeanRegistry;
        this.taskManager = taskManager;
        this.securityContext = securityContext;
        this.httpServletRequestHolder = httpServletRequestHolder;
    }

    @Override
    public <R extends SharedObject> R exec(final Action<R> action) throws RuntimeException {
        final long startTime = System.currentTimeMillis();

        LOGGER.debug("exec() - >> %s %s", action.getClass().getName(), httpServletRequestHolder);

        final TaskHandlerBean taskHandlerBean = taskHandlerBeanRegistry.getTaskHandlerBean(action);

        if (taskHandlerBean == null) {
            throw new EntityServiceException("No handler for " + action.getClass(), null, false);
        }
        final String userName = securityContext.getUserId();
        // Set the id before we can execute this action.
        action.setId(TaskIdFactory.create());
        action.setUserToken(UserTokenUtil.create(userName, httpServletRequestHolder.getSessionId()));

        try {
            final R r = taskManager.exec(action);

            LOGGER.debug("exec() - >> %s returns %s", action.getClass().getName(), r);
            return r;

        } catch (final Throwable t) {
            LOGGER.debug(t.getMessage(), t);
            throw EntityServiceExceptionUtil.create(t);
        } finally {
            LOGGER.debug("exec() - << %s took %s", action.getClass().getName(),
                    ModelStringUtil.formatDurationString(System.currentTimeMillis() - startTime));
        }
    }
}
