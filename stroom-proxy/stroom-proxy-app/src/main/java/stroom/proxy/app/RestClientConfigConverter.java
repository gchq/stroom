package stroom.proxy.app;

import stroom.util.config.PropertyUtil;
import stroom.util.config.PropertyUtil.Prop;
import stroom.util.shared.AbstractConfig;
import stroom.util.time.StroomDuration;

import io.dropwizard.client.JerseyClientConfiguration;
import io.dropwizard.client.ssl.TlsConfiguration;
import io.dropwizard.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import javax.inject.Singleton;

// Singleton so we only have to discover all the getters/setters once
@Singleton
public class RestClientConfigConverter {

    private static final Logger LOGGER = LoggerFactory.getLogger(RestClientConfigConverter.class);

    private static final Map<Class<?>, Class<?>> CLASS_MAP = Map.of(
            RestClientConfig.class, JerseyClientConfiguration.class,
            HttpClientTlsConfig.class, TlsConfiguration.class);

    private final Map<Class<?>, Map<Method, Method>> methodMap = new HashMap<>();
    private final Map<Class<?>, Function<Object, Object>> converterMap = new HashMap<>();

    public RestClientConfigConverter() {
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
                if (isConfigClass(valueClass)) {
                    // recurse
                    mapMethods(valueClass);
                }

                if (destProps.containsKey(name)) {
                    methodMap.computeIfAbsent(sourceClass, k -> new HashMap<>())
                            .put(sourceProps.get(name).getGetter(),
                                    destProps.get(name).getSetter());
                }
            });

            converterMap.put(sourceClass, this::convertObject);
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
                                } else {
                                    destSetter.invoke(destObj, sourcePropValue);
                                }
                            }
                        } catch (IllegalAccessException
                                | InvocationTargetException e) {
                            throw new RuntimeException(e);
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
}
