/*
 * Copyright 2026 Crown Copyright
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

package stroom.config.global.impl;

import stroom.config.global.api.GlobalConfigReader;
import stroom.config.global.shared.ConfigProperty;
import stroom.config.global.shared.GlobalConfigResource;
import stroom.config.global.shared.OverrideValue;
import stroom.node.api.NodeService;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PropertyPath;
import stroom.util.shared.ResourcePaths;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.client.SyncInvoker;
import jakarta.ws.rs.core.GenericType;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/// Reads the system properties for other modules, through [GlobalConfigService] (which checks the Manage
/// Properties permission), sanitised as the Properties screen's resource sanitises them.
public class GlobalConfigReaderImpl implements GlobalConfigReader {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(GlobalConfigReaderImpl.class);

    private final Provider<GlobalConfigService> globalConfigServiceProvider;
    private final Provider<NodeService> nodeServiceProvider;

    /// @param globalConfigServiceProvider Reads the properties, checking the user's permission.
    /// @param nodeServiceProvider         Calls the other nodes for their YAML values.
    @Inject
    GlobalConfigReaderImpl(final Provider<GlobalConfigService> globalConfigServiceProvider,
                           final Provider<NodeService> nodeServiceProvider) {
        this.globalConfigServiceProvider = globalConfigServiceProvider;
        this.nodeServiceProvider = nodeServiceProvider;
    }

    @Override
    public List<ConfigProperty> list() {
        return NullSafe.list(globalConfigServiceProvider.get().list().getValues())
                .stream()
                .map(GlobalConfigReaderImpl::sanitise)
                .toList();
    }

    @Override
    public Optional<ConfigProperty> fetch(final PropertyPath propertyPath) {
        Objects.requireNonNull(propertyPath, "propertyPath not supplied");
        return globalConfigServiceProvider.get()
                .fetch(propertyPath)
                .map(GlobalConfigReaderImpl::sanitise);
    }

    @Override
    public OverrideValue<String> fetchYamlOverrideValue(final PropertyPath propertyPath, final String nodeName) {
        Objects.requireNonNull(propertyPath, "propertyPath not supplied");
        Objects.requireNonNull(nodeName, "nodeName not supplied");
        LOGGER.debug("fetchYamlOverrideValue() - property: {}, node: {}", propertyPath, nodeName);

        // The same call the Properties screen makes for each node: the other node answers it from its own YAML,
        // checking the user's permission and sanitising the value as it does so.
        return nodeServiceProvider.get().remoteRestResult(
                nodeName,
                () -> ResourcePaths.buildAuthenticatedApiPath(
                        GlobalConfigResource.BASE_PATH,
                        GlobalConfigResource.CLUSTER_PROPERTIES_SUB_PATH,
                        propertyPath.toString(),
                        GlobalConfigResource.YAML_OVERRIDE_VALUE_SUB_PATH,
                        nodeName),
                () -> fetch(propertyPath)
                        .map(ConfigProperty::getYamlOverrideValue)
                        .orElseThrow(() -> new NotFoundException(LogUtil.message(
                                "Property {} not found", propertyPath))),
                SyncInvoker::get,
                response -> response.readEntity(new GenericType<OverrideValue<String>>() {
                }));
    }

    /// Removes the values of a password property, keeping whether each override is set, as the Properties
    /// screen is given it.
    ///
    /// @param configProperty A property.
    /// @return The property, without its values if it is a password; otherwise as it was.
    static ConfigProperty sanitise(final ConfigProperty configProperty) {
        final ConfigProperty.Builder builder = configProperty.copy();
        if (configProperty.isPassword()) {
            builder.defaultValue(null);
            builder.databaseOverrideValue(sanitise(configProperty.getDatabaseOverrideValue()));
            builder.yamlOverrideValue(sanitise(configProperty.getYamlOverrideValue()));
        }
        return builder.build();
    }

    private static OverrideValue<String> sanitise(final OverrideValue<String> overrideValue) {
        return overrideValue != null && overrideValue.isHasOverride()
                ? OverrideValue.withNullValue(String.class)
                : OverrideValue.unSet(String.class);
    }
}
