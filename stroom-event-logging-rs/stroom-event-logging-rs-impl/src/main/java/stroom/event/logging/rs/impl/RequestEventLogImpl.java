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

import stroom.event.logging.api.DocumentEventLog;
import stroom.event.logging.api.EventActionDecorator;
import stroom.event.logging.api.StroomEventLoggingService;
import stroom.event.logging.impl.LoggingConfig;
import stroom.security.api.SecurityContext;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.PageResponse;
import stroom.util.shared.ResultPage;

import com.google.inject.Injector;
import event.logging.EventAction;
import event.logging.ProcessEventAction;
import event.logging.Query;
import event.logging.SearchEventAction;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;

import java.util.Optional;

class RequestEventLogImpl implements RequestEventLog {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(RequestEventLogImpl.class);

    private final Injector injector;
    private final LoggingConfig config;
    private final DocumentEventLog documentEventLog;
    private final SecurityContext securityContext;
    private final StroomEventLoggingService eventLoggingService;

    @Inject
    RequestEventLogImpl(final Injector injector,
                        final LoggingConfig config,
                        final DocumentEventLog documentEventLog,
                        final SecurityContext securityContext,
                        final StroomEventLoggingService eventLoggingService) {
        this.injector = injector;
        this.config = config;
        this.documentEventLog = documentEventLog;
        this.securityContext = securityContext;
        this.eventLoggingService = eventLoggingService;
    }

    @Override
    public void log(final RequestInfo requestInfo, @Nullable final Object responseEntity, final Throwable error) {
        final ContainerResourceInfo containerResourceInfo = requestInfo.getContainerResourceInfo();
        if (!containerResourceInfo.shouldLog()) {
            return;
        }

        final Object requestEntity = requestInfo.getRequestObj();
        final String typeId = containerResourceInfo.getTypeId();
        final String descriptionVerb = containerResourceInfo.getVerbFromAnnotations();
        final Class<? extends EventActionDecorator> decoratorClass =
                containerResourceInfo.getEventActionDecoratorClass();

        LOGGER.debug(() -> LogUtil.message("log() - typeId: '{}', descVerb: '{}', decoratorClass: {}, opType: {}",
                typeId, descriptionVerb, decoratorClass.getSimpleName(), containerResourceInfo.getOperationType()));

        switch (containerResourceInfo.getOperationType()) {
            case DELETE:
                documentEventLog.delete(requestInfo.getBeforeCallObj(), typeId, descriptionVerb, error);
                break;
            case VIEW:
                documentEventLog.view(responseEntity, typeId, descriptionVerb, error);
                break;
            case CREATE:
                documentEventLog.create(responseEntity, typeId, descriptionVerb, error);
                break;
            case COPY:
                documentEventLog.copy(requestEntity, responseEntity, typeId, descriptionVerb, error);
                break;
            case UPDATE:
                if (!RequestInfo.objectIsLoggable(responseEntity)) {
                    // A success status or similar is returned, so we need to find the actual entity being updated
                    // by hitting a fetch method on the resource to get the post update entity. Relies on the resource
                    // implementing one of the FetchWith.... interfaces.
                    documentEventLog.update(requestInfo.getBeforeCallObj(),
                            requestInfo.getAfterCallObj(securityContext), typeId, descriptionVerb, error);
                } else {
                    // The request contains the before and the response contains the after
                    documentEventLog.update(requestInfo.getBeforeCallObj(),
                            responseEntity,
                            typeId,
                            descriptionVerb,
                            error);
                }
                break;
            case SEARCH:
                logSearch(decoratorClass, typeId, requestEntity, responseEntity, descriptionVerb, error);
                break;
            case EXPORT:
                documentEventLog.download(requestEntity, typeId, descriptionVerb, error);
                break;
            case IMPORT:
                documentEventLog.upload(requestEntity, typeId, descriptionVerb, error);
                break;
            case PROCESS:
                logProcess(decoratorClass, typeId, requestEntity, descriptionVerb, error);
                break;
            case UNKNOWN:
                documentEventLog.unknownOperation(requestEntity,
                        typeId,
                        "Uncategorised remote API call invoked",
                        error);
                break;
        }
    }

    @Override
    public void log(final RequestInfo info, final Object responseEntity) {
        log(info, responseEntity, null);
    }

    private <T extends EventAction> EventActionDecorator<T> createDecorator(
            final Class<? extends EventActionDecorator> decoratorClass) {
        if (decoratorClass == null) {
            return null;
        }
        final EventActionDecorator decorator = injector.getInstance(decoratorClass);
        return decorator;
    }

    private void logProcess(final Class<? extends EventActionDecorator> decoratorClass,
                            final String typeId,
                            final Object requestEntity,
                            final String descriptionVerb,
                            final Throwable error) {
        final EventActionDecorator<ProcessEventAction> decorator = createDecorator(decoratorClass);
        documentEventLog.process(requestEntity, typeId, descriptionVerb, error, decorator);
    }

    private void logSearch(final Class<? extends EventActionDecorator> decoratorClass,
                           final String typeId,
                           final Object requestEntity,
                           final Object responseEntity,
                           final String descriptionVerb,
                           final Throwable error) {
        final Query query = new Query();

        if (requestEntity != null) {
            String queryJson;
            try {
                queryJson = JsonUtil.writeValueAsString(requestEntity, false);
            } catch (final RuntimeException ex) {
                queryJson = "Invalid";
            }

            query.setRaw(queryJson);
        }
        PageResponse pageResponse = null;

        String listContents = "Objects";
        if (responseEntity instanceof PageResponse) {
            pageResponse = (PageResponse) responseEntity;
        } else if (responseEntity instanceof ResultPage) {
            final ResultPage<?> resultPage = (ResultPage) responseEntity;
            pageResponse = resultPage.getPageResponse();
            final Optional<?> firstVal = resultPage.getValues().stream().findFirst();
            if (firstVal.isPresent()) {
                if (firstVal.get().getClass().getSimpleName().endsWith("s")) {
                    listContents = firstVal.get().getClass().getSimpleName() + "es";
                } else if (firstVal.get().getClass().getSimpleName().endsWith("y")) {
                    listContents = firstVal.get().getClass().getSimpleName().substring(0,
                            firstVal.get().getClass().getSimpleName().length() - 1) + "ies";
                } else {
                    listContents = firstVal.get().getClass().getSimpleName() + "s";
                }

            }
        }

        final EventActionDecorator<SearchEventAction> decorator = createDecorator(decoratorClass);
        documentEventLog.search(typeId, query, listContents, pageResponse, descriptionVerb, error, decorator);
    }
}
