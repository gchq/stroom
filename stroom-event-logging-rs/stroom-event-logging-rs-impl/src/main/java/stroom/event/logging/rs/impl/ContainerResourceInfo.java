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

import stroom.event.logging.api.EventActionDecorator;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.security.api.SecurityContext;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.container.ResourceInfo;

import java.lang.reflect.Method;
import java.util.Optional;

public class ContainerResourceInfo {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ContainerResourceInfo.class);

    private final ResourceContext resourceContext;
    private final SecurityContext securityContext;
    private final ContainerRequestContext requestContext;
    private final ResourceInfo resourceInfo;
    private final OperationType operationType;
    private final Class<? extends EventActionDecorator> eventActionDecoratorClass;
    private final boolean autologgerAnnotationPresent;

    public ContainerResourceInfo(final ResourceContext resourceContext,
                                 final SecurityContext securityContext,
                                 final ResourceInfo resourceInfo,
                                 final ContainerRequestContext requestContext,
                                 final boolean logAllRestCalls) {
        this.resourceContext = resourceContext;
        this.securityContext = securityContext;
        this.resourceInfo = resourceInfo;
        this.requestContext = requestContext;
        final Optional<OperationType> operationType =
                getOperationTypeFromAnnotations(getMethod(), getResourceClass());
        this.autologgerAnnotationPresent = operationType.isPresent();
        if (logAllRestCalls && operationType.isPresent() && OperationType.UNLOGGED.equals(operationType.get())) {
            this.operationType = OperationType.UNKNOWN;
        } else {
            this.operationType = findOperationType(operationType, getMethod().getName(), requestContext.getMethod());
        }
        this.eventActionDecoratorClass = findEventActionDecorator(getMethod(), getResourceClass());
    }

    public Object getResource() {
        if (resourceContext == null) {
            return null; //Possible in some unit tests
        }
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

    public Method getMethod() {
        return resourceInfo.getResourceMethod();
    }

    public OperationType getOperationType() {
        return operationType;
    }

    public boolean isAutologgerAnnotationPresent() {
        return autologgerAnnotationPresent;
    }

    public Class<? extends EventActionDecorator> getEventActionDecoratorClass() {
        return eventActionDecoratorClass;
    }

    public String getTypeId() {
        //If method annotation provided use that on its own
        if ((getMethod().getAnnotation(AutoLogged.class) != null) &&
            (!getMethod().getAnnotation(AutoLogged.class).typeId().equals(AutoLogged.ALLOCATE_AUTOMATICALLY))) {
            return getMethod().getAnnotation(AutoLogged.class).typeId();
        }
        String resourcePrefix = getResourceClass().getSimpleName();
        if ((getResourceClass().getAnnotation(AutoLogged.class) != null) &&
            (!getResourceClass().getAnnotation(AutoLogged.class).typeId()
                    .equals(AutoLogged.ALLOCATE_AUTOMATICALLY))) {
            resourcePrefix = getResourceClass().getAnnotation(AutoLogged.class).typeId();
        }

        return resourcePrefix + "." + getMethod().getName();
    }

    public String getVerbFromAnnotations() {
        if ((getMethod().getAnnotation(AutoLogged.class) != null) &&
            (!getMethod().getAnnotation(AutoLogged.class).verb().equals(AutoLogged.ALLOCATE_AUTOMATICALLY))) {
            return getMethod().getAnnotation(AutoLogged.class).verb();
        }
        return null;
    }

    private static Class<? extends EventActionDecorator> findEventActionDecorator(final Method method,
                                                                                  final Class<?> resourceClass) {
        final Class<? extends EventActionDecorator> decoratorClass;
        if (method.getAnnotation(AutoLogged.class) != null) {
            decoratorClass = method.getAnnotation(AutoLogged.class).decorator();
        } else if (resourceClass.getAnnotation(AutoLogged.class) != null) {
            decoratorClass = resourceClass.getAnnotation(AutoLogged.class).decorator();
        } else {
            decoratorClass = EventActionDecorator.class;
        }

        if (decoratorClass.equals(EventActionDecorator.class)) {
            return null; // Default is no implementation
        }

        return decoratorClass;
    }

    private static Optional<OperationType> getOperationTypeFromAnnotations(final Method method,
                                                                           final Class<?> resourceClass) {
        if (method.getAnnotation(AutoLogged.class) != null) {
            return Optional.of(method.getAnnotation(AutoLogged.class).value());
        } else if (resourceClass.getAnnotation(AutoLogged.class) != null) {
            return Optional.of(resourceClass.getAnnotation(AutoLogged.class).value());
        }
        return Optional.empty();
    }

    public Optional<OperationType> getOperationTypeFromAnnotations() {
        return getOperationTypeFromAnnotations(getMethod(), getResourceClass());
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
            return camelCaseWord;
        }
    }

    private OperationType findOperationType(final Optional<OperationType> type,
                                            final String methodName,
                                            final String httpMethod) {

        if (type.isPresent() && !OperationType.ALLOCATE_AUTOMATICALLY.equals(type.get())) {
            return type.get();
        } else if (HttpMethod.DELETE.equals(httpMethod)) {
            return OperationType.DELETE;
        } else {
            final String firstCamelCasePart = getFirstCamelCasePart(methodName);

            LOGGER.debug(() -> LogUtil.message("methodName: {}, firstCamelCasePart: {}",
                    methodName, firstCamelCasePart));

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

    public boolean shouldLog() {
        final OperationType op = getOperationType();
//        if (OperationType.MANUALLY_LOGGED.equals(op)) {
//            LOGGER.debug("{}, not logging request", OperationType.MANUALLY_LOGGED);
//            return true;
////        } else if (config.isLogEveryRestCallEnabled()) {
////            LOGGER.debug("logEveryRestCallEnabled set to true, logging request");
////            return true;
//        } else
//
        if (op == OperationType.UNLOGGED) {
            LOGGER.debug("{}, not logging request", OperationType.UNLOGGED);
            return false;
        } else if (securityContext.isProcessingUser()) {
            LOGGER.debug("Processing user, not logging request");
            return false;
        }

//        else if (isAutologgerAnnotationPresent()) {
//            LOGGER.debug("Auto logger annotation present, logging request");
//            return true;
//        } else {
//            LOGGER.debug("No matches, not logging request");
//            return false;
//        }

        return true;
    }

    @Override
    public String toString() {
        return "ContainerResourceInfo{" +
               "uri=" + requestContext.getUriInfo().getRequestUri() +
               ", user=" + securityContext.getUserIdentityForAudit() +
               ", requestContext=" + requestContext +
               ", operationType=" + operationType +
               ", eventActionDecoratorClass=" + eventActionDecoratorClass +
               ", autologgerAnnotationPresent=" + autologgerAnnotationPresent +
               '}';
    }
}
