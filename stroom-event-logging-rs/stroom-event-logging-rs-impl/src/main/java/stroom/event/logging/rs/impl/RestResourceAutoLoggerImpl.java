/*
 * Copyright 2020 Crown Copyright
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
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.event.logging.rs.api.RestResourceAutoLogger;
import stroom.security.api.SecurityContext;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
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
                        LOGGER.error("Expected manual logging to have happened: " + requestInfo);
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

        if (exception instanceof WebApplicationException) {
            WebApplicationException wae = (WebApplicationException) exception;
            return wae.getResponse();
        } else {
            return delegatingExceptionMapperProvider.get().toResponse(exception);
        }
    }

    @Override
    public void aroundWriteTo(final WriterInterceptorContext writerInterceptorContext)
            throws WebApplicationException {
        try {
            writerInterceptorContext.proceed();
        } catch (Exception ex) {
            LOGGER.error("Error in Java RS filter chain processing.", ex);
        }

        final Object object = request.getAttribute(REQUEST_LOG_INFO_PROPERTY);
        if (object != null) {
            final RequestInfo requestInfo = (RequestInfo) object;
            if (OperationType.MANUALLY_LOGGED.equals(requestInfo.getContainerResourceInfo().getOperationType())) {
                if (!ThreadLocalLogState.hasLogged()) {
                    LOGGER.error("Expected manual logging to have happened: " + requestInfo);
                }
            } else {
                requestEventLogProvider.get().log(requestInfo, writerInterceptorContext.getEntity());
            }
            request.setAttribute(REQUEST_LOG_INFO_PROPERTY, null);
        }

        ThreadLocalLogState.setLogged(false);
    }

    @Override
    public void filter(final ContainerRequestContext context) throws IOException {
        ContainerResourceInfo containerResourceInfo =
                new ContainerResourceInfo(
                        resourceContext,
                        securityContextProvider.get(),
                        resourceInfo,
                        context,
                        loggingConfigProvider.get().isLogEveryRestCallEnabled());

        if (containerResourceInfo.shouldLog(loggingConfigProvider.get())) {
            if (context.hasEntity()) {
                final RequestEntityCapturingInputStream stream = new RequestEntityCapturingInputStream(resourceInfo,
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
