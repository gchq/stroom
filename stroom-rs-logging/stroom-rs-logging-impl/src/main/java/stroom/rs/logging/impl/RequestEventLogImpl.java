package stroom.rs.logging.impl;

import stroom.event.logging.api.DocumentEventLog;
import stroom.event.logging.api.StroomEventLoggingService;
import stroom.security.api.SecurityContext;
import stroom.util.shared.PageResponse;
import stroom.util.shared.ResultPage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import event.logging.BaseObject;
import event.logging.Event;
import event.logging.ObjectOutcome;
import event.logging.Query;
import event.logging.util.EventLoggingUtil;

import javax.inject.Inject;
import java.util.Optional;

class RequestEventLogImpl implements RequestEventLog {

    private final DocumentEventLog documentEventLog;
    private final SecurityContext securityContext;
    private final StroomEventLoggingService eventLoggingService;

    @Inject
    RequestEventLogImpl (final DocumentEventLog documentEventLog, final SecurityContext securityContext,
                         final StroomEventLoggingService eventLoggingService){
        this.documentEventLog = documentEventLog;
        this.securityContext = securityContext;
        this.eventLoggingService = eventLoggingService;
    }

    @Override
    public void log (LoggingInfo info, Object requestEntity, Object responseEntity, Throwable error){
        if (info == null){
            return;
        }

        String typeId = info.getResourceClass().getSimpleName() + "." + info.getMethod().getName();

        switch (info.getOperationType()){
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
                        listContents = firstVal.get().getClass().getSimpleName() + "s";
                    }
                }

                documentEventLog.search(typeId, query, listContents, pageResponse, error);
                break;
            case EXPORT:
            case IMPORT:

            case PROCESS:
            case UNKNOWN:
                break;
        }
    }

    @Override
    public void log (LoggingInfo info, Object requestEntity, Object responseEntity){
      log (info, requestEntity, responseEntity, null);
    }

    private void log (){

    }
}
