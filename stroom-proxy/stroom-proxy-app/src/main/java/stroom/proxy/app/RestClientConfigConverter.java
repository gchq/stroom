package stroom.proxy.app;

import stroom.util.config.PropertyUtil;
import stroom.util.config.PropertyUtil.Prop;
import stroom.util.io.PathCreator;
import stroom.util.shared.AbstractConfig;
import stroom.util.time.StroomDuration;

import io.dropwizard.client.JerseyClientConfiguration;
import io.dropwizard.client.ssl.TlsConfiguration;
import io.dropwizard.util.Duration;
import io.dropwizard.validation.ValidationMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

// Singleton so we only have to discover all the getters/setters once
@Singleton
public class RestClientConfigConverter {

    private static final Logger LOGGER = LoggerFactory.getLogger(RestClientConfigConverter.class);

    private static final Map<Class<?>, Class<?>> CLASS_MAP = Map.of(
            RestClientConfig.class, JerseyClientConfiguration.class,
            HttpClientTlsConfig.class, TlsConfiguration.class);

    private final Map<Class<?>, Map<Method, Method>> methodMap = new HashMap<>();
    private final PathCreator pathCreator;

    @Inject
    public RestClientConfigConverter(final PathCreator pathCreator) {
        this.pathCreator = pathCreator;

        mapMethods(RestClientConfig.class);
        LOGGER.debug("Completed initialisation of RestClientConfigConverter");
    }

    private void mapMethods(final Class<?> sourceClass) {
        LOGGER.debug("Recursing into {}", sourceClass.getSimpleName());
        final Class<?> destClass = CLASS_MAP.get(sourceClass);
        try {
            final Object vanillaSourceObj = sourceClass.getConstructor().newInstance();
            final Object vanillaDestObj = destClass.getConstructor().newInstance();

            final Map<String, Prop> sourceProps = PropertyUtil.getProperties(vanillaSourceObj);
            final Map<String, Prop> destProps = PropertyUtil.getProperties(vanillaDestObj);

            sourceProps.forEach((name, sourceProp) -> {
                final Class<?> valueClass = sourceProp.getValueClass();

                if (!sourceProp.hasAnnotation(ValidationMethod.class)) {
                    if (isConfigClass(valueClass)) {
                        // recurse
                        mapMethods(valueClass);
                    }

                    if (destProps.containsKey(name)) {
                        methodMap.computeIfAbsent(sourceClass, k -> new HashMap<>())
                                .put(sourceProps.get(name).getGetter(),
                                        destProps.get(name).getSetter());
                    }
                }
            });
        } catch (InstantiationException
                | IllegalAccessException
                | InvocationTargetException
                | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    private Object convertObject(final Object sourceObj) {
        try {
            final Class<?> sourceClass = sourceObj.getClass();
            final Class<?> destClass = CLASS_MAP.get(sourceClass);
            final Object destObj = destClass
                    .getConstructor()
                    .newInstance();
            LOGGER.debug("converting {} to {}",
                    sourceClass.getSimpleName(),
                    destClass.getSimpleName());

            methodMap.get(sourceClass)
                    .forEach((sourceGetter, destSetter) -> {
                        try {
                            final Object sourcePropValue = sourceGetter.invoke(sourceObj);
                            LOGGER.debug("method: {}, sourcePropValue: {}",
                                    sourceGetter.getName(),
                                    sourcePropValue);
                            if (sourcePropValue == null) {
                                destSetter.invoke(destObj, sourcePropValue);
                            } else if (isConfigClass(sourcePropValue.getClass())) {
                                // branch so recurse
                                final Object destPropValue = convertObject(sourcePropValue);
                                destSetter.invoke(destObj, destPropValue);
                            } else {
                                if (sourcePropValue.getClass().equals(StroomDuration.class)) {
                                    destSetter.invoke(destObj, convertDuration((StroomDuration) sourcePropValue));
                                } else if (destSetter.getParameterTypes()[0].equals(File.class)) {
                                    destSetter.invoke(destObj, convertFile((String) sourcePropValue));
                                } else if (destSetter.getParameterTypes()[0].equals(Optional.class)) {
                                    // We changed one of their optionals to just be a string so
                                    // need to map string=>optional
                                    destSetter.invoke(destObj, convertToOptional(sourcePropValue));
                                } else {
                                    destSetter.invoke(destObj, sourcePropValue);
                                }
                            }
                        } catch (Exception e) {
                            throw new RuntimeException("Error converting getter " +
                                    sourceClass.getSimpleName() + "." + sourceGetter.getName() +
                                    " to " + destSetter, e);
                        }
                    });
            return destObj;
        } catch (IllegalAccessException
                | InvocationTargetException
                | NoSuchMethodException
                | InstantiationException e) {
            throw new RuntimeException(e);
        }
    }

    public JerseyClientConfiguration convert(final RestClientConfig restClientConfig) {
        return (JerseyClientConfiguration) convertObject(restClientConfig);
    }

    private boolean isConfigClass(final Class<?> clazz) {
        return AbstractConfig.class.isAssignableFrom(clazz);
    }

    private Duration convertDuration(final StroomDuration stroomDuration) {
        try {
            // May fail due to overflow
            return Duration.nanoseconds(stroomDuration.toNanos());
        } catch (ArithmeticException e) {
            // Fall back to conversion using millis with possible loss of precision
            return Duration.milliseconds(stroomDuration.toMillis());
        }
    }

    private File convertFile(final String path) {
        return new File(pathCreator.makeAbsolute(
                pathCreator.replaceSystemProperties(path)));
    }

    private <T> Optional<T> convertToOptional(final T value) {
        return Optional.ofNullable(value);
    }
}
