package stroom.test;

import stroom.config.app.ConfigHolder;
import stroom.config.global.impl.ConfigMapper;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.AbstractConfig;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.UnaryOperator;
import javax.inject.Inject;

/**
 * Intercepts any calls to getConfigObject and allows the returned values to be replaced
 * with different values for testing.
 */
public class ConfigMapperSpy extends ConfigMapper {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ConfigMapperSpy.class);

    private final Map<Class<?>, UnaryOperator<?>> valueMappers;

    @Inject
    public ConfigMapperSpy(final ConfigHolder configHolder) {
        super(configHolder);
        this.valueMappers = new HashMap<>();
        Objects.requireNonNull(valueMappers);
    }

    @Override
    public <T extends AbstractConfig> T getConfigObject(final Class<T> clazz) {
        final T config = super.getConfigObject(clazz);
        return mapConfigValue(clazz, config);
    }

    private <T extends AbstractConfig> T mapConfigValue(final Class<T> clazz, final T value) {
        if (valueMappers == null) {
            // when getConfigObject is called as part of the super ctor, valueMappers will be null
            return value;
        } else if (value == null) {
            return null;
        } else {
            final UnaryOperator<?> valueMapper = this.valueMappers.get(clazz);
            if (valueMapper == null) {
                return value;
            } else {
                final UnaryOperator<T> mutator2 = (UnaryOperator<T>) valueMapper;
                final T newValue = mutator2.apply(value);
                LOGGER.debug("Modifying value of config object {}, now; {}", clazz.getSimpleName(), newValue);
                return newValue;
            }
        }
    }

    public <T extends AbstractConfig> void setConfigValueMappers(final Map<Class<T>, UnaryOperator<T>> valueMappers) {
        this.valueMappers.clear();
        this.valueMappers.putAll(valueMappers);
    }

    public <T extends AbstractConfig> void setConfigValueMapper(final Class<T> clazz,
                                                                final UnaryOperator<T> mutator) {
        this.valueMappers.put(clazz, mutator);
    }

    public void clearConfigValueMappers() {
        this.valueMappers.clear();
    }
}
