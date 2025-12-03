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

package stroom.util.config;

import stroom.util.config.PropertyUtil.ObjectInfo;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.PropertyPath;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class AbstractConfigUtil {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AbstractConfigUtil.class);

    private AbstractConfigUtil() {
    }

    /**
     * Modify one or more values in the config tree by rebuilding all the immutable {@link AbstractConfig}
     * branches as required. As the classes are all immutable any modified objects need to be rebuilt using
     * their constructors and then all ancestor objects have to be rebuilt in the same way.
     *
     * @param config              The object to start walking the tree from.
     * @param basePath            The base {@link PropertyPath} of the config object
     * @param replacementValueMap A map containing the values to update, key on their {@link PropertyPath}.
     * @param <T>
     * @return The new root object containing the mutated tree.
     */
    public static <T extends AbstractConfig> T mutateTree(
            final T config,
            final PropertyPath basePath,
            final Map<PropertyPath, Object> replacementValueMap) {

        final Map<PropertyPath, ObjectInfo<? extends AbstractConfig>> objectInfoMap = new HashMap<>();
        final ObjectMapper objectMapper = JsonUtil.getMapper();
        ;

        // Walk the tree to get the object info for each branch
        buildObjectInfoMap(objectMapper, config, basePath, objectInfoMap);
        // Take a copy so we can remove items as we go
        final Map<PropertyPath, Object> replacementValueMapCopy = new HashMap<>(replacementValueMap);

        final T modifiedTree = mutateBranch(config, objectInfoMap, replacementValueMapCopy);

        if (!replacementValueMapCopy.isEmpty()) {
            throw new RuntimeException(LogUtil.message(
                    "The following property paths [{}] were not found in the object tree. Has a property been renamed",
                    replacementValueMapCopy.keySet()
                            .stream()
                            .sorted()
                            .map(PropertyPath::toString)
                            .collect(Collectors.joining(", "))));
        }

        return modifiedTree;
    }

    /**
     * Populates objectInfoMap with an {@link ObjectInfo} object for each sub-class of {@link AbstractConfig}
     * that is not marked as {@link stroom.util.shared.NotInjectableConfig}. It walks the object tree from
     * the passed config object downwards. path is used as the property path for the root config
     *
     * @param config The root config
     */
    public static void buildObjectInfoMap(
            final ObjectMapper objectMapper,
            final AbstractConfig config,
            final PropertyPath path,
            final Map<PropertyPath, ObjectInfo<? extends AbstractConfig>> objectInfoMap) {

        config.setBasePath(path);

        final ObjectInfo<AbstractConfig> objectInfo = PropertyUtil.getObjectInfo(
                objectMapper,
                path.getPropertyName(),
                config);

        if (objectInfo.getConstructor() == null) {
            throw new RuntimeException("No JsonCreator constructor for " + config.getClass().getName());
        }

        // Decorate the config object with its path info, so it knows where it lives in the tree
        objectInfoMap.put(path, objectInfo);

        objectInfo.getPropertyMap()
                .forEach((k, prop) -> {
                    final PropertyPath fullPath = path.merge(prop.getName());

                    final Class<?> valueType = prop.getValueClass();

                    LOGGER.trace(() -> LogUtil.message("prop: {}, class: {}", prop, prop.getValueClass()));

                    if (AbstractConfig.class.isAssignableFrom(valueType)) {
                        final AbstractConfig childConfigObject = (AbstractConfig) prop.getValueFromConfigObject();
                        // This must be a branch, i.e. config object so recurse into that
                        if (childConfigObject != null) {
                            // Recurse into the child
                            buildObjectInfoMap(
                                    objectMapper,
                                    childConfigObject,
                                    fullPath,
                                    objectInfoMap);
                        }
                    }
                });

    }

    private static <T extends AbstractConfig> T mutateBranch(
            final T config,
            final Map<PropertyPath, ObjectInfo<? extends AbstractConfig>> objectInfoMap,
            final Map<PropertyPath, Object> replacementValueMap) {

        final ObjectInfo<T> objectInfo = (ObjectInfo<T>) Objects.requireNonNull(
                objectInfoMap.get(config.getBasePath()));

        final boolean hasBranchReplacement = replacementValueMap.containsKey(config.getBasePath());

        final T mutatedConfig;
        if (hasBranchReplacement) {
            final Object replacementValue = replacementValueMap.get(config.getBasePath());
            if (replacementValue != null) {
                mutatedConfig = (T) replacementValue;
            } else {
                mutatedConfig = null;
            }
        } else {
            final Map<String, Object> valueMap = new HashMap<>();

            // No replacement for the whole branch so check all its props
            final AtomicBoolean haveAnyPropsChanged = new AtomicBoolean(false);
            objectInfo.getPropertyMap().forEach((propName, prop) -> {
                final PropertyPath propPath = config.getBasePath().merge(propName);
                final Class<?> valueClass = prop.getValueClass();
                LOGGER.trace(() -> LogUtil.message("prop: {}, class: {}", prop, prop.getValueClass()));
                final boolean hasValueReplacement = replacementValueMap.containsKey(propPath);

                if (hasValueReplacement) {
                    final Object replacementValue = replacementValueMap.get(propPath);
                    valueMap.put(propName, replacementValue);
                    haveAnyPropsChanged.set(true);
                    // Remove this entry so when we get to the end we know if we have found them all
                    replacementValueMap.remove(propPath);
                } else {
                    final Object existingPropValue = prop.getValueFromConfigObject();

                    if (existingPropValue != null
                        && AbstractConfig.class.isAssignableFrom(valueClass)) {
                        // Branch so recurse
                        final Object newPropValue = mutateBranch((AbstractConfig) existingPropValue,
                                objectInfoMap,
                                replacementValueMap);
                        if (!Objects.equals(existingPropValue, newPropValue) && !haveAnyPropsChanged.get()) {
                            haveAnyPropsChanged.set(true);
                        }
                        valueMap.put(propName, newPropValue);
                    } else {
                        // Unchanged leaf
                        valueMap.put(propName, existingPropValue);
                    }
                }
            });

            if (haveAnyPropsChanged.get()) {
                // Create a replacement for this branch
                mutatedConfig = objectInfo.createInstance(valueMap::get);
                // Copy the basePath over in case it has been decorated with paths
                mutatedConfig.setBasePath(config.getBasePath());
            } else {
                // no props have change on this branch so just return what we had
                mutatedConfig = config;
            }
        }
        return mutatedConfig;
    }
}
