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

import stroom.docref.HasName;
import stroom.docref.HasType;
import stroom.docref.HasUuid;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.security.api.SecurityContext;
import stroom.util.shared.FetchWithIntegerId;
import stroom.util.shared.FetchWithLongId;
import stroom.util.shared.FetchWithTemplate;
import stroom.util.shared.FetchWithUuid;
import stroom.util.shared.HasId;
import stroom.util.shared.HasIntegerId;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.ws.rs.core.MultivaluedMap;

import static stroom.event.logging.rs.impl.RestResourceAutoLoggerImpl.LOGGER;

class RequestInfo {

    private final ContainerResourceInfo containerResourceInfo;
    private final Object requestObj;
    private final Object beforeCallObj;

    public RequestInfo(final SecurityContext securityContext, final ContainerResourceInfo containerResourceInfo) {
        this.containerResourceInfo = containerResourceInfo;
        this.requestObj = findRequestObj();
        this.beforeCallObj = securityContext.asProcessingUserResult(
                () -> findBeforeCallObj(containerResourceInfo.getResource(), requestObj));
    }

    public RequestInfo(final SecurityContext securityContext,
                       final ContainerResourceInfo containerResourceInfo,
                       Object requestObj) {
        this.containerResourceInfo = containerResourceInfo;
        if (requestObj == null) {
            requestObj = findRequestObj();
        }
        this.requestObj = requestObj;
        this.beforeCallObj = securityContext.asProcessingUserResult(
                () -> findBeforeCallObj(containerResourceInfo.getResource(), this.requestObj));
    }

    public Object getRequestObj() {
        return requestObj;
    }

    public Object getBeforeCallObj() {
        return beforeCallObj;
    }

    public ContainerResourceInfo getContainerResourceInfo() {
        return containerResourceInfo;
    }

    private Object findBeforeCallObj(Object resource, Object template) {
        if (template == null || resource == null) {
            return null;
        }
        Object result = null;

        //Only required for update and delete operations
        if (OperationType.UPDATE.equals(containerResourceInfo.getOperationType()) ||
                OperationType.DELETE.equals(containerResourceInfo.getOperationType())) {
            try {

                if (resource instanceof FetchWithIntegerId<?>) {
                    FetchWithIntegerId<?> integerReadSupportingResource = (FetchWithIntegerId<?>) resource;
                    if (template instanceof HasIntegerId) {
                        result = integerReadSupportingResource.fetch(((HasIntegerId) template).getId());
                    } else if (template instanceof HasId) {
                        HasId hasId = (HasId) template;
                        if (hasId.getId() > Integer.MAX_VALUE) {
                            RestResourceAutoLoggerImpl.LOGGER.error("ID out of range for int in request of type " +
                                    template.getClass().getSimpleName());
                        } else {
                            result = integerReadSupportingResource.fetch((int) ((HasId) template).getId());
                        }
                    } else {
                        RestResourceAutoLoggerImpl.LOGGER.error("Unable to extract ID from request of type " +
                                template.getClass().getSimpleName());
                    }
                } else if (resource instanceof FetchWithLongId<?>) {
                    FetchWithLongId<?> integerReadSupportingResource = (FetchWithLongId<?>) resource;
                    if (template instanceof HasIntegerId) {
                        result = integerReadSupportingResource.fetch(((HasIntegerId) template).getId().longValue());
                    } else if (template instanceof HasId) {
                        HasId hasId = (HasId) template;
                        if (hasId.getId() > Integer.MAX_VALUE) {
                            RestResourceAutoLoggerImpl.LOGGER.error("ID out of range for int in request of type " +
                                    template.getClass().getSimpleName());
                        } else {
                            result = integerReadSupportingResource.fetch(((HasId) template).getId());
                        }
                    } else {
                        RestResourceAutoLoggerImpl.LOGGER.error("Unable to extract ID from request of type " +
                                template.getClass().getSimpleName());
                    }
                } else if (resource instanceof FetchWithUuid<?>) {
                    FetchWithUuid<?> docrefReadSupportingResource = (FetchWithUuid<?>) resource;
                    if (template instanceof HasUuid && template instanceof HasType) {
                        String uuid = ((HasUuid) template).getUuid();
                        result = docrefReadSupportingResource.fetch(uuid);
                    } else {
                        RestResourceAutoLoggerImpl.LOGGER.error(
                                "Unable to extract uuid and type from request of type " +
                                        template.getClass().getSimpleName());
                    }
                } else if (resource instanceof FetchWithTemplate<?>) {
                    FetchWithTemplate<Object> templateReadSupportingResource = (FetchWithTemplate<Object>) resource;

                    Optional<Method> fetchMethodOptional =
                            Arrays.stream(templateReadSupportingResource.getClass().getMethods())
                            .filter(m -> m.getName().equals("fetch")
                                    && m.getParameterCount() == 1
                                    && m.getParameters()[0].getType().isAssignableFrom(template.getClass()))
                            .findFirst();

                    if (fetchMethodOptional.isPresent()) {
                        result = templateReadSupportingResource.fetch(template);
                    } else {
                        RestResourceAutoLoggerImpl.LOGGER.error(
                                "Unable to find appropriate fetch method for type " +
                                        template.getClass().getSimpleName());
                    }
                } else {
                    //Need to either implement the interface or switch to MANUALLY_LOGGED
                    RestResourceAutoLoggerImpl.LOGGER.warn("Remote resource " +
                            resource.getClass().getSimpleName() + " is not correctly configured for autologging." +
                            " Before operation object will not be available.");
                }

            } catch (Exception ex) {
                RestResourceAutoLoggerImpl.LOGGER.info("Unable to find existing/previous version of object", ex);
            }

        }

        return result;
    }

