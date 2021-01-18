package stroom.rs.logging.impl;

import stroom.util.shared.HasId;
import stroom.util.shared.HasUuid;
import stroom.util.shared.StroomLoggingOperationType;

import java.util.Optional;
import java.util.stream.Collectors;

import static stroom.rs.logging.impl.StroomServerLoggingFilterImpl.LOGGER;

class RequestInfo {

    private final ContainerResourceInfo containerResourceInfo;
    private final Object requestObj;


    public RequestInfo(final ContainerResourceInfo containerResourceInfo) {
        this.containerResourceInfo = containerResourceInfo;
        this.requestObj = findRequestObj();
    }

    public RequestInfo(final ContainerResourceInfo containerResourceInfo, Object requestObj) {
        this.containerResourceInfo = containerResourceInfo;
        this.requestObj = requestObj;
    }

    public Object getRequestObj() {
        return requestObj;
    }

    public ContainerResourceInfo getContainerResourceInfo() {
        return containerResourceInfo;
    }

    public boolean shouldLog (boolean logByDefault){
        return getContainerResourceInfo().shouldLog(logByDefault);
    }



    private Object findRequestObj(){
        Optional<String> paramNameOpt = containerResourceInfo.getRequestContext().getUriInfo().getPathParameters(true).keySet().stream().findFirst();
        if (paramNameOpt.isEmpty()){
            return null;
        }

        String paramName = paramNameOpt.get();

        if (containerResourceInfo.getRequestContext().getUriInfo().getPathParameters(true).keySet().size() > 1){
            LOGGER.warn("The request " + containerResourceInfo.getRequestContext().getUriInfo().getPath(false) + " contains multiple parameters");
        }

        String paramValue = containerResourceInfo.getRequestContext().getUriInfo().getPathParameters(true).get(paramName).stream().collect(Collectors.joining(", "));
        if ("id".equals(paramName)){
            return new ObjectId(paramValue);
        } else if ("uuid".equals(paramName)){
            return new ObjectUuid(paramValue);
        }

        return null;
    }

    private static class ObjectId implements HasId {
        private final long id;

        public ObjectId(String val){
            long id = 0;
            try {
                id = Long.parseLong(val);
            } catch (NumberFormatException ex) {
                LOGGER.error("Unable to log id of entity with non-numeric id " + val);
            } finally {
                this.id = id;
            }
        }

        @Override
        public long getId() {
            return id;
        }
    }

    private static class ObjectUuid implements HasUuid {
        private final String uuid;

        public ObjectUuid(String uuid){
            this.uuid = uuid;
        }

        @Override
        public String getUuid() {
            return uuid;
        }
    }

}
