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

package stroom.config.global.api;

import stroom.config.global.shared.ConfigProperty;
import stroom.config.global.shared.OverrideValue;
import stroom.util.shared.PropertyPath;

import java.util.List;
import java.util.Optional;

/// Reads Stroom's system properties as the Properties screen shows them, for code outside the config module
/// that only needs to look at them. Nothing here changes a property.
///
/// Every method needs the Manage Properties permission, as the Properties screen does, and throws a
/// permission exception without it. Password properties come back sanitised, as the Properties screen gets
/// them: their default, database and YAML values are removed, but whether each override is set is kept.
public interface GlobalConfigReader {

    /// @return Every property, each with its default, its database override and this node's YAML override.
    List<ConfigProperty> list();

    /// @param propertyPath The property's path, e.g. `stroom.ui.defaultMaxResults`.
    /// @return The property, with who set its database override and when if it has one. Empty if there is no
    /// such property.
    Optional<ConfigProperty> fetch(PropertyPath propertyPath);

    /// Gets a property's YAML override on one node, as the Properties screen does for each node: from this node
    /// itself, or by calling the other node. A node's YAML value can differ from another's.
    ///
    /// @param propertyPath The property's path.
    /// @param nodeName     The node.
    /// @return The node's YAML override, which is unset if its YAML doesn't set the property (or sets it to its
    /// default).
    /// @throws RuntimeException if the node can't be reached or there is no such property.
    OverrideValue<String> fetchYamlOverrideValue(PropertyPath propertyPath, String nodeName);
}
