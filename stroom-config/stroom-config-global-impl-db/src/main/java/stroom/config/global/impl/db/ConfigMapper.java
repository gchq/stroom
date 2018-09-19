/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.config.global.impl.db;


import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.google.common.base.Splitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.config.app.AppConfig;
import stroom.config.global.api.ConfigProperty;
import stroom.config.global.impl.db.BeanUtil.Prop;
import stroom.util.logging.LambdaLogger;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Singleton
public class ConfigMapper {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigMapper.class);

    private static final List<String> DELIMITERS = List.of(
            "|", ":", ";", ",", "!", "/", "\\", "#", "@", "~", "-", "_", "=", "+", "?");

    private final List<ConfigProperty> globalProperties = new ArrayList<>();
    private final Map<String, Prop> propertyMap = new HashMap<>();

    @Inject
    ConfigMapper(final AppConfig appConfig) {
        addMethods(appConfig, "stroom");
        globalProperties.sort(Comparator.naturalOrder());
    }

    List<ConfigProperty> getGlobalProperties() {
        return globalProperties;
    }

    private void addMethods(final Object object, final String path) {
        final Map<String, Prop> properties = BeanUtil.getProperties(object);
        properties.forEach((k, v) -> {
            String specifiedName = getName(v.getGetter());
            String specifiedDescription = getDescription(v.getGetter());
            String name = v.getName();
            if (specifiedName != null) {
                name = specifiedName;
            }

            final String fullPath = path + "." + name;

            try {
                final Object value = v.getGetter().invoke(v.getObject());
                if (value != null) {
                    final Class<?> type = value.getClass();
                    if (type.equals(String.class) ||
                            type.equals(Byte.class) ||
                            type.equals(Integer.class) ||
                            type.equals(Long.class) ||
                            type.equals(Short.class) ||
                            type.equals(Float.class) ||
                            type.equals(Double.class) ||
                            type.equals(Boolean.class) ||
                            type.equals(boolean.class) ||
                            type.equals(Character.class) ||
                            value instanceof List ||
                            value instanceof Map) {
                        propertyMap.put(fullPath, v);

                        // Create global property.
                        final String defaultValue = getDefaultValue(v.getObject(), v.getGetter());
                        final ConfigProperty configProperty = new ConfigProperty();
                        configProperty.setSource("Code");
                        configProperty.setName(fullPath);
                        configProperty.setDefaultValue(defaultValue);
                        configProperty.setValue(defaultValue);
                        configProperty.setDescription(specifiedDescription);
                        configProperty.setEditable(true);
                        configProperty.setPassword(fullPath.toLowerCase().contains("pass"));
                        globalProperties.add(configProperty);

                    } else {
                        addMethods(value, fullPath);
                    }
                }
            } catch (final InvocationTargetException | IllegalAccessException e) {
                LOGGER.error(e.getMessage(), e);
            }
        });
    }

    private String getName(final Method method) {
        for (final Annotation declaredAnnotation : method.getDeclaredAnnotations()) {
            if (declaredAnnotation.annotationType().equals(JsonProperty.class)) {
                final JsonProperty jsonProperty = (JsonProperty) declaredAnnotation;
                return jsonProperty.value();
            }
        }
        return null;
    }

    private String getDescription(final Method method) {
        for (final Annotation declaredAnnotation : method.getDeclaredAnnotations()) {
            if (declaredAnnotation.annotationType().equals(JsonPropertyDescription.class)) {
                final JsonPropertyDescription jsonPropertyDescription = (JsonPropertyDescription) declaredAnnotation;
                return jsonPropertyDescription.value();
            }
        }
        return null;
    }

    public static String convert(final Object value) {
        if (value != null) {
            if (value instanceof List) {
                return listToString((List<?>) value);
            } else if (value instanceof Map) {
                return mapToString((Map<?, ?>) value);
            } else {
                return value.toString();
            }
        } else {
            return null;
        }
    }

    private String getDefaultValue(final Object object, final Method method) {
        try {
            final Object value = method.invoke(object, (Object[]) new Class[0]);
            if (value != null) {
                return convert(value);
            }
        } catch (final IllegalArgumentException | IllegalAccessException | InvocationTargetException e) {
            LOGGER.debug(e.getMessage(), e);
        }
        return null;
    }

    public Object update(final String key, final String value) {
        try {
            final Prop prop = propertyMap.get(key);
            if (prop != null) {
                final Type genericType = prop.getSetter().getGenericParameterTypes()[0];
                final Object typedValue = convert(value, genericType);
                prop.getSetter().invoke(prop.getObject(), typedValue);
                return typedValue;
            }
        } catch (final IllegalArgumentException | IllegalAccessException | InvocationTargetException e) {
            // TODO why swallow these exceptions
            LOGGER.debug(e.getMessage(), e);
        }
        return null;
    }

    private static Object convert(final String value, final Type genericType) {
        if (value == null) {
            return null;
        }

        Class<?> type = getDataType(genericType);

        if (type.equals(String.class)) {
            return value;
        } else if (type.equals(Byte.class) || type.equals(byte.class)) {
            return Byte.valueOf(value);
        } else if (type.equals(Integer.class) || type.equals(int.class)) {
            return Integer.valueOf(value);
        } else if (type.equals(Long.class) || type.equals(long.class)) {
            return Long.valueOf(value);
        } else if (type.equals(Short.class) || type.equals(short.class)) {
            return Short.valueOf(value);
        } else if (type.equals(Float.class) || type.equals(float.class)) {
            return Float.valueOf(value);
        } else if (type.equals(Double.class) || type.equals(double.class)) {
            return Double.valueOf(value);
        } else if (type.equals(Boolean.class) || type.equals(boolean.class)) {
            return Boolean.valueOf(value);
        } else if ((type.equals(Character.class) || type.equals(char.class)) && value.length() > 0) {
            return value.charAt(0);
        } else if (type.isAssignableFrom(List.class)) {
            // determine the type of the list items
            Class<?> itemType = getDataType(getGenericTypes(genericType).get(0));
            return stringToList(value, itemType);
        } else if (type.isAssignableFrom(Map.class)) {
            // determine the types of the keys and values
            Class<?> keyType = getDataType(getGenericTypes(genericType).get(0));
            Class<?> valueType = getDataType(getGenericTypes(genericType).get(1));
            return stringToMap(value, keyType, valueType);
        }

        return null;
    }

    private static String listToString(final List<?> list) {

        if (list.isEmpty()) {
            return "";
        }
        List<String> strList = list.stream()
                .map(ConfigMapper::convert)
                .collect(Collectors.toList());

        String allText = String.join("", strList);

        String delimiter = getDelimiter(allText);

        // prefix the delimited form with the delimiter so when we deserialise
        // we know what the delimiter is
        return delimiter + String.join(delimiter, strList);
    }

    private static String mapToString(final Map<?, ?> map) {
        if (map.isEmpty()) {
            return "";
        }
        // convert keys/values to strings
        final List<Map.Entry<String, String>> strEntries = map.entrySet().stream()
                .map(entry -> {
                    String key = ConfigMapper.convert(entry.getKey());
                    String value = ConfigMapper.convert(entry.getValue());
                    return Map.entry(key, value);
                })
                .collect(Collectors.toList());

        // join all strings into one fat string
        final String allText = strEntries.stream()
                .map(entry -> entry.getKey() + entry.getValue())
                .collect(Collectors.joining());

        final String entryDelimiter = getDelimiter(allText);
        final String keyValueDelimiter = getDelimiter(allText, entryDelimiter);

        // prefix the delimited form with the delimiters so when we deserialise
        // we know what the delimiters are
        return entryDelimiter + keyValueDelimiter + strEntries.stream()
                .map(entry ->
                    entry.getKey() + keyValueDelimiter + entry.getValue())
                .collect(Collectors.joining(entryDelimiter));
    }

    private static <T> List<T> stringToList(final String serialisedForm, final Class<T> type) {
       if (serialisedForm == null || serialisedForm.isEmpty()) {
           return Collections.emptyList();
       }

       String delimiter = String.valueOf(serialisedForm.charAt(0));
       String delimitedValue = serialisedForm.substring(1);

       return StreamSupport.stream(Splitter.on(delimiter).split(delimitedValue).spliterator(), false)
               .map(str -> convert(str, type))
               .map(type::cast)
               .collect(Collectors.toList());
    }

    private static <K, V> Map<K, V> stringToMap(
            final String serialisedForm,
            final Class<K> keyType,
            final Class<V> valueType) {

        final String entryDelimiter = String.valueOf(serialisedForm.charAt(0));
        final String keyValueDelimiter = String.valueOf(serialisedForm.charAt(1));

        // now remove the delimiters from our value
        final String delimitedValue = serialisedForm.substring(2);

        return StreamSupport.stream(Splitter.on(entryDelimiter).split(delimitedValue).spliterator(), false)
                .map(keyValueStr -> {
                    final List<String> parts = Splitter.on(keyValueDelimiter).splitToList(keyValueStr);

                    if (parts.size() < 1 || parts.size() > 2) {
                        throw new RuntimeException(LambdaLogger.buildMessage("Too many parts [{}] in value [{}], whole value [{}]",
                                parts.size(), keyValueStr, serialisedForm));
                    }

                    String keyStr = parts.get(0);
                    String valueStr = parts.size() == 1 ? null : parts.get(1);

                    K key = keyType.cast(convert(keyStr, keyType));
                    V value = valueStr != null ? valueType.cast(convert(valueStr, valueType)) : null;

                    return Map.entry(key, value);
                })
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private static Class getDataType(Class clazz) {
        if(clazz.isPrimitive()) {
            return clazz;
        }

        if(clazz.isArray()) {
            return getDataType(clazz.getComponentType());
        }

        return clazz;
    }

    private static Class getDataType(Type type) {
        if(type instanceof Class) {
            return getDataType((Class) type);
        } else if (type instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) type;
            return getDataType(pt.getRawType());
        } else {
            throw new RuntimeException(LambdaLogger.buildMessage("Unexpected type of type {}",
                    type.getClass().getName()));
        }
    }

    private static List<Type> getGenericTypes(Type type) {
        if(type instanceof Class) {
            return Collections.emptyList();
        } else if (type instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) type;
            Type[] specificTypes = pt.getActualTypeArguments();

            return Arrays.asList(specificTypes);
        } else {
            throw new RuntimeException(LambdaLogger.buildMessage("Unexpected type of type {}",
                    type.getClass().getName()));
        }
    }

    private static String getDelimiter(final String allText) {
        return getDelimiter(allText, "");
    }

    private static String getDelimiter(final String allText, final String... blackListedDelimiters) {
        // remove already used delimiters from the available candidates
        final List<String> delimiterCandidates;
        if (blackListedDelimiters!= null && blackListedDelimiters.length > 0) {
            delimiterCandidates = new ArrayList<>(DELIMITERS);
            for (String blackListedDelimiter : blackListedDelimiters) {
                delimiterCandidates.remove(blackListedDelimiter);
            }
        } else {
            delimiterCandidates = DELIMITERS;
        }

        // find the first delimiter that does not appear in the text
        return delimiterCandidates.stream()
                .filter(delimiter -> !allText.contains(delimiter))
                .findFirst()
                .orElseThrow(() ->
                        new RuntimeException("Exhausted all delimiters"));
    }

}
