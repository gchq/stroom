/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.event.logging.rs.impl;

import stroom.dropwizard.common.DelegatingExceptionMapper;
import stroom.event.logging.api.ThreadLocalLogState;
import stroom.event.logging.impl.LoggingConfig;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.event.logging.rs.api.RestResourceAutoLogger;
import stroom.security.api.SecurityContext;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.ServerErrorException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.WriterInterceptorContext;
import org.glassfish.jersey.message.MessageUtils;

import java.io.IOException;

@Singleton
public class RestResourceAutoLoggerImpl implements RestResourceAutoLogger {

    static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(RestResourceAutoLoggerImpl.class);

    private static final ObjectMapper OBJECT_MAPPER = JsonUtil.getMapper();

    private static final String REQUEST_LOG_INFO_PROPERTY = "stroom.rs.logging.request";

    //Accessed via Provider<T> in line with code standards for injected fields that are expected to be stateless
    private final Provider<LoggingConfig> loggingConfigProvider;
    private final Provider<RequestEventLog> requestEventLogProvider;
    private final Provider<SecurityContext> securityContextProvider;
    private final Provider<DelegatingExceptionMapper> delegatingExceptionMapperProvider;

    @Context
    private HttpServletRequest request;

    @Context
    private ResourceInfo resourceInfo;

    @Context
    private ResourceContext resourceContext;


    @Inject
    RestResourceAutoLoggerImpl(final Provider<SecurityContext> securityContextProvider,
                               final Provider<RequestEventLog> requestEventLogProvider,
                               final Provider<LoggingConfig> loggingConfigProvider,
                               final Provider<DelegatingExceptionMapper> delegatingExceptionMapperProvider) {
        this.securityContextProvider = securityContextProvider;
        this.requestEventLogProvider = requestEventLogProvider;
        this.loggingConfigProvider = loggingConfigProvider;
        this.delegatingExceptionMapperProvider = delegatingExceptionMapperProvider;
    }

    //For unit test use
    RestResourceAutoLoggerImpl(final Provider<SecurityContext> securityContextProvider,
                               final Provider<RequestEventLog> requestEventLogProvider,
                               final Provider<LoggingConfig> loggingConfigProvider,
                               final ResourceInfo resourceInfo,
                               final HttpServletRequest request,
                               final Provider<DelegatingExceptionMapper> delegatingExceptionMapperProvider) {
        this.securityContextProvider = securityContextProvider;
        this.requestEventLogProvider = requestEventLogProvider;
        this.loggingConfigProvider = loggingConfigProvider;
        this.resourceInfo = resourceInfo;
        this.request = request;
        this.delegatingExceptionMapperProvider = delegatingExceptionMapperProvider;
    }

    @Override
    public Response toResponse(final Throwable exception) {
        if (request != null) {
            final Object object = request.getAttribute(REQUEST_LOG_INFO_PROPERTY);
            if (object != null) {
                final RequestInfo requestInfo = (RequestInfo) object;
                if (OperationType.MANUALLY_LOGGED.equals(requestInfo.getContainerResourceInfo().getOperationType())) {
                    if (!ThreadLocalLogState.hasLogged()) {
                        LOGGER.debug("Expected manual logging to have happened: "
                                     + NullSafe.get(requestInfo.getContainerResourceInfo(),
                                ContainerResourceInfo::getRequestContext,
                                ContainerRequestContext::getUriInfo,
                                UriInfo::getRequestUri));
                    }
                } else {
                    requestEventLogProvider.get().log(requestInfo, null, exception);
                }
                request.setAttribute(REQUEST_LOG_INFO_PROPERTY, null);
            }
        } else {
            LOGGER.warn("Unable to create audit log for exception, request is null", exception);
        }

        ThreadLocalLogState.setLogged(false);

        if (exception instanceof final WebApplicationException wae) {
            return wae.getResponse();
        } else {
            return delegatingExceptionMapperProvider.get().toResponse(exception);
        }
    }

    @Override
    public void aroundWriteTo(final WriterInterceptorContext writerInterceptorContext)
            throws WebApplicationException {
        // When we come in here, the resource method will have completed successfully,
        // so we can intercept its returned value (the entity) before jakarta.ws.rs
        // serialises it to json (or some other mime type).
        // This means we can include the returned entity in the audit log event.
        Exception exception = null;
        try {
            writerInterceptorContext.proceed();
        } catch (final Exception ex) {
            // This is either an error serialising the entity or in another interceptor in the chain.
            // Swallow and save the ex, so we can log the failure
            LOGGER.error("Error in Jakarta RS filter chain processing, which is likely " +
                         "a problem serialising the resource's response entity. " +
                         "entityType: {}, entity: {}, exceptionMessage: {}",
                    writerInterceptorContext.getType(),
                    writerInterceptorContext.getEntity(),
                    LogUtil.exceptionMessage(ex), ex);
            exception = ex;
        }

        if (request.getAttribute(REQUEST_LOG_INFO_PROPERTY) instanceof final RequestInfo requestInfo) {
            if (OperationType.MANUALLY_LOGGED == requestInfo.getContainerResourceInfo().getOperationType()) {
                if (!ThreadLocalLogState.hasLogged()) {
                    LOGGER.debug("Expected manual logging to have happened: "
                                 + NullSafe.get(requestInfo.getContainerResourceInfo(),
                            ContainerResourceInfo::getRequestContext,
                            ContainerRequestContext::getUriInfo,
                            UriInfo::getRequestUri));
                }
            } else {
                // The log() method will check if logging is required, e.g. for UNLOGGED
                final Object entity = writerInterceptorContext.getEntity();
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("aroundWriteTo() - type: {}, entity: {}, exceptionMessage: {}",
                            writerInterceptorContext.getType(),
                            entity,
                            LogUtil.exceptionMessage(exception));
                }
                if (exception != null) {
                    // Log it as a failure
                    requestEventLogProvider.get()
                            .log(requestInfo, entity, exception);
                } else {
                    requestEventLogProvider.get()
                            .log(requestInfo, entity);
                }
            }
            request.setAttribute(REQUEST_LOG_INFO_PROPERTY, null);
        }

        ThreadLocalLogState.setLogged(false);

        // Now we have done our audit logging, we can re-throw any error so the caller gets it
        if (exception instanceof final WebApplicationException wae) {
            throw wae;
        } else if (exception != null) {
            throw new ServerErrorException(LogUtil.exceptionMessage(exception), Status.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public void filter(final ContainerRequestContext context) throws IOException {
        final ContainerResourceInfo containerResourceInfo =
                new ContainerResourceInfo(
                        resourceContext,
                        securityContextProvider.get(),
                        resourceInfo,
                        context,
                        loggingConfigProvider.get().isLogEveryRestCallEnabled());

        if (containerResourceInfo.shouldLog()) {
            if (context.hasEntity()) {
                final RequestEntityCapturingInputStream stream = new RequestEntityCapturingInputStream(
                        resourceInfo,
                        context.getEntityStream(),
                        OBJECT_MAPPER,
                        MessageUtils.getCharset(context.getMediaType()));
                context.setEntityStream(stream);

                request.setAttribute(REQUEST_LOG_INFO_PROPERTY,
                        new RequestInfo(securityContextProvider.get(),
                                containerResourceInfo,
                                stream.getRequestEntity()));
            } else {
                request.setAttribute(REQUEST_LOG_INFO_PROPERTY,
                        new RequestInfo(securityContextProvider.get(), containerResourceInfo));
            }
        }
    }


    //Needed for some unit tests
    void setResourceContext(final ResourceContext resourceContext) {
        this.resourceContext = resourceContext;
    }
}
