/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.core.receive;

import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.meta.api.MetaService;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.StroomStatusCode;
import stroom.receive.common.AttributeMapFilter;
import stroom.receive.common.AttributeMapValidator;
import stroom.receive.common.RequestHandler;
import stroom.receive.common.StreamTargetStreamHandlers;
import stroom.receive.common.StroomStreamException;
import stroom.receive.common.StroomStreamProcessor;
import stroom.security.api.RequestAuthenticator;
import stroom.security.api.SecurityContext;
import stroom.security.api.UserIdentity;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.TaskProgressHandler;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.HttpHeaders;

/**
 * <p>
 * Handle the incoming requests and stream them to disk checking a few things.
 * </p>
 */
class ReceiveDataRequestHandler implements RequestHandler {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ReceiveDataRequestHandler.class);

    private final SecurityContext securityContext;
    private final AttributeMapFilterFactory attributeMapFilterFactory;
    private final StreamTargetStreamHandlers streamTargetStreamHandlerProvider;
    private final TaskContextFactory taskContextFactory;
    private final MetaService metaService;
    private final RequestAuthenticator requestAuthenticator;
    private final ReceiveDataConfig receiveDataConfig;

    @Inject
    public ReceiveDataRequestHandler(final SecurityContext securityContext,
                                     final AttributeMapFilterFactory attributeMapFilterFactory,
                                     final StreamTargetStreamHandlers streamTargetStreamHandlerProvider,
                                     final TaskContextFactory taskContextFactory,
                                     final MetaService metaService,
                                     final RequestAuthenticator requestAuthenticator,
                                     final ReceiveDataConfig receiveDataConfig) {
        this.securityContext = securityContext;
        this.attributeMapFilterFactory = attributeMapFilterFactory;
        this.streamTargetStreamHandlerProvider = streamTargetStreamHandlerProvider;
        this.taskContextFactory = taskContextFactory;
        this.metaService = metaService;
        this.requestAuthenticator = requestAuthenticator;
        this.receiveDataConfig = receiveDataConfig;
    }

    @Override
    public void handle(final HttpServletRequest request, final HttpServletResponse response) {
        securityContext.asProcessingUser(() -> {
            final AttributeMapFilter attributeMapFilter = attributeMapFilterFactory.create();
            final AttributeMap attributeMap = AttributeMapUtil.create(request);
            final String authorisationHeader = attributeMap.get(HttpHeaders.AUTHORIZATION);

            // If token authentication is required but no token is supplied then error.
            if (receiveDataConfig.isRequireTokenAuthentication() &&
                    (authorisationHeader == null || authorisationHeader.isBlank())) {
                throw new StroomStreamException(StroomStatusCode.CLIENT_TOKEN_REQUIRED, attributeMap);
            }

            // Authenticate the request token if there is one.
            final Optional<UserIdentity> optionalUserIdentity = requestAuthenticator.authenticate(request);

            // Add the user identified in the token (if present) to the attribute map.
            optionalUserIdentity
                    .map(UserIdentity::getId)
                    .ifPresent(id -> attributeMap.put("UploadUser", id));

            if (receiveDataConfig.isRequireTokenAuthentication() && optionalUserIdentity.isEmpty()) {
                // If token authentication is required, but we could not verify the token then error.
                throw new StroomStreamException(StroomStatusCode.CLIENT_TOKEN_NOT_AUTHORISED, attributeMap);

            } else {
                // Remove authorization header from attributes.
                attributeMap.remove(HttpHeaders.AUTHORIZATION);

                // Validate the supplied attributes.
                AttributeMapValidator.validate(attributeMap, metaService::getTypes);

                final String feedName;
                if (attributeMapFilter.filter(attributeMap)) {
                    debug("Receiving data", attributeMap);

                    feedName = Optional.ofNullable(attributeMap.get(StandardHeaderArguments.FEED))
                            .map(String::trim)
                            .orElse("");

                    // Get the type name from the header arguments if supplied.
                    String typeName = Optional.ofNullable(attributeMap.get(StandardHeaderArguments.TYPE))
                            .map(String::trim)
                            .orElse("");

                    taskContextFactory.context("Receiving Data", taskContext -> {
                        final Consumer<Long> progressHandler =
                                new TaskProgressHandler(taskContext, "Receiving " + feedName + " - ");
                        try (final InputStream inputStream = request.getInputStream()) {
                            streamTargetStreamHandlerProvider.handle(feedName, typeName, attributeMap, handler -> {
                                final StroomStreamProcessor stroomStreamProcessor = new StroomStreamProcessor(
                                        attributeMap,
                                        handler,
                                        progressHandler);
                                stroomStreamProcessor.processRequestHeader(request);
                                stroomStreamProcessor.processInputStream(inputStream, "");
                            });
                        } catch (final RuntimeException | IOException e) {
                            LOGGER.error(e.getMessage(), e);
                            StroomStreamException.createAndThrow(e, attributeMap);
                        }
                    }).run();
                } else {
                    // Drop the data.
                    debug("Dropping data", attributeMap);
                }

                // Set the response status.
                final StroomStatusCode stroomStatusCode = StroomStatusCode.OK;
                response.setStatus(stroomStatusCode.getHttpCode());
                logSuccess(attributeMap, stroomStatusCode);
            }
        });
    }

    private void logSuccess(final AttributeMap attributeMap, final StroomStatusCode stroomStatusCode) {
        final StringBuilder clientDetailsStringBuilder = new StringBuilder();
        AttributeMapUtil.appendAttributes(
                attributeMap,
                clientDetailsStringBuilder,
                StandardHeaderArguments.X_FORWARDED_FOR,
                StandardHeaderArguments.REMOTE_HOST,
                StandardHeaderArguments.REMOTE_ADDRESS,
                StandardHeaderArguments.RECEIVED_PATH);

        final String clientDetailsStr = clientDetailsStringBuilder.isEmpty()
                ? ""
                : " - " + clientDetailsStringBuilder;

        LOGGER.info(() -> LogUtil.message(
                "Sending success response {} - {}{}",
                stroomStatusCode.getHttpCode(),
                StroomStreamException.buildStatusMessage(stroomStatusCode, attributeMap),
                clientDetailsStr));
    }

    private void debug(final String message, final AttributeMap attributeMap) {
        if (LOGGER.isDebugEnabled()) {
            final List<String> keys = attributeMap
                    .keySet()
                    .stream()
                    .sorted()
                    .toList();
            final StringBuilder sb = new StringBuilder();
            keys.forEach(key -> {
                sb.append(key);
                sb.append("=");
                sb.append(attributeMap.get(key));
                sb.append(",");
            });
            if (sb.length() > 0) {
                sb.setLength(sb.length() - 1);
            }

            LOGGER.debug(message + " (" + sb + ")");
        }
    }
}
