package stroom.util.yaml;

import stroom.util.io.DiffUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.PropertyPath;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

public class YamlUtil {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(YamlUtil.class);

    public static Path getYamlFileFromArgs(final String[] args) {
        // This is not ideal as we are duplicating what dropwizard is doing but there appears to be
        // no way of getting the yaml file location from the dropwizard classes
        Path path = null;

        for (String arg : args) {
            if (arg.toLowerCase().endsWith("yml") || arg.toLowerCase().endsWith("yaml")) {
                Path yamlFile = Path.of(arg);
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
                    "",
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
        } catch (JsonProcessingException e) {
            throw new RuntimeException(LogUtil.message(
                    "Error converting merged tree to {}: {}", valueType.getName(), e.getMessage()), e);
        }
    }

    private static void diffNodeTrees(final ObjectMapper objectMapper, final JsonNode node1, final JsonNode node2) {
        try {
            final String node1Yaml = objectMapper.writeValueAsString(node1);
            final String node2Yaml = objectMapper.writeValueAsString(node2);
            DiffUtil.unifiedDiff(node1Yaml, node2Yaml, true, 3);
        } catch (IOException e) {
            LOGGER.debug("Error writing node tree to string: " + e.getMessage(), e);
        }
    }

    private static void mergeNodeTrees(final JsonNode jsonNode,
                                       final JsonNode parentJsonNode,
                                       final String name,
                                       final String indent,
                                       final PropertyPath propertyPath,
                                       final JsonNode sourceRootNode) {
        final String jsonPointerExpr = "/" + propertyPath.delimitedBy("/");
        final JsonNode equivalentSourceNode = propertyPath.isBlank()
                ? sourceRootNode
                : sourceRootNode.at(jsonPointerExpr);

        LOGGER.trace("{}name: {}, type: {}, jsonPointerExpr: {}, source type: {}",
                indent,
                name,
                jsonNode.getNodeType(),
                jsonPointerExpr,
                equivalentSourceNode.getNodeType());

        if (JsonNodeType.OBJECT.equals(jsonNode.getNodeType())) {
            final Set<String> childFieldNames = new HashSet<>();
            jsonNode.fieldNames().forEachRemaining(childFieldNames::add);

            equivalentSourceNode.fieldNames().forEachRemaining(childFieldName -> {
                LOGGER.trace("{}Field: {}", indent, childFieldName);
                if (!childFieldNames.contains(childFieldName)) {
                    // Add field that is in the source node tree but not in ours
                    // I.e. assuming the absence of a field is not an explicit null
                    final JsonNode sourceNode = equivalentSourceNode.get(childFieldName);
                    LOGGER.trace("{}Adding missing field {} from source", indent, childFieldName);
                    ((ObjectNode) jsonNode).set(childFieldName, sourceNode);
                } else {
                    // field is in both so recurese in
                    final JsonNode childNode = jsonNode.get(childFieldName);
                    final PropertyPath childPropertyPath = propertyPath.merge(childFieldName);
                    LOGGER.trace("{}Recursing into field {}", indent, childFieldName);
                    mergeNodeTrees(
                            childNode,
                            jsonNode,
                            childFieldName,
                            LOGGER.isTraceEnabled()
                                    ? indent + "  "
                                    : indent,
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
                LOGGER.trace("{}Replacing value of this field from source", indent);
                ((ObjectNode) parentJsonNode).replace(name, equivalentSourceNode);
            } else {
                LOGGER.trace("{}Treating this node as an explicit null", indent);
            }
        }
    }

    public static ObjectMapper createYamlObjectMapper() {
        final YAMLFactory yamlFactory = new YAMLFactory();

        return new ObjectMapper(yamlFactory)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
//        mapper.configure(DeserializationFeature.UNWRAP_ROOT_VALUE, true);
                .configure(SerializationFeature.INDENT_OUTPUT, false)
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
//        mapper.configure(SerializationFeature.WRAP_ROOT_VALUE, true);
                .setSerializationInclusion(Include.NON_NULL);
    }
}
