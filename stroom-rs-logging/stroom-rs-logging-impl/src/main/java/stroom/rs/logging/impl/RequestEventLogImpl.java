package stroom.rs.logging.impl;

import stroom.event.logging.api.DocumentEventLog;
import stroom.event.logging.api.StroomEventLoggingService;
import stroom.security.api.SecurityContext;
import stroom.util.shared.PageResponse;
import stroom.util.shared.ResultPage;
import stroom.util.shared.StroomLoggingOperationType;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import event.logging.Query;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.Optional;

class RequestEventLogImpl implements RequestEventLog {

    private final RequestLoggingConfig config;
    private final DocumentEventLog documentEventLog;
    private final SecurityContext securityContext;
    private final StroomEventLoggingService eventLoggingService;

    @Inject
    RequestEventLogImpl (final RequestLoggingConfig config, final DocumentEventLog documentEventLog, final SecurityContext securityContext,
                         final StroomEventLoggingService eventLoggingService){
        this.config = config;
        this.documentEventLog = documentEventLog;
        this.securityContext = securityContext;
        this.eventLoggingService = eventLoggingService;
    }

    @Override
    public void log (final RequestInfo requestInfo, @Nullable final Object responseEntity, final Throwable error){
        if (!requestInfo.shouldLog(config.isGlobalLoggingEnabled())){
            return;
        }

        Object requestEntity = requestInfo.getRequestObj();

        final String typeId = requestInfo.getContainerResourceInfo().getTypeId();
        final String descriptionVerb = requestInfo.getContainerResourceInfo().getVerbFromAnnotations();

        switch (requestInfo.getContainerResourceInfo().getOperationType()){
            case DELETE:
                documentEventLog.delete(requestEntity,typeId, descriptionVerb, error);
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
                documentEventLog.update(requestEntity,responseEntity,typeId, descriptionVerb, error);
                break;
            case SEARCH:
                logSearch(typeId, requestEntity, responseEntity,  descriptionVerb, error);
                break;
            case EXPORT:
                documentEventLog.download(requestEntity,typeId, descriptionVerb, error);
                break;
            case IMPORT:
                documentEventLog.upload(requestEntity,typeId, descriptionVerb, error);
                break;
            case PROCESS:
                documentEventLog.process(requestEntity,typeId, descriptionVerb, error);
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


    private void logSearch (String typeId, Object requestEntity, Object responseEntity, String descriptionVerb, Throwable error){
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
        documentEventLog.search(typeId, query, listContents, pageResponse, descriptionVerb, error);
    }
}
