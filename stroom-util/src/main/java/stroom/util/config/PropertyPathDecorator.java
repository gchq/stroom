package stroom.util.config;

import stroom.util.config.PropertyUtil.Prop;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.HasPropertyPath;
import stroom.util.shared.PropertyPath;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Map;

public class PropertyPathDecorator {

    private static final Logger LOGGER = LoggerFactory.getLogger(PropertyPathDecorator.class);

    private PropertyPathDecorator() {
    }

    public static void decoratePaths(final HasPropertyPath config,
                                     final PropertyPath propertyPath) {

        LOGGER.info("Decorating {} with path {}",
                config.getClass().getSimpleName(),
                propertyPath);
        // Set the path of this config object
        config.setBasePath(propertyPath);

        // Get all the props at this level
        final Map<String, Prop> properties = PropertyUtil.getProperties(config);

        properties.values()
                .parallelStream()
                .forEach(prop -> {
                    final Class<?> propValueType = prop.getValueClass();
                    final Object propValue = prop.getValueFromConfigObject();
                    final Method propGetter = prop.getGetter();
                    // The prop may have a JsonPropery annotation that defines its name
                    final String propSpecifiedName = getNameFromAnnotation(propGetter);
                    final String propName = Strings.isNullOrEmpty(propSpecifiedName)
                            ? prop.getName()
                            : propSpecifiedName;
                    final PropertyPath propPath = propertyPath.merge(propName);

                    if (HasPropertyPath.class.isAssignableFrom(propValueType)) {
                        if (propValue != null) {
                            HasPropertyPath childConfigObject = (HasPropertyPath) propValue;
                            decoratePaths(childConfigObject, propPath);
                        }
                    }
                });
    }

    private static String getNameFromAnnotation(final Method method) {
        for (final Annotation declaredAnnotation : method.getDeclaredAnnotations()) {
            if (declaredAnnotation.annotationType().equals(JsonProperty.class)) {
                final JsonProperty jsonProperty = (JsonProperty) declaredAnnotation;
                return jsonProperty.value();
            }
        }
        return null;
    }
}
