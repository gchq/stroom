package stroom.config.app;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.util.config.PropertyUtil;
import stroom.util.shared.AbstractConfig;

import java.lang.reflect.Field;

class TestAppConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestAppConfig.class);

    private final static String STROOM_PACKAGE_PREFIX = "stroom.";

    /**
     * Test to verify that all fields in the config tree of type stroom.*
     * are marked with the IsConfig interface. Also useful for seeing the object tree
     * and the annotations
     */
    @Test
    public void testIsConfigUse() {
        checkProperties(AppConfig.class, "");
    }

    private void checkProperties(final Class<?> clazz, final String indent) {
        for (Field field : clazz.getDeclaredFields()) {
            final Class<?> fieldClass = field.getType();
            if (fieldClass.getName().startsWith("stroom") &&
                    ( !fieldClass.getSimpleName().equals("Logger") &&
                            !fieldClass.getSimpleName().equals("LambdaLogger"))) {

                LOGGER.debug("{}Field {} : {} {}",
                        indent, field.getName(), fieldClass.getSimpleName(), fieldClass.getAnnotations());

                Assertions.assertThat(AbstractConfig.class)
                        .isAssignableFrom(fieldClass);

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
                new AppConfig(),
                prop -> true,
                prop -> {
                    if (prop.getValueFromConfigObject() == null) {
                        LOGGER.warn("{}.{} is null",
                                prop.getParentObject().getClass().getSimpleName(),
                                prop.getName());
                    }
                });
    }
}
