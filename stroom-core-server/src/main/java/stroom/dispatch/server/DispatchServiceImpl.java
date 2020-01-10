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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import stroom.dispatch.shared.DispatchService;
import stroom.entity.server.util.BaseEntityDeProxyProcessor;
import stroom.entity.server.util.EntityServiceExceptionUtil;
import stroom.entity.shared.Action;
import stroom.entity.shared.EntityServiceException;
import stroom.entity.shared.PermissionException;
import stroom.feed.server.UserAgentSessionUtil;
import stroom.security.SecurityContext;
import stroom.task.server.TaskHandlerBean;
import stroom.task.server.TaskHandlerBeanRegistry;
import stroom.task.server.TaskManager;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.SharedObject;
import stroom.util.task.TaskIdFactory;

import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Component(DispatchServiceImpl.BEAN_NAME)
public class DispatchServiceImpl extends XsrfProtectedServiceServlet implements DispatchService {
    public static final String BEAN_NAME = "dispatchService";

    private static final Logger LOGGER = LoggerFactory.getLogger(DispatchServiceImpl.class);

    private final TaskHandlerBeanRegistry taskHandlerBeanRegistry;
    private final TaskManager taskManager;
    private final SecurityContext securityContext;

    @Inject
    public DispatchServiceImpl(final TaskHandlerBeanRegistry taskHandlerBeanRegistry,
                               final TaskManager taskManager,
                               final SecurityContext securityContext) {
        this.taskHandlerBeanRegistry = taskHandlerBeanRegistry;
        this.taskManager = taskManager;
        this.securityContext = securityContext;
    }

    @Override
    public <R extends SharedObject> R exec(final Action<R> action) throws RuntimeException {
        final long startTime = System.currentTimeMillis();

        LOGGER.debug("exec() - >> {}", action.getClass().getName());

        final TaskHandlerBean taskHandlerBean = taskHandlerBeanRegistry.getTaskHandlerBean(action);

        if (taskHandlerBean == null) {
            throw new EntityServiceException("No handler for " + action.getClass(), null, false);
        }
        // Set the id before we can execute this action.
        action.setId(TaskIdFactory.create());
        action.setUserIdentity(securityContext.getUserIdentity());

        try {
            final Action<R> processedAction = processAction(action);
            final R r = taskManager.exec(processedAction);
            final R processedResult = processResult(r);

            LOGGER.debug("exec() - >> {} returns {}", action.getClass().getName(), processedResult);
            return processedResult;
        } catch (final PermissionException e) {
            LOGGER.debug(e.getMessage(), e);
            throw new EntityServiceException(e.getGenericMessage());
        } catch (final Throwable t) {
            LOGGER.debug(t.getMessage(), t);
            throw EntityServiceExceptionUtil.create(t);
        } finally {
            LOGGER.debug("exec() - << {} took {}", action.getClass().getName(),
                    ModelStringUtil.formatDurationString(System.currentTimeMillis() - startTime));
        }
    }

    @SuppressWarnings("unchecked")
    private <R extends SharedObject> Action<R> processAction(final Action<R> action) {
        try {
            final BaseEntityDeProxyProcessor processor = new BaseEntityDeProxyProcessor(true);
            return (Action<R>) processor.process(action);
        } catch (final Throwable e) {
            throw EntityServiceExceptionUtil.create(e);
        }
    }

    @SuppressWarnings("unchecked")
    private <R extends SharedObject> R processResult(final R result) {
        try {
            final BaseEntityDeProxyProcessor processor = new BaseEntityDeProxyProcessor(false);
            return (R) processor.process(result);
        } catch (final Throwable e) {
            throw EntityServiceExceptionUtil.create(e);
        }
    }

    @Override
    protected void service(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
        try {
            // Set the user agent in the session.
            UserAgentSessionUtil.set(req);
            super.service(req, resp);

        } catch (final Exception ex) {
            LOGGER.error("handle() - {}", req.getRequestURI(), ex);
            throw ex;
        }
    }

    @Override
    public void log(final String message, final Throwable t) {
        LOGGER.warn(getServletName() + ": " + message);
        LOGGER.debug(getServletName() + ": " + message, t);
    }
}
