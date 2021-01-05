package stroom.rs.logging.impl;

import stroom.event.logging.api.DocumentEventLog;

import javax.inject.Inject;

class RequestEventLogImpl implements RequestEventLog {

    private final DocumentEventLog documentEventLog;

    @Inject
    RequestEventLogImpl (final DocumentEventLog documentEventLog){
        this.documentEventLog = documentEventLog;
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
                documentEventLog.create(requestEntity,typeId,error);
                break;
        }
    }

    @Override
    public void log (LoggingInfo info, Object requestEntity, Object responseEntity){
      log (info, requestEntity, responseEntity, null);
    }
}
