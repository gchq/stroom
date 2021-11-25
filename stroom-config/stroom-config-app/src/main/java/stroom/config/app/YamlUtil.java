/*
 * Copyright 2016 Crown Copyright
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

package stroom.config.app;

import stroom.util.io.DiffUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.PropertyPath;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.dropwizard.configuration.ConfigurationException;
import io.dropwizard.configuration.ConfigurationFactory;
import io.dropwizard.configuration.ConfigurationFactoryFactory;
import io.dropwizard.configuration.ConfigurationSourceProvider;
import io.dropwizard.configuration.DefaultConfigurationFactoryFactory;
import io.dropwizard.configuration.EnvironmentVariableSubstitutor;
import io.dropwizard.configuration.FileConfigurationSourceProvider;
import io.dropwizard.configuration.SubstitutingSourceProvider;
import io.dropwizard.jackson.Jackson;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

public class YamlUtil {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(YamlUtil.class);

    private YamlUtil() {
        // Utility
    }

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

    public static AppConfig readAppConfig(final Path configFile) throws IOException {
        return readConfig(configFile).getAppConfig();
    }

    /**
     * Reads a yaml file that matches the structure of a complete DropWizard {@link Config}
     * object tree. The file undergoes substitution and validation.
     */
    public static Config readConfig(final Path configFile) throws IOException {

        final ConfigurationSourceProvider configurationSourceProvider = createConfigurationSourceProvider(
                new FileConfigurationSourceProvider(), false);

        final ConfigurationFactoryFactory<Config> configurationFactoryFactory =
                new DefaultConfigurationFactoryFactory<>();

        final ConfigurationFactory<Config> configurationFactory = configurationFactoryFactory
                .create(
                        Config.class,
                        io.dropwizard.jersey.validation.Validators.newValidator(),
                        Jackson.newObjectMapper(),
                        "dw");

        Config config = null;
        try {
            config = configurationFactory.build(configurationSourceProvider, configFile.toAbsolutePath().toString());
        } catch (ConfigurationException e) {
            throw new RuntimeException(LogUtil.message("Error parsing configuration from file {}",
                    configFile.toAbsolutePath()), e);
        }

        return config;
    }

    public static ConfigurationSourceProvider createConfigurationSourceProvider(
            final ConfigurationSourceProvider baseConfigurationSourceProvider,
            final boolean logChanges) {

        return new StroomConfigurationSourceProvider(
                new SubstitutingSourceProvider(
                        baseConfigurationSourceProvider,
                        new EnvironmentVariableSubstitutor(false)),
                logChanges);
    }

    /**
     * Reads a YAML string that has already been through the drop wizard env var substitution.
     */
    public static AppConfig readDropWizardSubstitutedAppConfig(final String yamlStr) {

        Objects.requireNonNull(yamlStr);

        final Yaml yaml = new Yaml();
        final Map<String, Object> obj = yaml.load(yamlStr);

        // fail on unknown so it skips over all the drop wiz yaml content that has no
        // corresponding annotated props in DummyConfig
        final ObjectMapper mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        try {
            final DummyConfig dummyConfig = mapper.convertValue(obj, DummyConfig.class);
            return dummyConfig.getAppConfig();
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Error parsing yaml string", e);
        }
    }

    public static void writeConfig(final Config config, final OutputStream outputStream) throws IOException {
        final YAMLFactory yf = new YAMLFactory();
        final ObjectMapper mapper = new ObjectMapper(yf);
        // wrap the AppConfig so that it sits at the right level
        mapper.writeValue(outputStream, config);

    }

    public static void writeConfig(final AppConfig appConfig, final OutputStream outputStream) throws IOException {
        Config config = new Config();
        config.setAppConfig(appConfig);
        writeConfig(config, outputStream);
    }

    public static void writeConfig(final Config config, final Path path) throws IOException {
        final YAMLFactory yf = new YAMLFactory();
        final ObjectMapper mapper = new ObjectMapper(yf);
        // wrap the AppConfig so that it sits at the right level
        mapper.writeValue(path.toFile(), config);
    }

    public static void writeConfig(final AppConfig appConfig, final Path path) throws IOException {
        Config config = new Config();
        config.setAppConfig(appConfig);
        writeConfig(config, path);
    }


    public static <T> T mergeYamlNodeTrees(final Class<T> valueType,
                                           final Function<ObjectMapper, JsonNode> sparseTreeProvider,
                                           final Function<ObjectMapper, JsonNode> defaultTreeProvider) {

        return mergeYamlNodeTrees(valueType, createYamlObjectMapper(), sparseTreeProvider, defaultTreeProvider);
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

        final JsonNode sparseRootNode = sparseTreeProvider.apply(yamlObjectMapper);
        final JsonNode defaultRootNode = defaultTreeProvider.apply(yamlObjectMapper);

        final JsonNode mergedNode;
        if (sparseRootNode.isMissingNode()) {
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

        LOGGER.doIfDebugEnabled(() ->
                diffNodeTrees(yamlObjectMapper, defaultRootNode, mergedNode));

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

    /**
     * Used to simulate the {@link Config} class that wraps {@link AppConfig} when we are not
     * interested in anything in {@link Config} except {@link AppConfig}.
     */
    private static class DummyConfig {

        @JsonProperty("appConfig")
        private final AppConfig appConfig;

        @JsonCreator
        public DummyConfig(@JsonProperty("appConfig") final AppConfig appConfig) {
            this.appConfig = appConfig;
        }

        public AppConfig getAppConfig() {
            return appConfig;
        }
    }
}
