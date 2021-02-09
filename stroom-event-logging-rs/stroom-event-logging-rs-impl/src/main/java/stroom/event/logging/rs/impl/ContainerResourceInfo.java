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

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.AutoLogged;
import stroom.util.shared.AutoLogged.OperationType;

import javax.ws.rs.HttpMethod;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ResourceContext;
import javax.ws.rs.container.ResourceInfo;
import java.lang.reflect.Method;
import java.util.Optional;

public class ContainerResourceInfo {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ContainerResourceInfo.class);

    private final ResourceContext resourceContext;
    private final ContainerRequestContext requestContext;
    private final ResourceInfo resourceInfo;
    private final OperationType operationType;

    public ContainerResourceInfo(final ResourceContext resourceContext,
                                 final ResourceInfo resourceInfo,
                                 final ContainerRequestContext requestContext) {
        this.resourceContext = resourceContext;
        this.resourceInfo = resourceInfo;
        this.requestContext = requestContext;
        this.operationType = findOperationType(
                getMethod(),
                getResourceClass(),
                requestContext.getMethod());
    }

    public Object getResource() {
        return resourceContext.getResource(getResourceClass());
    }

    public ContainerRequestContext getRequestContext() {
        return requestContext;
    }

    public ResourceInfo getResourceInfo() {
        return resourceInfo;
    }

    public Class<?> getResourceClass() {
        return resourceInfo.getResourceClass();
    }

    public Method getMethod(){
        return resourceInfo.getResourceMethod();
    }

    public OperationType getOperationType(){
        return operationType;
    }

    public String getTypeId(){
        //If method annotation provided use that on its own
        if ((getMethod().getAnnotation(AutoLogged.class) != null) &&
                (!getMethod().getAnnotation(AutoLogged.class).typeId().equals(AutoLogged.ALLOCATE_AUTOMATICALLY))){
            return getMethod().getAnnotation(AutoLogged.class).typeId();
        }
        String resourcePrefix = getResourceClass().getSimpleName();
        if ((getResourceClass().getAnnotation(AutoLogged.class) != null) &&
                (!getResourceClass().getAnnotation(AutoLogged.class).typeId().equals(AutoLogged.ALLOCATE_AUTOMATICALLY))){
            resourcePrefix = getResourceClass().getAnnotation(AutoLogged.class).typeId();
        }

        return resourcePrefix + "." + getMethod().getName();
    }

    public String getVerbFromAnnotations(){
        if ((getMethod().getAnnotation(AutoLogged.class) != null) &&
                (!getMethod().getAnnotation(AutoLogged.class).verb().equals(AutoLogged.ALLOCATE_AUTOMATICALLY))){
            return getMethod().getAnnotation(AutoLogged.class).verb();
        }
        return null;
    }

    private static Optional<OperationType> getOperationTypeFromAnnotations(final Method method,
                                                                           final Class<?> resourceClass){
        if (method.getAnnotation(AutoLogged.class) != null){
            return Optional.of(method.getAnnotation(AutoLogged.class).value());
        } else if (resourceClass.getAnnotation(AutoLogged.class) != null){
            return Optional.of(resourceClass.getAnnotation(AutoLogged.class).value());
        }
        return Optional.empty();
    }

    public Optional<OperationType> getOperationTypeFromAnnotations(){
        return getOperationTypeFromAnnotations(getMethod(), getResourceClass());
    }

    public boolean shouldLog (boolean logByDefault){
        return getOperationTypeFromAnnotations()
                .filter(type ->
                        !(OperationType.UNLOGGED.equals(type) || OperationType.MANUALLY_LOGGED.equals(type)))
                .isPresent() || logByDefault;
    }

    /**
     * "lowerCamel" => "lower"
     * "UpperCamel" => ""
     */
    private String getFirstCamelCasePart(final String camelCaseWord) {
        final int len = camelCaseWord.length();
        int firstUpperIdx = -1;
        for (int i = 0; i < len; i++) {
            if (Character.isUpperCase(camelCaseWord.charAt(i))) {
                firstUpperIdx = i;
                break;
            }
        }
        if (firstUpperIdx != -1) {
            return camelCaseWord.substring(0, firstUpperIdx);
        } else {
            return "";
        }
    }

    private OperationType findOperationType(final Method method,
                                            final Class<?> resourceClass,
                                            final String httpMethod) {

        final Optional<OperationType> type = getOperationTypeFromAnnotations(
                method, resourceClass);

        if (type.isPresent() && !OperationType.ALLOCATE_AUTOMATICALLY.equals(type.get())) {
            return type.get();
        } else if (HttpMethod.DELETE.equals(httpMethod)) {
            return OperationType.DELETE;
        } else {
            final String firstCamelCasePart = getFirstCamelCasePart(method.getName());

            LOGGER.debug(() -> LogUtil.message("methodName: {}, firstCamelCasePart: {}",
                    method.getName(), firstCamelCasePart));

            if ("get".equals(firstCamelCasePart)
                    || "fetch".equals(firstCamelCasePart)
                    || "read".equals(firstCamelCasePart)
                    || "view".equals(firstCamelCasePart)) {
                return OperationType.VIEW;
            } else if ("create".equals(firstCamelCasePart)) {
                return OperationType.CREATE;
            } else if ("delete".equals(firstCamelCasePart)) {
                return OperationType.DELETE;
            } else if ("update".equals(firstCamelCasePart)
                    || "save".equals(firstCamelCasePart)
                    || "set".equals(firstCamelCasePart)) {
                return OperationType.UPDATE;
            } else if ("find".equals(firstCamelCasePart)
                    || "search".equals(firstCamelCasePart)
                    || "list".equals(firstCamelCasePart)) {
                return OperationType.SEARCH;
            } else if ("import".equals(firstCamelCasePart)
                    || "upload".equals(firstCamelCasePart)) {
                return OperationType.IMPORT;
            } else if ("export".equals(firstCamelCasePart)
                    || "download".equals(firstCamelCasePart)) {
                return OperationType.EXPORT;
            } else if ("copy".equals(firstCamelCasePart)) {
                return OperationType.COPY;
            } else {
                return OperationType.UNKNOWN;
            }
        }
    }
}
