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

import stroom.event.logging.api.DocumentEventLog;
import stroom.event.logging.api.EventActionDecorator;
import stroom.event.logging.api.StroomEventLoggingService;
import stroom.event.logging.impl.LoggingConfig;
import stroom.security.api.SecurityContext;
import stroom.util.shared.PageResponse;
import stroom.util.shared.ResultPage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Injector;
import event.logging.EventAction;
import event.logging.ProcessEventAction;
import event.logging.Query;
import event.logging.SearchEventAction;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.Optional;

class RequestEventLogImpl implements RequestEventLog {

    private final Injector injector;
    private final LoggingConfig config;
    private final DocumentEventLog documentEventLog;
    private final SecurityContext securityContext;
    private final StroomEventLoggingService eventLoggingService;

    @Inject
    RequestEventLogImpl (final Injector injector, final LoggingConfig config, final DocumentEventLog documentEventLog, final SecurityContext securityContext,
                         final StroomEventLoggingService eventLoggingService){
        this.injector = injector;
        this.config = config;
        this.documentEventLog = documentEventLog;
        this.securityContext = securityContext;
        this.eventLoggingService = eventLoggingService;
    }

    @Override
    public void log (final RequestInfo requestInfo, @Nullable final Object responseEntity, final Throwable error){
        if (!requestInfo.getContainerResourceInfo().shouldLog(config)) {
            return;
        }

        Object requestEntity = requestInfo.getRequestObj();

        final String typeId = requestInfo.getContainerResourceInfo().getTypeId();
        final String descriptionVerb = requestInfo.getContainerResourceInfo().getVerbFromAnnotations();
        final Class<? extends EventActionDecorator> decoratorClass = requestInfo.getContainerResourceInfo().getEventActionDecoratorClass();

        switch (requestInfo.getContainerResourceInfo().getOperationType()){
            case DELETE:
                documentEventLog.delete(requestInfo.getBeforeCallObj(),typeId, descriptionVerb, error);
                break;
            case VIEW:
                documentEventLog.view(responseEntity,typeId, descriptionVerb,error);
                break;
            case CREATE:
                documentEventLog.create(responseEntity,typeId, descriptionVerb,error);
                break;
            case COPY:
                documentEventLog.copy(requestEntity,typeId, descriptionVerb,error);
                break;
            case UPDATE:
                documentEventLog.update(requestInfo.getBeforeCallObj(),responseEntity,typeId, descriptionVerb, error);
                break;
            case SEARCH:
                logSearch(decoratorClass, typeId, requestEntity, responseEntity,  descriptionVerb, error);
                break;
            case EXPORT:
                documentEventLog.download(requestEntity,typeId, descriptionVerb, error);
                break;
            case IMPORT:
                documentEventLog.upload(requestEntity,typeId, descriptionVerb, error);
                break;
            case PROCESS:
                logProcess(decoratorClass, typeId, requestEntity, descriptionVerb, error);
                break;
            case UNKNOWN:
                documentEventLog.unknownOperation(requestEntity,typeId, "Uncategorised remote API call invoked", error);
                break;
        }
    }

    @Override
    public void log (RequestInfo info, Object responseEntity){
      log (info, responseEntity, null);
    }

    private <T extends EventAction> EventActionDecorator<T>
        createDecorator(Class <? extends EventActionDecorator> decoratorClass){
        if (decoratorClass == null){
            return null;
        }
        EventActionDecorator decorator = injector.getInstance(decoratorClass);
        return decorator;
    }

    private void logProcess (Class<? extends EventActionDecorator> decoratorClass, String typeId, Object requestEntity, String descriptionVerb, Throwable error) {
        EventActionDecorator<ProcessEventAction> decorator = createDecorator(decoratorClass);
        documentEventLog.process(requestEntity,typeId, descriptionVerb, error, decorator);
    }

    private void logSearch (Class<? extends EventActionDecorator> decoratorClass, String typeId, Object requestEntity, Object responseEntity, String descriptionVerb, Throwable error){
        Query query = new Query();

        if (requestEntity != null) {
            String queryJson;
            ObjectMapper mapper = new ObjectMapper();
            try {
                queryJson = mapper.writeValueAsString(requestEntity);
            } catch (JsonProcessingException ex) {
                queryJson = "Invalid";
            }

            query.setRaw(queryJson);
        }
        PageResponse pageResponse = null;

        String listContents = "Objects";
        if (responseEntity instanceof PageResponse){
            pageResponse = (PageResponse) responseEntity;
        } else if (responseEntity instanceof ResultPage){
            ResultPage<?> resultPage = (ResultPage) responseEntity;
            pageResponse = resultPage.getPageResponse();
            Optional<?> firstVal = resultPage.getValues().stream().findFirst();
            if (firstVal.isPresent()){
                if (firstVal.get().getClass().getSimpleName().endsWith("s")){
                    listContents = firstVal.get().getClass().getSimpleName() + "es";
                } else if (firstVal.get().getClass().getSimpleName().endsWith("y")){
                    listContents = firstVal.get().getClass().getSimpleName().substring(0,
                            firstVal.get().getClass().getSimpleName().length() - 1) + "ies";
                } else {
                    listContents = firstVal.get().getClass().getSimpleName() + "s";
                }

            }
        }

        EventActionDecorator<SearchEventAction> decorator = createDecorator(decoratorClass);
        documentEventLog.search(typeId, query, listContents, pageResponse, descriptionVerb, error, decorator);
    }
}
