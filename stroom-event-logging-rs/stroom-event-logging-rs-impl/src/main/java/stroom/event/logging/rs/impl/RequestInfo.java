/*
 * Copyright 2016-2025 Crown Copyright
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
import stroom.docref.HasUuid;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.security.api.SecurityContext;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.FetchWithIntegerId;
import stroom.util.shared.FetchWithLongId;
import stroom.util.shared.FetchWithTemplate;
import stroom.util.shared.FetchWithUuid;
import stroom.util.shared.FindWithCriteria;
import stroom.util.shared.HasId;
import stroom.util.shared.HasIntegerId;
import stroom.util.shared.ResultPage;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.ws.rs.core.MultivaluedMap;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

class RequestInfo {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(RequestInfo.class);

    private final ContainerResourceInfo containerResourceInfo;
    private final Object requestObj;
    private final Object beforeCallObj;
    private Object afterCallObj;
    private boolean afterCallObjFound = false;

    public RequestInfo(final SecurityContext securityContext, final ContainerResourceInfo containerResourceInfo) {
        this.containerResourceInfo = containerResourceInfo;
        this.requestObj = findRequestObj();
        this.beforeCallObj = securityContext.asProcessingUserResult(
                () -> findBeforeOrAfterCallObj(containerResourceInfo.getResource(), requestObj));
    }

    public static boolean objectIsLoggable(final Object obj) {
        return obj != null
               && !obj.getClass().getName().startsWith("java.")
               && !(obj instanceof Collection);
    }

    public RequestInfo(final SecurityContext securityContext,
                       final ContainerResourceInfo containerResourceInfo,
                       Object requestObj) {
        this.containerResourceInfo = containerResourceInfo;
        if (!objectIsLoggable(requestObj)) {
            requestObj = findRequestObj();
        }
        this.requestObj = requestObj;
        this.beforeCallObj = securityContext.asProcessingUserResult(
                () -> findBeforeOrAfterCallObj(containerResourceInfo.getResource(), this.requestObj));
    }

    public Object getRequestObj() {
        return requestObj;
    }

    public Object getBeforeCallObj() {
        return beforeCallObj;
    }

    public synchronized Object getAfterCallObj(final SecurityContext securityContext) {
        if (afterCallObjFound) {
            return afterCallObj;
        }
        afterCallObj = securityContext.asProcessingUserResult(
                () -> findBeforeOrAfterCallObj(containerResourceInfo.getResource(), this.requestObj));
        afterCallObjFound = true;
        return afterCallObj;
    }

    public ContainerResourceInfo getContainerResourceInfo() {
        return containerResourceInfo;
    }

    private Object findBeforeOrAfterCallObj(final Object resource, final Object template) {
        if (template == null || resource == null) {
            return null;
        }
        Object result = null;

        //Only required for update and delete operations
        if (OperationType.UPDATE == containerResourceInfo.getOperationType() ||
            OperationType.DELETE == containerResourceInfo.getOperationType()) {
            try {
                final boolean templateHasAnId = template instanceof HasId ||
                                                template instanceof HasIntegerId || template instanceof HasUuid;

                if (templateHasAnId) {
                    if (resource instanceof FetchWithIntegerId<?>) {
                        final FetchWithIntegerId<?> integerReadSupportingResource = (FetchWithIntegerId<?>) resource;
                        if (template instanceof HasIntegerId) {
                            result = integerReadSupportingResource.fetch(((HasIntegerId) template).getId());
                        } else if (template instanceof HasId) {
                            final HasId hasId = (HasId) template;
                            if (hasId.getId() > Integer.MAX_VALUE) {
                                LOGGER.error("ID out of range for int in request of type " +
                                             template.getClass().getSimpleName());
                            } else {
                                result = integerReadSupportingResource.fetch((int) ((HasId) template).getId());
                            }
                        } else {
                            LOGGER.error("Unable to extract ID from request of type " +
                                         template.getClass().getSimpleName());
                        }
                    } else if (resource instanceof FetchWithLongId<?>) {
                        final FetchWithLongId<?> integerReadSupportingResource = (FetchWithLongId<?>) resource;
                        if (template instanceof HasIntegerId) {
                            result = integerReadSupportingResource.fetch(((HasIntegerId) template).getId().longValue());
                        } else if (template instanceof HasId) {
                            final HasId hasId = (HasId) template;
                            if (hasId.getId() > Integer.MAX_VALUE) {
                                LOGGER.error("ID out of range for int in request of type " +
                                             template.getClass().getSimpleName());
                            } else {
                                result = integerReadSupportingResource.fetch(((HasId) template).getId());
                            }
                        } else {
                            LOGGER.error("Unable to extract ID from request of type " +
                                         template.getClass().getSimpleName());
                        }
                    } else if (resource instanceof FetchWithUuid<?>) {
                        final FetchWithUuid<?> docrefReadSupportingResource = (FetchWithUuid<?>) resource;
                        if (template instanceof HasUuid) {
                            final String uuid = ((HasUuid) template).getUuid();
                            result = docrefReadSupportingResource.fetch(uuid);
                        } else {
                            LOGGER.error(
                                    "Unable to extract uuid and type from request of type " +
                                    template.getClass().getSimpleName());
                        }
                    }
                } else if (resource instanceof FetchWithTemplate<?>) {
                    final FetchWithTemplate<Object> templateReadSupportingResource =
                            (FetchWithTemplate<Object>) resource;

                    final Optional<Method> fetchMethodOptional =
                            Arrays.stream(templateReadSupportingResource.getClass().getMethods())
                                    .filter(m ->
                                            m.getName().equals("fetch")
                                            && m.getParameterCount() == 1
                                            && m.getParameters()[0].getType().isAssignableFrom(template.getClass()))
                                    .findFirst();

                    if (fetchMethodOptional.isPresent()) {
                        result = templateReadSupportingResource.fetch(template);
                    } else {
                        LOGGER.error(
                                "Unable to find appropriate fetch method for type " +
                                template.getClass().getSimpleName());
                    }
                } else if (resource instanceof FindWithCriteria<?, ?>) {
                    final FindWithCriteria<Object, Object> findWithCriteriaSupportingResource =
                            (FindWithCriteria<Object, Object>) resource;

                    final Optional<Method> findMethodOptional =
                            Arrays.stream(findWithCriteriaSupportingResource.getClass().getMethods())
                                    .filter(m ->
                                            m.getName().equals("find")
                                            && m.getParameterCount() == 1
                                            && m.getParameters()[0].getType().isAssignableFrom(template.getClass()))
                                    .findFirst();
                    if (findMethodOptional.isPresent()) {
                        final ResultPage<Object> resultPage = findWithCriteriaSupportingResource.find(template);
                        if (resultPage != null) {
                            result = resultPage.getValues();
                        } else {
                            result = null;
                        }
                    } else {
                        LOGGER.error(
                                "Unable to find appropriate fetch method for type " +
                                template.getClass().getSimpleName());
                    }
                } else {
                    //Need to either implement the interface or switch to MANUALLY_LOGGED
                    LOGGER.warn("Remote resource " +
                                resource.getClass().getSimpleName() + " is not correctly configured for autologging." +
                                " Before operation object will not be available.");
                }

            } catch (final Exception ex) {
                LOGGER.info("Unable to find existing/previous version of object", ex);
            }

        }

        return result;
    }

    private Object findRequestObj() {
        final int numberOfPathParms = containerResourceInfo.getRequestContext()
                .getUriInfo()
                .getPathParameters(false)
                .keySet()
                .size();
        final int numberOfQueryParams = containerResourceInfo.getRequestContext()
                .getUriInfo()
                .getQueryParameters(false)
                .keySet()
                .size();
        final int numberOfPathAndQueryParms = numberOfPathParms + numberOfQueryParams;

        if (numberOfPathAndQueryParms == 0) {
            return null;
        }

        if (numberOfPathAndQueryParms > 1) {
            final WithParameters obj = new WithParameters(
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
            final String paramName = paramMap.keySet().stream().findFirst().get();
            final String paramValue = paramMap.get(paramName).stream().collect(Collectors.joining(", "));
            if ("id".equals(paramName)) {
                return new ObjectId(paramValue);
            } else if ("uuid".equals(paramName)) {
                return new ObjectUuid(paramValue);
            } else {
                final WithParameters obj = new WithParameters(
                        containerResourceInfo.getRequestContext().getUriInfo().getPathParameters(false));
                obj.addParams(containerResourceInfo.getRequestContext().getUriInfo().getQueryParameters(false));
                return obj;
            }
        }

    }


    // --------------------------------------------------------------------------------


    public static class ObjectId implements HasId {

        private final long id;

        public ObjectId(final String val) {
            long id = 0;
            try {
                id = Long.parseLong(val);
            } catch (final NumberFormatException ex) {
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


    // --------------------------------------------------------------------------------


    public static class ObjectUuid implements HasUuid {

        private final String uuid;

        public ObjectUuid(final String uuid) {
            this.uuid = uuid;
        }

        @Override
        public String getUuid() {
            return uuid;
        }
    }


    // --------------------------------------------------------------------------------


    public static class WithParameters extends Properties implements HasName {

//        private Properties allParameters = new Properties();

        public WithParameters(final MultivaluedMap<String, String> origParams) {
            final Set<Entry<String, String>> params = createParms(origParams);

            params.stream().forEach((entry) -> this.put(entry.getKey(), entry.getValue()));
        }

        private Set<Entry<String, String>> createParms(final MultivaluedMap<String, String> origParams) {
            return origParams.keySet().stream().map(k -> {
                return new Entry<String, String>() {
                    @Override
                    public String getKey() {
                        return k;
                    }

                    @Override
                    public String getValue() {
                        return origParams.get(k).stream().collect(Collectors.joining(", "));
                    }

                    @Override
                    public String setValue(final String value) {
                        return null;
                    }
                };
            }).collect(Collectors.toSet());
        }

        public void addParams(final MultivaluedMap<String, String> origParams) {
            if (origParams == null || origParams.size() == 0) {
                return;
            }
            final Set<Entry<String, String>> parms = createParms(origParams);

            parms.stream().forEach((entry) -> this.put(entry.getKey(), entry.getValue()));
        }

        @Override
        @JsonIgnore
        public String getName() {
            return this.entrySet().stream()
                    .map(e ->
                            e.getKey() + " = " + e.getValue())
                    .collect(Collectors.joining(", "));
        }
    }
}
