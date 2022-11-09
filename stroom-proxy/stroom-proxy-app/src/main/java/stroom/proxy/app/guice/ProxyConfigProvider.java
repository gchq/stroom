package stroom.proxy.app.guice;

import stroom.proxy.app.ProxyConfig;
import stroom.proxy.app.ProxyConfigHolder;
import stroom.util.NullSafe;
import stroom.util.config.PropertyUtil;
import stroom.util.config.PropertyUtil.Prop;
import stroom.util.exception.ThrowingSupplier;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.NotInjectableConfig;
import stroom.util.shared.PropertyPath;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This is sort of the equivalent to stroom's ConfigMapper class.
 * It holds the de-serialised config objects in a map that is then used to
 * provide them for guice injection.
 */
@Singleton
public class ProxyConfigProvider {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ProxyConfigProvider.class);

    // The map to be used by guice to provide instances. This map will be replaced in its entirety
    // when config has changed, on the basis that config is not changed very often
    private volatile Map<Class<? extends AbstractConfig>, AbstractConfig> configInstanceMap = null;

    @SuppressWarnings("unused")
    @Inject
    ProxyConfigProvider(final ProxyConfigHolder configHolder) {
        this(configHolder.getProxyConfig());
    }

    ProxyConfigProvider(final ProxyConfig proxyConfig) {
        // Don't need to know about changes on first run as everything will be null to start with
        rebuildConfigInstances(proxyConfig, false);
    }

    <T extends AbstractConfig> T getConfigObject(final Class<T> clazz) {
        final AbstractConfig config = configInstanceMap.get(clazz);
        Objects.requireNonNull(config, "No config instance found for class " + clazz.getName());
        try {
            return clazz.cast(config);
        } catch (Exception e) {
            throw new RuntimeException(LogUtil.message(
                    "Error casting config object to {}, found {}",
                    clazz.getName(),
                    config.getClass().getName()), e);
        }
    }

    Set<Class<? extends AbstractConfig>> getInjectableClasses() {
        return Collections.unmodifiableSet(configInstanceMap.keySet());
    }

    private void addConfigInstances(
            final AbstractConfig config,
            final Map<Class<? extends AbstractConfig>, AbstractConfig> configInstanceMap,
            final ObjectMapper objectMapper,
            final PropertyPath propertyPath,
            final AtomicInteger changeCounter,
            final boolean logChanges) {

        try {
            final Class<? extends AbstractConfig> clazz = config.getClass();

            final Map<String, Prop> propMap = PropertyUtil.getProperties(objectMapper, config);
            propMap.forEach((k, prop) -> {
                final String childPropName = prop.getName();
                final PropertyPath childPropPath = propertyPath.merge(childPropName);
                final Class<?> valueType = prop.getValueClass();
                if (AbstractConfig.class.isAssignableFrom(valueType)) {

                    final AbstractConfig childValue = (AbstractConfig) prop.getValueFromConfigObject();

                    if (childValue != null) {
                        // recurse
                        addConfigInstances(
                                childValue,
                                configInstanceMap,
                                objectMapper,
                                childPropPath,
                                changeCounter,
                                logChanges);
                    } else {
                        if (!prop.getValueClass().isAnnotationPresent(NotInjectableConfig.class)) {
                            // We should not have null config objects that are meant to be injectable
                            throw new RuntimeException(LogUtil.message("Prop {} is null but is injectable config"));
                        }
                    }
                }
            });

            // Non-injectable config is only accessible by an injectable ancestor so no need to add
            // it to the providers map
            if (!clazz.isAnnotationPresent(NotInjectableConfig.class)) {
                // Add the injectable config object to the instance map so the providers can get it
                configInstanceMap.put(config.getClass(), config);
            }

            final AbstractConfig existingConfig = NullSafe.get(this.configInstanceMap, map -> map.get(clazz));
//            if (existingConfig != null) {
            // This is an update so see what has changed
            propMap.forEach((k, prop) -> {
                final Class<?> valueType = prop.getValueClass();
                // Only log changes for values that are not config object
                // Recursion into config objects will happen above
                if (!AbstractConfig.class.isAssignableFrom(valueType)) {
                    final Object newValue = prop.getValueFromConfigObject();
                    final Object existingValue;
                    if (existingConfig != null) {
                        existingValue = prop.getValueFromConfigObject(existingConfig);
                    } else {
                        if (AbstractConfig.class.isAssignableFrom(valueType)) {
                            LOGGER.info("valueType: {}", valueType);
                            // Create a vanilla config obj with all defaults
                            existingValue = ThrowingSupplier.unchecked(() ->
                                            valueType.getConstructor().newInstance())
                                    .get();
                        } else {
                            existingValue = null;
                        }
                    }

                    if (!Objects.equals(existingValue, newValue)) {
                        changeCounter.incrementAndGet();
                        if (logChanges) {
                            LOGGER.info("Config property {} has changed from [{}] to [{}]",
                                    propertyPath.merge(prop.getName()),
                                    existingValue,
                                    newValue);
                        }
                    }
                }
            });
//            }

        } catch (Exception e) {
            throw new RuntimeException("Error adding config instances for " + config.getClass().getName(), e);
        }
    }

    public void rebuildConfigInstances(final ProxyConfig newProxyConfig) {
        rebuildConfigInstances(newProxyConfig, true);
    }

    private synchronized void rebuildConfigInstances(final ProxyConfig newProxyConfig,
                                                     final boolean logChanges) {
        LOGGER.debug("Rebuilding object instance map");
        final Map<Class<? extends AbstractConfig>, AbstractConfig> newInstanceMap = new HashMap<>();

        final AtomicInteger changeCounter = new AtomicInteger();

        addConfigInstances(newProxyConfig,
                newInstanceMap,
                createObjectMapper(),
                ProxyConfig.ROOT_PROPERTY_PATH,
                changeCounter,
                logChanges);

        if (configInstanceMap == null || changeCounter.get() > 0) {
            LOGGER.debug("Swapping out configInstanceMap, changeCounter {}", changeCounter);
            // Now swap out the current map with the new one
            if (configInstanceMap == null) {
                configInstanceMap = new HashMap<>();
            }
            configInstanceMap = newInstanceMap;
        } else {
            LOGGER.debug("No change to the map");
        }

        LOGGER.debug("Completed rebuild of object instance map");
    }

    private static ObjectMapper createObjectMapper() {
        final ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(SerializationFeature.INDENT_OUTPUT, false);
        mapper.setSerializationInclusion(Include.NON_NULL);
        return mapper;
    }
}
