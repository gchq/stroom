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
    public void log (LoggingInfo info, Object requestEntity, Object responseEntity){
        if (info == null){
            return;
        }

        switch (info.getOperationType()){
            case DELETE:
                documentEventLog.delete(requestEntity,null);
                break;
            case VIEW:
                documentEventLog.view(responseEntity,null);
                break;
            case CREATE:
                documentEventLog.create(requestEntity,null);
                break;
        }
    }
}
