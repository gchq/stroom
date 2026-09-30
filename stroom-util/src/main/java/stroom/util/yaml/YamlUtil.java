/*
 * Copyright 2018 Crown Copyright
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

package stroom.util.yaml;

import stroom.util.io.DiffUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PropertyPath;

import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.BeanProperty;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.node.JsonNodeType;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

public class YamlUtil {

    private static final YAMLMapper VANILLA_OBJECT_MAPPER = createVanillaMapper();
    private static final YAMLMapper OBJECT_MAPPER = createYamlMapper(true);
    private static final YAMLMapper NO_INDENT_MAPPER = createYamlMapper(false);

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(YamlUtil.class);

    public static <T> T mergeYamlNodeTrees(final Class<T> valueType,
                                           final Function<YAMLMapper, JsonNode> sparseTreeProvider,
                                           final Function<YAMLMapper, JsonNode> defaultTreeProvider) {

        return mergeYamlNodeTrees(valueType, createYamlMapper(), sparseTreeProvider, defaultTreeProvider);
    }

    public static JsonNode mergeYamlNodeTrees(final YAMLMapper yamlMapper,
                                              final Function<YAMLMapper, JsonNode> sparseTreeProvider,
                                              final Function<YAMLMapper, JsonNode> defaultTreeProvider) {

        final JsonNode sparseRootNode = sparseTreeProvider.apply(yamlMapper);
        final JsonNode defaultRootNode = defaultTreeProvider.apply(yamlMapper);

        final JsonNode mergedNode;
        if (sparseRootNode == null || sparseRootNode.isMissingNode()) {
            // Special case for empty input
            mergedNode = defaultRootNode;
        } else {
            mergeNodeTrees(
                    sparseRootNode,
                    null,
                    null,
                    new StringBuilder(),
                    PropertyPath.blank(),
                    defaultRootNode);

            mergedNode = sparseRootNode;
        }

        LOGGER.doIfTraceEnabled(() -> {
            LOGGER.trace("Comparing default config (old) to the merged config (new)");
            diffNodeTrees(yamlMapper, defaultRootNode, mergedNode);
        });

        return mergedNode;
    }

    /**
     * Merges a sparse JsonNode tree with a fully populated default jsonNode tree. Missing nodes will be added.
     * Nodes that are null and are a branch in the default tree are replaced with the corresponding node from
     * the default tree. Nodes that are null but are a leaf in the default tree are left as is and treated as
     * explicit nulls.
     *
     * @param valueType           The POJO type to convert the merged yaml into.
     * @param yamlMapper          The {@link YAMLMapper} to use for (de)serialisation.
     * @param sparseTreeProvider  A function to produce a {@link JsonNode} tree of the sparse yaml. Allows you to
     *                            create the node tree from file/string/stream/etc.
     * @param defaultTreeProvider A function to produce a {@link JsonNode} tree of the default yaml. Allows you to*
     *                            create the node tree from file/string/stream/etc.
     * @param <T>                 The POJO type to convert the merged yaml into.
     * @return The merged yaml de-serialised into T.
     */
    public static <T> T mergeYamlNodeTrees(final Class<T> valueType,
                                           final YAMLMapper yamlMapper,
                                           final Function<YAMLMapper, JsonNode> sparseTreeProvider,
                                           final Function<YAMLMapper, JsonNode> defaultTreeProvider) {

        final JsonNode mergedNode = mergeYamlNodeTrees(
                yamlMapper,
                sparseTreeProvider,
                defaultTreeProvider);

        try {
            return yamlMapper.treeToValue(mergedNode, valueType);
        } catch (final JacksonException e) {
            throw new RuntimeException(LogUtil.message(
                    "Error converting merged tree to {}: {}", valueType.getName(), e.getMessage()), e);
        }
    }

    private static void diffNodeTrees(final YAMLMapper objectMapper, final JsonNode node1, final JsonNode node2) {
        try {
            final String node1Yaml = objectMapper.writeValueAsString(node1);
            final String node2Yaml = objectMapper.writeValueAsString(node2);
            DiffUtil.unifiedDiff(node1Yaml, node2Yaml, true, 3);
        } catch (final Exception e) {
            LOGGER.debug("Error writing node tree to string: " + e.getMessage(), e);
        }
    }

    private static void mergeNodeTrees(final JsonNode jsonNode,
                                       final JsonNode parentJsonNode,
                                       final String name,
                                       final StringBuilder indentBuilder,
                                       final PropertyPath propertyPath,
                                       final JsonNode sourceRootNode) {
        final String jsonPointerExpr = "/" + propertyPath.delimitedBy("/");
        final JsonNode equivalentSourceNode = propertyPath.isBlank()
                ? sourceRootNode
                : sourceRootNode.at(jsonPointerExpr);

//        System.out.println(LogUtil.message("{}name: {}, type: {}, jsonPointerExpr: {}, source type: {}",
//                indent,
//                name,
//                jsonNode.getNodeType(),
//                jsonPointerExpr,
//                equivalentSourceNode.getNodeType()));
        LOGGER.trace("{}name: {}, type: {}, jsonPointerExpr: {}, source type: {}",
                indentBuilder,
                name,
                jsonNode.getNodeType(),
                jsonPointerExpr,
                equivalentSourceNode.getNodeType());

        if (JsonNodeType.OBJECT.equals(jsonNode.getNodeType())) {
            final Set<String> childFieldNames = new HashSet<>(jsonNode.propertyNames());

            equivalentSourceNode.propertyNames().forEach(childFieldName -> {
                LOGGER.trace("{}Field: {}", indentBuilder, childFieldName);
                if (!childFieldNames.contains(childFieldName)) {
                    // Add field that is in the source node tree but not in ours
                    // I.e. assuming the absence of a field is not an explicit null
                    final JsonNode sourceNode = equivalentSourceNode.get(childFieldName);
                    LOGGER.trace("{}Adding missing field {} from source", indentBuilder, childFieldName);
                    ((ObjectNode) jsonNode).set(childFieldName, sourceNode);
                } else {
                    // field is in both so recurse in
                    final JsonNode childNode = jsonNode.get(childFieldName);
                    final PropertyPath childPropertyPath = propertyPath.merge(childFieldName);
                    LOGGER.trace("{}Recursing into field {}", indentBuilder, childFieldName);
                    mergeNodeTrees(
                            childNode,
                            jsonNode,
                            childFieldName,
                            indentBuilder.append("  "),
                            childPropertyPath,
                            sourceRootNode);
                }
            });

//            jsonNode.properties().forEach(entry -> {
//
//            });
        } else if (JsonNodeType.NULL.equals(jsonNode.getNodeType())) {
            if (equivalentSourceNode.isMissingNode()) {
                throw new RuntimeException("Can't find node " + jsonPointerExpr + " in source tree");
            }

            if (equivalentSourceNode.isObject() || equivalentSourceNode.isArray()) {
                // copy the source node into our parent
//                System.out.println(LogUtil.message("{}Replacing value of this field from source", indent));
                LOGGER.trace("{}Replacing value of this field from source", indentBuilder);
                ((ObjectNode) parentJsonNode).replace(name, equivalentSourceNode);
            } else {
//                System.out.println(LogUtil.message("{}Treating this node as an explicit null", indent));
                LOGGER.trace("{}Treating this node as an explicit null", indentBuilder);
            }
        }
    }

    public static YAMLMapper getMapper() {
        return OBJECT_MAPPER;
    }

    public static YAMLMapper getNoIndentMapper() {
        return NO_INDENT_MAPPER;
    }

    public static YAMLMapper getVanillaMapper() {
        return VANILLA_OBJECT_MAPPER;
    }

    private static YAMLMapper createYamlMapper() {
        return createYamlMapper(false);
    }

    /**
     * Standard {@link YAMLMapper} with almost no configurations
     */
    private static YAMLMapper createVanillaMapper() {
        return YAMLMapper.builder()
                // JacksonV3 changes the default behaviour for enums to use the toString
                // as the serialised form, so turn that off so we use the name.
                .disable(EnumFeature.READ_ENUMS_USING_TO_STRING)
                // JacksonV3 changes the default behaviour for enums to use the toString
                // as the serialised form, so turn that off so we use the name.
                .disable(EnumFeature.WRITE_ENUMS_USING_TO_STRING)
                // This defaults to true in Jackson v3, but false in v2.
                // Make it behave like v2 for now, with the warning module to warn us about null
                // primitives. When we think we have fixed the issues, we can make it error for null prims
                .configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false)
                .addModule(createPrimitiveWarningModule())
                .build();
    }

    private static YAMLMapper createYamlMapper(final boolean indent) {
        return createVanillaMapper()
                .rebuild()
                .configure(SerializationFeature.INDENT_OUTPUT, indent)
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
                .changeDefaultPropertyInclusion(incl ->
                        incl.withValueInclusion(JsonInclude.Include.NON_NULL))
                .changeDefaultPropertyInclusion(incl ->
                        incl.withContentInclusion(JsonInclude.Include.NON_NULL))
                .build();
    }

    public static YAMLMapper createConsistentOrderYamlMapper(final boolean indent) {
        return createYamlMapper(indent)
                .rebuild()
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .disable(MapperFeature.SORT_CREATOR_PROPERTIES_FIRST)
                .build();
    }

    private static SimpleModule createPrimitiveWarningModule() {
        final SimpleModule warningModule = new SimpleModule("NullPrimitiveWarningModule");

        // Register all 8 primitive types using the new Jackson v3 ValueDeserializer base
        warningModule.addDeserializer(
                boolean.class,
                new WarnOnNullDeserializer<>("boolean", false, JsonParser::getValueAsBoolean));
        warningModule.addDeserializer(
                byte.class,
                new WarnOnNullDeserializer<>("byte", (byte) 0, p -> (byte) p.getValueAsInt()));
        warningModule.addDeserializer(
                short.class,
                new WarnOnNullDeserializer<>("short", (short) 0, p -> (short) p.getValueAsInt()));
        warningModule.addDeserializer(
                int.class, new WarnOnNullDeserializer<>("int", 0, JsonParser::getValueAsInt));
        warningModule.addDeserializer(
                long.class, new WarnOnNullDeserializer<>("long", 0L, JsonParser::getValueAsLong));
        warningModule.addDeserializer(
                float.class,
                new WarnOnNullDeserializer<>("float", 0.0f, p -> (float) p.getValueAsDouble()));
        warningModule.addDeserializer(
                double.class,
                new WarnOnNullDeserializer<>("double", 0.0d, JsonParser::getValueAsDouble));

        warningModule.addDeserializer(
                char.class,
                new WarnOnNullDeserializer<>("char", '\u0000', p -> {
                    final String text = p.getString();
                    return (text != null && !text.isEmpty())
                            ? text.charAt(0)
                            : '\u0000';
                }));
        return warningModule;
    }


    // --------------------------------------------------------------------------------


    @FunctionalInterface
    private interface PrimitiveReader<T> {

        T read(JsonParser p);
    }


    // --------------------------------------------------------------------------------


    private static class WarnOnNullDeserializer<T> extends ValueDeserializer<T> {

        private final String typeName;
        private final T defaultValue;
        private final PrimitiveReader<T> reader;

        // Contextual fields to hold the class names captured during setup
        private final Class<?> targetClass;
        private final Class<?> enclosingClass;
        private final String propertyName;

        // Root constructor (registered initially in the module)
        public WarnOnNullDeserializer(final String typeName,
                                      final T defaultValue,
                                      final PrimitiveReader<T> reader) {
            this(typeName, defaultValue, reader, null, null, null);
        }

        // Contextual constructor (spawned per-property)
        private WarnOnNullDeserializer(final String typeName,
                                       final T defaultValue,
                                       final PrimitiveReader<T> reader,
                                       final Class<?> targetClass,
                                       final Class<?> enclosingClass,
                                       final String propertyName) {
            this.typeName = typeName;
            this.defaultValue = defaultValue;
            this.reader = reader;
            this.targetClass = targetClass;
            this.enclosingClass = enclosingClass;
            this.propertyName = propertyName;
        }

        @Override
        public ValueDeserializer<?> createContextual(final DeserializationContext ctxt,
                                                     final BeanProperty property) {
            // This method is triggered during initialisation where ctxt and property ARE populated
            // We only want to capture the extra info if the bean prop is a primitive
            if (NullSafe.test(property, BeanProperty::getType, JavaType::isPrimitive)) {
                final Class<?> target = property.getType().getRawClass();
                final Class<?> enclosing = (property.getMember() != null)
                        ? property.getMember().getDeclaringClass()
                        : null;
                final String beanPropertyName = property.getName();

                // Return a clone of this deserializer containing the specific class context
                return new WarnOnNullDeserializer<>(
                        this.typeName, this.defaultValue, this.reader, target, enclosing, beanPropertyName);
            } else {
                return this;
            }
        }

        @Override
        public T deserialize(final JsonParser p, final DeserializationContext ctxt) {
            return reader.read(p);
        }

        @Override
        public T getNullValue(final DeserializationContext ctxt) {
            // This method is only going to be called when we have a null primitive, so the overhead
            // is acceptable as we are trying to eradicate cases of null primitives
            final String enclosingClassName = NullSafe.getOrElse(enclosingClass, Class::getName, "?");
            final String targetClassName = NullSafe.getOrElse(targetClass, Class::getName, "?");
            String jsonNodeName = "?";

            if (NullSafe.nonNull(ctxt, DeserializationContext::getParser)) {
                try {
                    // This will be null if the prop is not in the json
                    final String currentField = ctxt.getParser().currentName();
                    if (currentField != null) {
                        jsonNodeName = currentField;
                    }
                } catch (final Exception e) {
                    // Fallback gracefully if stream token evaluation fails
                }
            }

            // Logs a warning when a null is mapped to a primitive default path
            LOGGER.error("Found null value for {} primitive YAML property. Using default value '{}' instead. " +
                         "jsonNodeName: '{}', targetClassName: '{}', enclosingClassName: '{}', " +
                         "beanPropertyName: '{}'. " +
                         "Please raise an issue at https://github.com/gchq/stroom/issues, including this " +
                         "error in the description.",
                    typeName, defaultValue, jsonNodeName, targetClassName, enclosingClassName, propertyName);
            return defaultValue;
        }
    }
}
