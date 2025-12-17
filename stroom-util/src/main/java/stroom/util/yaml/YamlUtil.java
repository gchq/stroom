/*
 * Copyright 2016-2025 Crown Copyright
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
import stroom.util.shared.PropertyPath;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

public class YamlUtil {

    private static final ObjectMapper VANILLA_OBJECT_MAPPER = createVanillaObjectMapper();
    private static final ObjectMapper OBJECT_MAPPER = createYamlObjectMapper(true);
    private static final ObjectMapper NO_INDENT_MAPPER = createYamlObjectMapper(false);

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(YamlUtil.class);

    public static Path getYamlFileFromArgs(final String[] args) {
        // This is not ideal as we are duplicating what dropwizard is doing but there appears to be
        // no way of getting the yaml file location from the dropwizard classes
        Path path = null;

        for (final String arg : args) {
            if (arg.toLowerCase().endsWith("yml") || arg.toLowerCase().endsWith("yaml")) {
                final Path yamlFile = Path.of(arg);
                if (Files.isRegularFile(yamlFile)) {
                    path = yamlFile;
                    break;
                } else {
                    // NOTE if you are getting here while running in IJ then you have probable not run
                    // local.yaml.sh
                    LOGGER.warn("YAML config file [{}] from arguments [{}] is not a valid file.\n" +
                                "You need to supply a valid stroom configuration YAML file.",
                            yamlFile, Arrays.asList(args));
                }
            }
        }

        if (path == null) {
            throw new RuntimeException(
                    "Could not extract YAML config file from arguments [" + Arrays.asList(args) + "]");
        }

        Path realConfigFile = null;
        try {
            realConfigFile = path.toRealPath();
            LOGGER.info("Using config file: \"" + realConfigFile + "\"");
        } catch (final IOException e) {
            LOGGER.error("Unable to find location of real config file from \"" + path + "\"");
        }

        return realConfigFile;
    }

    public static <T> T mergeYamlNodeTrees(final Class<T> valueType,
                                           final Function<ObjectMapper, JsonNode> sparseTreeProvider,
                                           final Function<ObjectMapper, JsonNode> defaultTreeProvider) {

        return mergeYamlNodeTrees(valueType, createYamlObjectMapper(), sparseTreeProvider, defaultTreeProvider);
    }

    public static JsonNode mergeYamlNodeTrees(final ObjectMapper yamlObjectMapper,
                                              final Function<ObjectMapper, JsonNode> sparseTreeProvider,
                                              final Function<ObjectMapper, JsonNode> defaultTreeProvider) {

        final JsonNode sparseRootNode = sparseTreeProvider.apply(yamlObjectMapper);
        final JsonNode defaultRootNode = defaultTreeProvider.apply(yamlObjectMapper);

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
            diffNodeTrees(yamlObjectMapper, defaultRootNode, mergedNode);
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
     * @param yamlObjectMapper    The {@link ObjectMapper} to use for (de)serialisation.
     * @param sparseTreeProvider  A function to produce a {@link JsonNode} tree of the sparse yaml. Allows you to
     *                            create the node tree from file/string/stream/etc.
     * @param defaultTreeProvider A function to produce a {@link JsonNode} tree of the default yaml. Allows you to*
     *                            create the node tree from file/string/stream/etc.
     * @param <T>                 The POJO type to convert the merged yaml into.
     * @return The merged yaml de-serialised into T.
     */
    public static <T> T mergeYamlNodeTrees(final Class<T> valueType,
                                           final ObjectMapper yamlObjectMapper,
                                           final Function<ObjectMapper, JsonNode> sparseTreeProvider,
                                           final Function<ObjectMapper, JsonNode> defaultTreeProvider) {

        final JsonNode mergedNode = mergeYamlNodeTrees(
                yamlObjectMapper,
                sparseTreeProvider,
                defaultTreeProvider);

        try {
            return yamlObjectMapper.treeToValue(mergedNode, valueType);
        } catch (final JsonProcessingException e) {
            throw new RuntimeException(LogUtil.message(
                    "Error converting merged tree to {}: {}", valueType.getName(), e.getMessage()), e);
        }
    }

    private static void diffNodeTrees(final ObjectMapper objectMapper, final JsonNode node1, final JsonNode node2) {
        try {
            final String node1Yaml = objectMapper.writeValueAsString(node1);
            final String node2Yaml = objectMapper.writeValueAsString(node2);
            DiffUtil.unifiedDiff(node1Yaml, node2Yaml, true, 3);
        } catch (final IOException e) {
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
            final Set<String> childFieldNames = new HashSet<>();
            jsonNode.fieldNames().forEachRemaining(childFieldNames::add);

            equivalentSourceNode.fieldNames().forEachRemaining(childFieldName -> {
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

            jsonNode.fields().forEachRemaining(entry -> {
            });
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

    public static ObjectMapper getMapper() {
        return OBJECT_MAPPER;
    }

    public static ObjectMapper getNoIndentMapper() {
        return NO_INDENT_MAPPER;
    }

    public static ObjectMapper getVanillaObjectMapper() {
        return VANILLA_OBJECT_MAPPER;
    }

    private static ObjectMapper createYamlObjectMapper() {
        return createYamlObjectMapper(false);
    }

    /**
     * No configurations apart from registering {@link Jdk8Module} for {@link java.util.Optional}
     * use.
     */
    private static ObjectMapper createVanillaObjectMapper() {
        return new ObjectMapper(new YAMLFactory())
                .registerModule(new Jdk8Module()); // Needed to deal with Optional<...>
    }

    private static ObjectMapper createYamlObjectMapper(final boolean indent) {
        final YAMLFactory yamlFactory = new YAMLFactory();
        return new ObjectMapper(yamlFactory)
                .registerModule(new Jdk8Module()) // Needed to deal with Optional<...>
//                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
//        mapper.configure(DeserializationFeature.UNWRAP_ROOT_VALUE, true);
                .configure(SerializationFeature.INDENT_OUTPUT, indent)
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
//        mapper.configure(SerializationFeature.WRAP_ROOT_VALUE, true);
                .setSerializationInclusion(Include.NON_NULL);
    }
}
