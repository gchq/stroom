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

package stroom.core.dispatch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.dispatch.shared.DispatchService;
import stroom.docref.SharedObject;
import stroom.task.api.TaskHandler;
import stroom.task.api.TaskIdFactory;
import stroom.task.api.TaskManager;
import stroom.task.impl.TaskHandlerRegistry;
import stroom.task.shared.Action;
import stroom.task.shared.Task;
import stroom.util.EntityServiceExceptionUtil;
import stroom.util.shared.ResourcePaths;
import stroom.util.servlet.SessionIdProvider;
import stroom.util.servlet.UserAgentSessionUtil;
import stroom.util.shared.EntityServiceException;
import stroom.util.shared.IsServlet;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.PermissionException;

import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;

public class DispatchServiceImpl extends XsrfProtectedServiceServlet implements DispatchService, IsServlet {
    private static final Logger LOGGER = LoggerFactory.getLogger(DispatchServiceImpl.class);

    private static final Set<String> PATH_SPECS = Set.of(ResourcePaths.DISPATCH_RPC_PATH);

    private final TaskHandlerRegistry taskHandlerBeanRegistry;
    private final TaskManager taskManager;
    private final SessionIdProvider sessionIdProvider;

    @Inject
    public DispatchServiceImpl(final TaskHandlerRegistry taskHandlerBeanRegistry,
                               final TaskManager taskManager,
                               final SessionIdProvider sessionIdProvider) {
        this.taskHandlerBeanRegistry = taskHandlerBeanRegistry;
        this.taskManager = taskManager;
        this.sessionIdProvider = sessionIdProvider;
    }

    @Override
    public <R extends SharedObject> R exec(final Action<R> action) throws EntityServiceException {
        final long startTime = System.currentTimeMillis();

        LOGGER.debug("exec() - >> {} {}", action.getClass().getName(), sessionIdProvider.get());

        final TaskHandler<Task<R>, R> taskHandlerBean = taskHandlerBeanRegistry.findHandler(action);
        if (taskHandlerBean == null) {
            throw new EntityServiceException("No handler for " + action.getClass(), null, false);
        }

        // Set the id before we can execute this action.
        action.setId(TaskIdFactory.create());

        try {
            final R r = taskManager.exec(action);
            LOGGER.debug("exec() - >> {} returns {}", action.getClass().getName(), r);
            return r;
        } catch (final PermissionException e) {
            LOGGER.debug(e.getMessage(), e);
            throw new EntityServiceException(e.getGenericMessage());
        } catch (final RuntimeException e) {
            LOGGER.debug(e.getMessage(), e);
            throw EntityServiceExceptionUtil.create(e);
        } finally {
            LOGGER.debug("exec() - << {} took {}", action.getClass().getName(),
                    ModelStringUtil.formatDurationString(System.currentTimeMillis() - startTime));
        }
    }

    @Override
    protected void service(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
        try {
            // Set the user agent in the session.
            UserAgentSessionUtil.set(req);
            super.service(req, resp);

        } catch (final RuntimeException e) {
            LOGGER.error("handle() - {}", req.getRequestURI(), e);
            throw e;
        }
    }

    /**
     * @return The part of the path that will be in addition to any base path,
     * e.g. "/datafeed".
     */
    @Override
    public Set<String> getPathSpecs() {
        return PATH_SPECS;
    }

    @Override
    public void log(final String message, final Throwable t) {
        LOGGER.warn(getServletName() + ": " + message);
        LOGGER.debug(getServletName() + ": " + message, t);
    }
}
