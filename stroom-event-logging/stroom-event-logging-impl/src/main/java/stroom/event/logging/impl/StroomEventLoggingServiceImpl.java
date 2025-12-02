/*
 * Copyright 2016 Crown Copyright
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

package stroom.event.logging.impl;

import stroom.activity.api.CurrentActivity;
import stroom.docref.DocRef;
import stroom.docref.HasName;
import stroom.docref.HasType;
import stroom.docref.HasUuid;
import stroom.entity.shared.ExpressionCriteria;
import stroom.event.logging.api.ObjectInfoProvider;
import stroom.event.logging.api.ObjectType;
import stroom.event.logging.api.PurposeUtil;
import stroom.event.logging.api.StroomEventLoggingService;
import stroom.event.logging.api.StroomEventLoggingUtil;
import stroom.event.logging.api.ThreadLocalLogState;
import stroom.security.api.SecurityContext;
import stroom.security.api.UserIdentity;
import stroom.util.io.ByteSize;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.BuildInfo;
import stroom.util.shared.HasAuditInfo;
import stroom.util.shared.HasId;
import stroom.util.shared.HasIntegerId;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.introspect.AnnotatedMethod;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import event.logging.BaseObject;
import event.logging.Criteria;
import event.logging.Data;
import event.logging.Device;
import event.logging.Event;
import event.logging.EventAction;
import event.logging.EventDetail;
import event.logging.EventSource;
import event.logging.EventTime;
import event.logging.OtherObject;
import event.logging.Purpose;
import event.logging.SystemDetail;
import event.logging.User;
import event.logging.impl.DefaultEventLoggingService;
import event.logging.util.DeviceUtil;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import jakarta.servlet.http.HttpServletRequest;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Singleton
public class StroomEventLoggingServiceImpl extends DefaultEventLoggingService implements StroomEventLoggingService {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(StroomEventLoggingServiceImpl.class);

    private static final String PROCESSING_USER_ID = "INTERNAL_PROCESSING_USER";
    private static final String X_FORWARDED_FOR = "X-FORWARDED-FOR";

    private static final String SYSTEM = "Stroom";
    private static final String ENVIRONMENT = "";
    private static final String GENERATOR = "StroomEventLoggingService";

    private volatile boolean obtainedDevice;
    private volatile Device storedDevice;

    private final SecurityContext securityContext;
    private final Provider<HttpServletRequest> httpServletRequestProvider;
    private final CurrentActivity currentActivity;
    private final Provider<BuildInfo> buildInfoProvider;
    private final DeviceCache deviceCache;

    private final Map<ObjectType, Provider<ObjectInfoProvider>> objectInfoProviderMap;

    private final ObjectMapper objectMapper;

    private final Provider<LoggingConfig> loggingConfigProvider;

    @Inject
    StroomEventLoggingServiceImpl(final Provider<LoggingConfig> loggingConfigProvider,
                                  final SecurityContext securityContext,
                                  final Provider<HttpServletRequest> httpServletRequestProvider,
                                  final Map<ObjectType, Provider<ObjectInfoProvider>> objectInfoProviderMap,
                                  final CurrentActivity currentActivity,
                                  final Provider<BuildInfo> buildInfoProvider,
                                  final DeviceCache deviceCache) {
        this.loggingConfigProvider = loggingConfigProvider;
        this.securityContext = securityContext;
        this.httpServletRequestProvider = httpServletRequestProvider;
        this.objectInfoProviderMap = objectInfoProviderMap;
        this.currentActivity = currentActivity;
        this.buildInfoProvider = buildInfoProvider;
        this.deviceCache = deviceCache;
        this.objectMapper = createObjectMapper();
    }

    @Override
    public void log(final Event event) {
        try {
            ThreadLocalLogState.setLogged(true);
            super.log(event);
        } catch (final Exception e) {
            // Swallow the exception so failure to log does not prevent the action being logged
            // from succeeding
            LOGGER.error("Error logging event {}: {}", event, LogUtil.exceptionMessage(e), e);
        }
    }

    @Override
    public Event createEvent(final String typeId,
                             final String description,
                             final Purpose purpose,
                             final EventAction eventAction) {
        // Get the current request.
        final HttpServletRequest request = getRequest();

        // Get device.
        final Device device = getDevice(request);

        // Get client.
        final Device client = getClient(request);

        return Event.builder()
                .withEventTime(EventTime.builder()
                        .withTimeCreated(new Date())
                        .build())
                .withEventSource(EventSource.builder()
                        .withSystem(SystemDetail.builder()
                                .withName(SYSTEM)
                                .withEnvironment(ENVIRONMENT)
                                .withVersion(buildInfoProvider.get().getBuildVersion())
                                .build())
                        .withGenerator(GENERATOR)
                        .withDevice(device)
                        .withClient(client)
                        .withUser(getUser())
                        .withRunAs(getRunAsUser())
                        .build())
                .withEventDetail(EventDetail.builder()
                        .withTypeId(typeId)
                        .withDescription(description)
                        .withPurpose(mergePurposes(PurposeUtil.create(currentActivity.getActivity()), purpose))
                        .withEventAction(eventAction)
                        .build())
                .build();
    }

    /**
     * Shallow merge of the two Purpose objects
     */
    private Purpose mergePurposes(final Purpose base, final Purpose override) {
        if (base == null || override == null) {
            if (base == null && override == null) {
                return null;
            }

            return Objects.requireNonNullElse(override, base);
        } else {
            final Purpose purpose = base.newCopyBuilder().build();
            mergeValue(override::getAuthorisations, purpose::setAuthorisations);
            mergeValue(override::getClassification, purpose::setClassification);
            mergeValue(override::getJustification, purpose::setJustification);
            mergeValue(override::getStakeholders, purpose::setStakeholders);
            mergeValue(override::getExpectedOutcome, purpose::setExpectedOutcome);
            mergeValue(override::getSubject, purpose::setSubject);

            // Combine the list of Data items from each
            purpose.getData().clear();
            purpose.getData().addAll(base.getData());
            purpose.getData().addAll(override.getData());
            return purpose;
        }
    }

    private <T> void mergeValue(final Supplier<T> getter, final Consumer<T> setter) {
        final T value = getter.get();
        if (value != null) {
            setter.accept(value);
        }
    }

    private Device getDevice(final HttpServletRequest request) {
        // Get stored device info.
        final Device storedDevice = obtainStoredDevice(request);

        // We need to copy the stored device as users may make changes to the
        // returned object that might not be thread safe.
        return copyDevice(storedDevice);
    }

    private Optional<String> getIpFromXForwardedFor(final HttpServletRequest request) {
        final String forwardedFor = request.getHeader(X_FORWARDED_FOR);
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            final String ip;
            // X_FORWARD_FOR may contain multiple IPs if it has been through multiple proxies
            // for example '1.2.3.4, 9.8.7.6'.  We will take the left most (i.e. most distant) one.
            if (forwardedFor.contains(",")) {
                ip = forwardedFor.substring(0, forwardedFor.indexOf(","));
            } else {
                ip = forwardedFor;
            }
            LOGGER.debug("{}: [{}], ip: [{}]", X_FORWARDED_FOR, forwardedFor, ip);
            return Optional.of(ip);
        } else {
            LOGGER.debug("{} is empty", X_FORWARDED_FOR);
            return Optional.empty();
        }
    }

    private Device getClient(final HttpServletRequest request) {
        final Device device;
        if (request != null) {
            // First try and use the X-FORWARDED-FOR header that provides the originating
            // IP address of the request if behind a proxy.
            final String address = getIpFromXForwardedFor(request)
                    .orElseGet(request::getRemoteAddr);

            device = copyDevice(deviceCache.getDeviceForIpAddress(address));

            LOGGER.debug(() -> LogUtil.message("Device - (host: {}, ip: {}, mac: {})",
                    device.getHostName(),
                    device.getIPAddress(),
                    device.getMACAddress()));
        } else {
            device = null;
        }
        return device;
    }

    private synchronized Device obtainStoredDevice(final HttpServletRequest request) {
        if (!obtainedDevice) {
            // First try and get the local server IP address and host name.
            InetAddress inetAddress = null;
            try {
                inetAddress = InetAddress.getLocalHost();
            } catch (final UnknownHostException e) {
                LOGGER.warn("Problem getting device from InetAddress", e);
            }

            if (inetAddress != null) {
                storedDevice = DeviceUtil.createDeviceFromInetAddress(inetAddress);
            } else {
                // Make final attempt to set with request if we have one and
                // haven't been able to set IP and host name already.
                if (request != null) {
                    storedDevice = deviceCache.getDeviceForIpAddress(request.getLocalAddr());
                }
            }
            obtainedDevice = true;
        }
        return storedDevice;
    }

    private Device copyDevice(final Device source) {
        Device dest = null;
        if (source != null) {
            dest = new Device();
            dest.setIPAddress(source.getIPAddress());
            dest.setHostName(source.getHostName());
            dest.setMACAddress(source.getMACAddress());
        }
        return dest;
    }

    private User getRunAsUser() {
        try {
            if (securityContext.isProcessingUser()) {
                // We are running as proc user so try and get the OS user,
                // though that may just be a shared account.
                // This is useful where a CLI command is being used
                final String osUser = System.getProperty("user.name");

                return User.builder()
                        .withId(osUser != null
                                ? osUser
                                : PROCESSING_USER_ID)
                        .build();
            }
        } catch (final RuntimeException e) {
            LOGGER.warn("Problem getting current user", e);
        }

        return null;
    }

    private User getUser() {
        try {
            final UserIdentity userIdentity = securityContext.getUserIdentity();
            return User.builder()
                    .withId(userIdentity.subjectId())
                    .withName(userIdentity.getDisplayName())
                    .build();
        } catch (final RuntimeException e) {
            LOGGER.warn("Problem getting current user", e);
        }

        return null;
    }

    private HttpServletRequest getRequest() {
        if (httpServletRequestProvider != null) {
            return httpServletRequestProvider.get();
        }
        return null;
    }

    @Override
    public BaseObject convert(final Supplier<?> objectSupplier, final boolean useInfoProviders) {
        if (objectSupplier != null) {
            // Run as proc user in case we are logging a user trying to access a thing they
            // don't have perms for
            final Object object = securityContext.asProcessingUserResult(objectSupplier);
            return convert(object, useInfoProviders);
        } else {
            return OtherObject.builder()
                    .withDescription(UNKNOWN_OBJECT_DESCRIPTION)
                    .build();
        }
    }

    @Override
    public BaseObject convert(final Object object, final boolean useInfoProviders) {
        if (object == null) {
            return OtherObject.builder()
                    .withDescription(UNKNOWN_OBJECT_DESCRIPTION)
                    .build();
        }

        final BaseObject baseObj;
        final ObjectInfoProvider objectInfoAppender = useInfoProviders
                ? getInfoAppender(object.getClass())
                : null;
        if (objectInfoAppender != null) {
            baseObj = objectInfoAppender.createBaseObject(object);
        } else {
            final OtherObject.Builder<Void> builder = OtherObject.builder()
                    .withType(getObjectType(object))
                    .withId(getObjectId(object))
                    .withName(getObjectName(object))
                    .withDescription(describe(object));

            builder.addData(getDataItems(object));

            baseObj = builder.build();
        }

        return baseObj;
    }

    private String getObjectType(final Object object) {
        if (object instanceof HasType) {
            return String.valueOf(((HasType) object).getType());
        }

        final ObjectInfoProvider objectInfoProvider = getInfoAppender(object.getClass());
        if (objectInfoProvider == null) {
            if (object instanceof Collection) {
                final Collection<?> collection = (Collection<?>) object;
                if (collection.isEmpty()) {
                    return "Empty collection";
                } else {
                    return "Collection containing " + (long) collection.size() + " "
                           + collection.stream().findFirst().get().getClass().getSimpleName() +
                           " and possibly other objects";
                }
            }
            return object.getClass().getSimpleName();
        }
        return objectInfoProvider.getObjectType(object);
    }

    private ObjectInfoProvider getInfoAppender(final Class<?> type) {
        if (type == null) {
            return null;
        }
        ObjectInfoProvider appender = null;

        if (String.class.equals(type)) {
            appender = new ObjectInfoProvider() {
                @Override
                public BaseObject createBaseObject(final Object object) {
                    return OtherObject.builder()
                            .withType(object.toString())
                            .build();
                }

                @Override
                public String getObjectType(final Object object) {
                    return object.toString();
                }
            };
        } else {
            // Some providers exist for superclasses and not subclass types so keep looking through the
            // class hierarchy to find a provider.
            Class<?> currentType = type;
            Provider<ObjectInfoProvider> provider = null;
            while (currentType != null && provider == null) {
                provider = objectInfoProviderMap.get(new ObjectType(currentType));
                currentType = currentType.getSuperclass();
            }

            if (provider != null) {
                appender = provider.get();
            }
        }

        if (appender == null) {
            LOGGER.debug("No ObjectInfoProvider found for " + type.getName());
        }

        return appender;
    }

    @Override
    public String describe(final Object object) {
        if (object == null) {
            return UNKNOWN_OBJECT_DESCRIPTION;
        }
        final StringBuilder desc = new StringBuilder();
        final String objectType = getObjectType(object);
        if (objectType != null) {
            desc.append(objectType);
        }

        final String objectName = getObjectName(object);
        if (objectName != null) {
            desc.append(" \"");
            desc.append(objectName);
            desc.append("\"");
        }

        final String objectId = getObjectId(object);
        if (objectId != null) {
            desc.append(" id=");
            desc.append(objectId);
        }

        return desc.toString();
    }


    private String getObjectName(final Object object) {
        if (object instanceof HasName) {
            return ((HasName) object).getName();
        }

        return null;
    }

    private String getObjectId(final Object object) {
        if (object instanceof HasUuid) {
            return ((HasUuid) object).getUuid();
        }

        if (object instanceof HasId) {
            return String.valueOf(((HasId) object).getId());
        }

        if (object instanceof HasIntegerId) {
            return String.valueOf(((HasIntegerId) object).getId());
        }

        return null;
    }

    @Override
    public Criteria convertExpressionCriteria(final String type,
                                              final ExpressionCriteria expressionCriteria) {
        return Criteria.builder()
                .withType(type)
                .withQuery(StroomEventLoggingUtil.convertExpression(expressionCriteria.getExpression()))
                .withData(Data.builder()
                        .withName("pageRequest")
                        .addData(getDataItems(expressionCriteria.getPageRequest()))
                        .build())
                .withData(Data.builder()
                        .withName("sortList")
                        .addData(getDataItems(expressionCriteria.getSortList()))
                        .build())
                .build();
    }

    /**
     * Create {@link Data} items from properties of the supplied POJO
     *
     * @param obj POJO from which to extract properties
     * @return List of {@link Data} items representing properties of the supplied POJO
     */
    @Override
    public List<Data> getDataItems(final Object obj) {
        if (obj == null || loggingConfigProvider.get().getMaxDataElementStringLength() == 0) {
            return null;
        }

        if (obj instanceof Properties) {
            return getDataItemsFromProperties((Properties) obj);
        }

        return getDataItemsFromJavaBean(obj);
    }

    private List<Data> getDataItemsFromProperties(final Properties properties) {
        return properties.entrySet().stream().map(entry ->
                convertValToData(entry.getKey().toString(), entry.getValue())).collect(Collectors.toList());
    }

    private List<Data> getDataItemsFromJavaBean(final Object bean) {

        // Construct a Jackson JavaType for the class
        final JavaType javaType = objectMapper.getTypeFactory().constructType(bean.getClass());

        // Introspect the given type
        final BeanDescription beanDescription = objectMapper.getSerializationConfig().introspect(javaType);

        // Find properties
        final List<BeanPropertyDefinition> properties = beanDescription.findProperties();

        // Get class level ignored properties
        final Set<String> ignoredProperties = new HashSet<>(objectMapper.getSerializationConfig()
                .getAnnotationIntrospector()
                .findPropertyIgnorals(beanDescription.getClassInfo())
                .getIgnored()); // Filter properties removing the class level ignored ones

        if (loggingConfigProvider.get().isOmitRecordDetailsLoggingEnabled()) {
            final Set<String> standardInterfaceProperties = ignorePropertiesFromStandardInterfaces(bean);
            ignoredProperties.addAll(standardInterfaceProperties);
        }

        final List<BeanPropertyDefinition> availableProperties = properties.stream()
                .filter(property -> !ignoredProperties.contains(property.getName()))
                .collect(Collectors.toList());

        return availableProperties.stream().map(
                beanPropDef -> {
                    final Object valObj = extractPropVal(beanPropDef, bean);
                    return convertValToData(beanPropDef.getName(), valObj);
                }).collect(Collectors.toList());
    }

    private Data convertValToData(final String name, final Object valObj) {
        final Data.Builder<?> builder = Data.builder().withName(name);

        if (valObj != null) {
            final LoggingConfig loggingConfig = loggingConfigProvider.get();
            if (valObj instanceof Collection<?>) {
                final Collection<?> collection = (Collection<?>) valObj;

                if (loggingConfig.getMaxListElements() >= 0
                    && collection.size() > loggingConfig.getMaxListElements()) {
                    final String collectionValue = collection.stream()
                            .limit(loggingConfig.getMaxListElements())
                            .map(Objects::toString)
                            .collect(Collectors.joining(", "));
                    builder.withValue(collectionValue + "...(" + collection.size() +
                                      " elements in total).");
                } else {
                    final String collectionValue = collection.stream()
                            .map(Objects::toString)
                            .collect(Collectors.joining(", "));
                    builder.withValue(collectionValue);
                }
            } else if (HasName.class.isAssignableFrom(valObj.getClass())) {
                builder.withValue(((HasName) valObj).getName());
            } else if (isLeafPropertyType(valObj.getClass())) {
                final String value;
                if (shouldRedact(name.toLowerCase(), valObj.getClass())) {
                    value = "********";
                } else {
                    if (loggingConfig.getMaxDataElementStringLength() > 0) {
                        final String stringVal = valObj.toString();
                        if (stringVal.length() > loggingConfig.getMaxDataElementStringLength()) {
                            value = stringVal.substring(0,
                                    loggingConfig.getMaxDataElementStringLength() - 1)
                                    + "...";
                        } else {
                            value = stringVal;
                        }
                    } else {
                        value = valObj.toString();
                    }
                }
                builder.withValue(value);
            } else {
                getDataItems(valObj).forEach(builder::addData);
            }
        }
        return builder.build();

    }

    private static Set<String> ignorePropertiesFromStandardInterfaces(final Object obj) {
        final Set<String> ignore = new HashSet<>();
        ignorePropertiesFromSuperType(obj, HasIntegerId.class, ignore);
        ignorePropertiesFromSuperType(obj, HasAuditInfo.class, ignore);
        ignorePropertiesFromSuperType(obj, HasId.class, ignore);
        ignorePropertiesFromSuperType(obj, HasName.class, ignore);
        ignorePropertiesFromSuperType(obj, HasUuid.class, ignore);
        ignorePropertiesFromSuperType(obj, HasType.class, ignore);

        //No interface defined yet - but version has a reasonably standard meaning and can be ignored
        ignore.add("version");
        return ignore;
    }

    private static void ignorePropertiesFromSuperType(final Object obj, final Class<?> potentialSuperType,
                                                      final Set<String> ignoreProps) {
        if (potentialSuperType.isAssignableFrom(obj.getClass())) {
            ignoreProps.addAll(Arrays.stream(potentialSuperType.getMethods())
                    .flatMap(method -> {
                        final String methodName = method.getName();
                        if (methodName.startsWith("get")) {
                            return Stream.of(methodName.substring(3, 4).toLowerCase() + methodName.substring(4));
                        } else if (methodName.startsWith("is")) {
                            return Stream.of(methodName.substring(2, 3).toLowerCase() + methodName.substring(3));
                        } else {
                            return Stream.empty();
                        }
                    }).collect(Collectors.toList()));
        }
    }

    private static boolean isLeafPropertyType(final Class<?> type) {

        final boolean isLeaf = type.equals(String.class) ||
                               type.equals(Byte.class) ||
                               type.equals(byte.class) ||
                               type.equals(Integer.class) ||
                               type.equals(int.class) ||
                               type.equals(Long.class) ||
                               type.equals(long.class) ||
                               type.equals(Short.class) ||
                               type.equals(short.class) ||
                               type.equals(Float.class) ||
                               type.equals(float.class) ||
                               type.equals(Double.class) ||
                               type.equals(double.class) ||
                               type.equals(Boolean.class) ||
                               type.equals(boolean.class) ||
                               type.equals(Character.class) ||
                               type.equals(char.class) ||

                               DocRef.class.isAssignableFrom(type) ||
                               Enum.class.isAssignableFrom(type) ||
                               Path.class.isAssignableFrom(type) ||
                               StroomDuration.class.isAssignableFrom(type) ||
                               ByteSize.class.isAssignableFrom(type) ||
                               Date.class.isAssignableFrom(type) ||
                               Instant.class.isAssignableFrom(type) ||
                               (type.isArray() &&
                          (type.getComponentType().equals(Byte.class) ||
                           type.getComponentType().equals(byte.class) ||
                           type.getComponentType().equals(Character.class) ||
                           type.getComponentType().equals(char.class)));

        LOGGER.trace("isLeafPropertyType({}), returning: {}", type, isLeaf);
        return isLeaf;
    }

    private static Object extractPropVal(final BeanPropertyDefinition beanPropDef, final Object obj) {
        final AnnotatedMethod method = beanPropDef.getGetter();

        if (method != null) {
            try {
                return method.callOn(obj);
            } catch (final Exception e) {
                LOGGER.debug("Error calling getter of " + beanPropDef.getName() + " on class " +
                             obj.getClass().getSimpleName(), e);
            }
        } else {
            LOGGER.debug("No getter for property " + beanPropDef.getName() + " of class " +
                         obj.getClass().getSimpleName());
        }

        return null;
    }

    //It is possible for a resource to be annotated to prevent it being logged at all, even when the resource
    //itself is logged, e.g. due to configuration settings
    //Assess whether this field should be redacted
    public boolean shouldRedact(final String propNameLowercase, final Class<?> type) {
        if (Boolean.class.isAssignableFrom(type) || boolean.class.isAssignableFrom(type)) {
            return false; //Don't redact boolean types
        }

        //TODO consider replacing or augmenting this hard coding
        // with a mechanism to allow properties to be selected for redaction, e.g. using annotations
        return propNameLowercase.endsWith("password") ||
               propNameLowercase.endsWith("secret") ||
               propNameLowercase.endsWith("token") ||
               propNameLowercase.endsWith("nonce") ||
               propNameLowercase.endsWith("key");
    }


    private static ObjectMapper createObjectMapper() {
        final ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(SerializationFeature.INDENT_OUTPUT, false);
        mapper.setSerializationInclusion(Include.NON_NULL);

        return mapper;
    }
}