    private Object findRequestObj() {
        int numberOfPathParms = containerResourceInfo.getRequestContext()
                .getUriInfo()
                .getPathParameters(false)
                .keySet()
                .size();
        int numberOfQueryParams = containerResourceInfo.getRequestContext()
                .getUriInfo()
                .getQueryParameters(false)
                .keySet()
                .size();
        int numberOfPathAndQueryParms = numberOfPathParms + numberOfQueryParams;

        if (numberOfPathAndQueryParms == 0) {
            return null;
        }

        if (numberOfPathAndQueryParms > 1) {
            WithParameters obj = new WithParameters(
                    containerResourceInfo.getRequestContext().getUriInfo().getPathParameters(false));
            obj.addParams(containerResourceInfo.getRequestContext().getUriInfo().getQueryParameters(false));
            return obj;
        } else {
            final MultivaluedMap<String, String> paramMap;
            if (numberOfPathParms == 1) {
                paramMap = containerResourceInfo.getRequestContext().getUriInfo().getPathParameters(false);
            } else {
                paramMap = containerResourceInfo.getRequestContext().getUriInfo().getQueryParameters(false);
            }
            String paramName = paramMap.keySet().stream().findFirst().get();
            String paramValue = paramMap.get(paramName).stream().collect(Collectors.joining(", "));
            if ("id".equals(paramName)) {
                return new ObjectId(paramValue);
            } else if ("uuid".equals(paramName)) {
                return new ObjectUuid(paramValue);
            } else {
                WithParameters obj = new WithParameters(
                        containerResourceInfo.getRequestContext().getUriInfo().getPathParameters(false));
                obj.addParams(containerResourceInfo.getRequestContext().getUriInfo().getQueryParameters(false));
                return obj;
            }
        }

    }

    public static class ObjectId implements HasId {

        private final long id;

        public ObjectId(String val) {
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

    public static class ObjectUuid implements HasUuid {

        private final String uuid;

        public ObjectUuid(String uuid) {
            this.uuid = uuid;
        }

        @Override
        public String getUuid() {
            return uuid;
        }
    }

    public static class WithParameters implements HasName {

        private String name;

        public WithParameters(MultivaluedMap<String, String> origParms) {
            Set<Entry<String, String>> parms = createParms(origParms);

            name = parms.stream()
                    .map(e ->
                            e.getKey() + " = " + e.getValue())
                    .collect(Collectors.joining(", "));
        }

        private Set<Entry<String, String>> createParms(MultivaluedMap<String, String> origParms) {
            return origParms.keySet().stream().map(k -> {
                return new Entry<String, String>() {
                    @Override
                    public String getKey() {
                        return k;
                    }

                    @Override
                    public String getValue() {
                        return origParms.get(k).stream().collect(Collectors.joining(", "));
                    }

                    @Override
                    public String setValue(final String value) {
                        return null;
                    }
                };
            }).collect(Collectors.toSet());
        }

        public void addParams(MultivaluedMap<String, String> origParms) {
            name = name.length() > 0
                    ? name + ", "
                    : "" +
                            createParms(origParms).stream()
                                    .map(e ->
                                            e.getKey() + " = " + e.getValue())
                                    .collect(Collectors.joining(", "));
        }

        @Override
        public String getName() {
            return name;
        }
    }
}
