package stroom.proxy.app;

import stroom.docref.DocRef;
import stroom.util.config.PropertyUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LogUtil;
import stroom.util.shared.AbstractProxyConfig;
import stroom.util.shared.NotInjectableConfig;
import stroom.util.time.StroomDuration;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Singleton;

class TestProxyConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestProxyConfig.class);

    private static final Set<Class<?>> WHITE_LISTED_CLASSES = Set.of(
            Logger.class,
            LambdaLogger.class,
            StroomDuration.class
    );

    /**
     * Test to verify that all fields in the config tree of type stroom.*
     * implement IsProxyConfig . Also useful for seeing the object tree
     * and the annotations
     */
    @Test
    public void testIsProxyConfigUse() {
        checkProperties(ProxyConfig.class, "");
    }

    private void checkProperties(final Class<?> clazz, final String indent) {
        for (Field field : clazz.getDeclaredFields()) {
            final Class<?> fieldClass = field.getType();

            // We are trying to inspect props that are themselves config objects
            if (fieldClass.getName().startsWith("stroom")
                    && fieldClass.getSimpleName().endsWith("Config")
                    && !WHITE_LISTED_CLASSES.contains(fieldClass)) {

                LOGGER.debug("{}Field {} : {} {}",
                        indent, field.getName(), fieldClass.getSimpleName(), fieldClass.getAnnotations());

                Assertions.assertThat(AbstractProxyConfig.class)
                        .withFailMessage(LogUtil.message("Class {} does not extend {}",
                                fieldClass.getName(),
                                AbstractProxyConfig.class.getName()))
                        .isAssignableFrom(fieldClass);

                if (fieldClass.getDeclaredAnnotation(NotInjectableConfig.class) == null) {
                    // Class should be injectable so make sure it is marked singleton
                    // Strictly it does not need to be as when we do the bindings we bind to
                    // instances of each IsProxyConfig sub class, but it makes it nice
                    // and explicit for the dev. Just do it!
                    Assertions.assertThat(fieldClass.getDeclaredAnnotation(Singleton.class))
                            .withFailMessage(LogUtil.message("Class {} does not have the {} annotation.",
                                    fieldClass.getName(),
                                    Singleton.class.getName()))
                            .isNotNull();
                }

                // This field is another config object so recurs into it
                checkProperties(fieldClass, indent + "  ");
            } else {
                // Not a stroom config object so nothing to do
            }
        }
    }

    @Test
    void showPropsWithNullValues() {
        // list any config values that are null.  This may be valid so no assertions used.
        PropertyUtil.walkObjectTree(
                new ProxyConfig(),
                prop -> true,
                prop -> {
                    if (prop.getValueFromConfigObject() == null) {
                        LOGGER.warn("{} => {} is null",
                                prop.getParentObject().getClass().getSimpleName(),
                                prop.getName());
                    }
                });
    }

    @Test
    void showPropsWithCollectionValues() {
        // list any config values that are null.  This may be valid so no assertions used.
        PropertyUtil.walkObjectTree(
                new ProxyConfig(),
                prop -> true,
                prop -> {
                    final Class<?> valueClass = prop.getValueClass();
                    if (!valueClass.getName().startsWith("stroom")
                            && isCollectionClass(valueClass)) {
                        LOGGER.warn("{}.{} => {} => {}",
                                prop.getParentObject().getClass().getSimpleName(),
                                prop.getName(),
                                prop.getValueType(),
                                prop.getValueClass());
                    }
//                    if (prop.getValueType().getTypeName().matches("")) {
//                    }
                });
    }

    private boolean isCollectionClass(final Class<?> clazz) {
        return clazz.isAssignableFrom(List.class)
                || clazz.isAssignableFrom(Map.class)
                || clazz.equals(DocRef.class);
    }

}
