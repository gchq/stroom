package stroom.rs.logging.impl;

import stroom.event.logging.api.DocumentEventLog;
import stroom.event.logging.api.StroomEventLoggingService;
import stroom.security.api.SecurityContext;
import stroom.util.shared.PageResponse;
import stroom.util.shared.ResultPage;
import stroom.util.shared.StroomLog;
import stroom.util.shared.StroomLoggingOperationType;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import event.logging.BaseObject;
import event.logging.Event;
import event.logging.ObjectOutcome;
import event.logging.Query;
import event.logging.util.EventLoggingUtil;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.Optional;

class RequestEventLogImpl implements RequestEventLog {

    private final RequestLoggingConfig config;
    private final DocumentEventLog documentEventLog;
    private final SecurityContext securityContext;
    private final StroomEventLoggingService eventLoggingService;

    @Inject
    RequestEventLogImpl (final RequestLoggingConfig confg, final DocumentEventLog documentEventLog, final SecurityContext securityContext,
                         final StroomEventLoggingService eventLoggingService){
        this.config = confg;
        this.documentEventLog = documentEventLog;
        this.securityContext = securityContext;
        this.eventLoggingService = eventLoggingService;
    }

    @Override
    public void log (final RequestInfo requestInfo, @Nullable final Object responseEntity, final Throwable error){
        if (!shouldLog(requestInfo)){
            return;
        }

        Object requestEntity = requestInfo.getRequestObj();

        String typeId = requestInfo.getResourceClass().getSimpleName() + "." + requestInfo.getMethod().getName();

        switch (requestInfo.getOperationType()){
            case DELETE:
                documentEventLog.delete(requestEntity,typeId,error);
                break;
            case VIEW:
                documentEventLog.view(responseEntity,typeId,error);
                break;
            case CREATE:
                documentEventLog.create(responseEntity,typeId,error);
                break;
            case COPY:
                documentEventLog.copy(requestEntity,typeId,error);
                break;
            case UPDATE:
                documentEventLog.update(requestEntity,responseEntity,typeId,error);
                break;
            case SEARCH:
                logSearch(typeId, requestEntity, responseEntity, error);
                break;
            case EXPORT:
                documentEventLog.download(requestEntity,typeId, error);
                break;
            case IMPORT:
                documentEventLog.upload(requestEntity,typeId, error);
                break;
            case PROCESS:
                documentEventLog.process(requestEntity,typeId, error);
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

    private boolean shouldLog (RequestInfo requestInfo){
        //Global default is enabled via property
        if (requestInfo == null){
            return false;
        }

        StroomLoggingOperationType methodOpType = null;
        if (requestInfo.getMethod().getAnnotation(StroomLog.class) != null){
            methodOpType = requestInfo.getMethod().getAnnotation(StroomLog.class).value();
        }
        StroomLoggingOperationType classOpType = null;
        if (requestInfo.getResourceClass().getAnnotation(StroomLog.class) != null){
            classOpType = requestInfo.getResourceClass().getAnnotation(StroomLog.class).value();
        }

        //Method can override class and class can override global setting
        if (StroomLoggingOperationType.UNLOGGED.equals(methodOpType)){
            return false;
        }

        if (StroomLoggingOperationType.UNLOGGED.equals(classOpType) && methodOpType == null){
            return false;
        }

        if (!config.isGlobalLoggingEnabled() && classOpType == null && methodOpType == null ) {
            return false;
        }

        return true;
    }

    private void logSearch (String typeId, Object requestEntity, Object responseEntity, Throwable error){
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
                } else {
                    listContents = firstVal.get().getClass().getSimpleName() + "s";
                }

            }
        }
        documentEventLog.search(typeId, query, listContents, pageResponse, error);
    }
}
