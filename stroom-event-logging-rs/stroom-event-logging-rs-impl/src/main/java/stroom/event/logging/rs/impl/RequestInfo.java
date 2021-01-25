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

import java.util.Optional;
import java.util.stream.Collectors;

import stroom.util.shared.HasId;
import stroom.util.shared.HasUuid;

import static stroom.event.logging.rs.impl.RestResourceAutoLoggerImpl.LOGGER;

class RequestInfo {

    private final ContainerResourceInfo containerResourceInfo;
    private final Object requestObj;


    public RequestInfo(final ContainerResourceInfo containerResourceInfo) {
        this.containerResourceInfo = containerResourceInfo;
        this.requestObj = findRequestObj();
    }

    public RequestInfo(final ContainerResourceInfo containerResourceInfo, Object requestObj) {
        this.containerResourceInfo = containerResourceInfo;
        if (requestObj == null){
            requestObj = findRequestObj();
        }
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
