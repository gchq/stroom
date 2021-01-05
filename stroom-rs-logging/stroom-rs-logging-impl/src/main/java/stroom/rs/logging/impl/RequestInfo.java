package stroom.rs.logging.impl;

import stroom.util.shared.HasId;

import static stroom.rs.logging.impl.StroomServerLoggingFilterImpl.LOGGER;

class RequestInfo {

    private final LoggingInfo loggingInfo;
    private final Object requestEntity;

    public RequestInfo(final LoggingInfo loggingInfo, final Object requestEntity) {
        this.loggingInfo = loggingInfo;
        this.requestEntity = requestEntity;
    }

    public RequestInfo(final String requestPath, final LoggingInfo loggingInfo){
        //Determine param objects from path
        this.loggingInfo = loggingInfo;
        this.requestEntity = parseParamsFromPath (requestPath);
    }

    public LoggingInfo getLoggingInfo() {
        return loggingInfo;
    }

    public Object getRequestEntity() {
        return requestEntity;
    }

    private Object parseParamsFromPath(final String path){
        if (path == null || loggingInfo.getRequestParamClass().isEmpty()){
            return null;
        }

        String[] tokens = loggingInfo.getPath().split("[{}]");
        if (tokens.length < 2){
            return null;
        }
        if (tokens.length > 3){
            LOGGER.warn("The request " + loggingInfo.getPath() + " contains multiple parameters");
        }

        int stopIndex = path.substring(tokens[0].length()).indexOf('/');
        if (stopIndex < 0) {
            stopIndex = path.length();
        }

        String paramName = tokens[1];
        String paramValue = path.substring(tokens[0].length(),stopIndex);

        if ("id".equals(paramName)){
            return new IdParm (paramValue);
        }

        return null;
    }

    private static class IdParm implements HasId {
        private final long id;

        public IdParm(String val){
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
}
