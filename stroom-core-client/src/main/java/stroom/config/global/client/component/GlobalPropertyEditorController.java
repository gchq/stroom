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

package stroom.config.global.client.component;

import stroom.alert.client.event.AlertCallback;
import stroom.alert.client.event.AlertEvent;
import stroom.config.global.client.model.NodeSource;
import stroom.config.global.shared.ConfigProperty;
import stroom.config.global.shared.GlobalConfigResource;
import stroom.config.global.shared.OverrideValue;
import stroom.dispatch.client.RestError;
import stroom.dispatch.client.RestFactory;
import stroom.node.client.NodeClient;
import stroom.ui.config.client.UiConfigCache;
import stroom.util.client.DelayedUpdate;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.popup.client.PopupPosition;
import stroom.widget.popup.client.PopupSize;
import stroom.widget.popup.client.PopupType;
import stroom.widget.popup.client.event.HidePopupRequestEvent;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.Window;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/// Edits an application property ([GlobalPropertyEditor]) in a dialog made for one use, saving its
/// database value on OK. It asks every enabled node for its YAML value, to show whether the
/// nodes' effective values differ.
public class GlobalPropertyEditorController extends Controller {

    private static final GlobalConfigResource GLOBAL_CONFIG_RESOURCE = GWT.create(GlobalConfigResource.class);

    private static final OverrideValue<String> ERROR_VALUE = OverrideValue.with("[[ERROR]]");
    private static final String MAGIC_NULL = "NULL";
    private static final String MULTIPLE_YAML_VALUES_MSG = "[Multiple YAML values exist in the cluster]";
    private static final String MULTIPLE_EFFECTIVE_VALUES_MSG = "[Multiple effective values exist in the cluster]";
    private static final String MULTIPLE_SOURCES_MSG = "[Configured from multiple sources]";
    private static final String NODE_UNREACHABLE_MSG = "[Node unreachable]";
    private static final String UNKNOWN_MSG = "[Unknown]";

    private final GlobalPropertyEditor view;
    private final RestFactory restFactory;
    private final NodeClient nodeClient;
    private final UiConfigCache uiConfigCache;
    private final Provider<ConfigPropertyClusterValuesController> clusterValuesProvider;
    private final Set<String> unreachableNodes = new HashSet<>();

    // node => yamlOverride
    private final Map<String, OverrideValue<String>> nodeToYamlOverrideMap = new HashMap<>();

    // effectiveValue => (sources)
    // Allows us to see which nodes have which effective values, e.g. you may have some nodes
    // with one value and the rest with another.
    private final Map<String, Set<NodeSource>> effectiveValueToNodeSourcesMap = new HashMap<>();

    private final DelayedUpdate delayedUpdate = new DelayedUpdate(50, () -> {
        updateWarningState();
        refreshValuesOnChange();
    });

    private ConfigProperty configProperty;

    /// Made by GIN, with its form.
    ///
    /// @param eventBus              The application's event bus.
    /// @param view                  The form.
    /// @param restFactory           Fetches and saves the property.
    /// @param nodeClient            Lists the enabled nodes.
    /// @param uiConfigCache         Gives the help URL, and is refreshed after a save.
    /// @param clusterValuesProvider Makes the Cluster Values dialog.
    @Inject
    public GlobalPropertyEditorController(final EventBus eventBus,
                                          final GlobalPropertyEditor view,
                                          final RestFactory restFactory,
                                          final NodeClient nodeClient,
                                          final UiConfigCache uiConfigCache,
                                          final Provider<ConfigPropertyClusterValuesController> clusterValuesProvider) {
        super(eventBus);
        this.view = view;
        this.restFactory = restFactory;
        this.nodeClient = nodeClient;
        this.uiConfigCache = uiConfigCache;
        this.clusterValuesProvider = clusterValuesProvider;
        initWidget(view);

        view.setOnChange(this::refreshValuesOnChange);
        view.setOnShowClusterValues(this::onShowClusterValues);
        view.setOnDataTypeHelp(() -> showHelp("Data Types"));
        onDispose(delayedUpdate::reset);
    }

    /// Shows a property in a dialog, owned by `opener` until it is closed. A saved property is
    /// fetched again first; if that fails, nothing is shown.
    ///
    /// @param opener         What shows it.
    /// @param configProperty The property, new (no id) or saved.
    /// @param onClosed       Called once the dialog has closed, by OK or Cancel.
    public void show(final HasControllerNode opener,
                     final ConfigProperty configProperty,
                     final Runnable onClosed) {
        if (configProperty.getId() != null) {
            final String propertyName = configProperty.getName().toString();
            restFactory
                    .create(GLOBAL_CONFIG_RESOURCE)
                    .method(res -> res.getPropertyByName(propertyName))
                    .onSuccess(fetched -> showDialog(opener, fetched, onClosed))
                    .onFailure(error -> {
                        showError(error, "Error fetching property " + propertyName, null);
                        // Never shown, so still a root
                        dispose();
                    })
                    .owner(this)
                    .taskMonitorFactory(taskMonitor())
                    .exec();
        } else {
            showDialog(opener, configProperty, onClosed);
        }
    }

    private void showDialog(final HasControllerNode opener,
                            final ConfigProperty configProperty,
                            final Runnable onClosed) {
        this.configProperty = configProperty;
        // find out the yaml values for each node in the cluster
        refreshYamlOverrideForAllNodes();
        updateAllNodeEffectiveValues();
        read();

        ControllerDialog.showOnce(opener, this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(PopupSize.resizableX(500))
                .caption("Application Property - " + configProperty.getName())
                .onShow(e -> view.focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        write(e);
                    } else {
                        e.hide();
                    }
                })
                .onHide(e -> onClosed.run())
                .fire();
    }

    private void onShowClusterValues() {
        // Show the cluster values popup at a slight offset from this one to make it clear it is
        // above it. Offsets are not equal to account for the title bar
        final PopupPosition offsetPopupPosition = new PopupPosition(
                view.getElement().getAbsoluteLeft() + 20,
                view.getElement().getAbsoluteTop() + 10);

        final Map<String, Set<NodeSource>> modifiedEffectiveValueToNodeSourcesMap;
        if (unreachableNodes.isEmpty()) {
            modifiedEffectiveValueToNodeSourcesMap = effectiveValueToNodeSourcesMap;
        } else {
            modifiedEffectiveValueToNodeSourcesMap = new HashMap<>(effectiveValueToNodeSourcesMap);
            modifiedEffectiveValueToNodeSourcesMap.put(
                    NODE_UNREACHABLE_MSG,
                    unreachableNodes.stream()
                            .map(node -> new NodeSource(node, UNKNOWN_MSG))
                            .collect(Collectors.toSet()));
        }

        clusterValuesProvider.get().show(
                this,
                configProperty,
                modifiedEffectiveValueToNodeSourcesMap,
                offsetPopupPosition);
    }

    private void updateWarningState() {
        final long uniqueEffectiveValuesCount = getUniqueEffectiveValuesCount();
        final long uniqueSourcesCount = getUniqueSourcesCount();
        final long nodeCount = getNodeCount();

        if (nodeCount == 1 && unreachableNodes.isEmpty()) {
            // Single node cluster so no need for cluster values screen
            view.hideClusterValues();
        } else if (uniqueEffectiveValuesCount > 1 || uniqueSourcesCount > 1 || didAnyNodesError()) {
            final String msg;
            if (didAnyNodesError()) {
                msg = "Error fetching values from all nodes in the cluster";
            } else if (uniqueEffectiveValuesCount > 1) {
                msg = "Multiple unique values exist in the cluster (" + uniqueEffectiveValuesCount + ")";
            } else {
                msg = "Multiple value sources exist in the cluster (" + uniqueSourcesCount + ")";
            }
            view.showClusterValuesWarning(msg);
        } else {
            // All good so show info
            view.showClusterValuesInfo();
        }
    }

    private long getUniqueEffectiveValuesCount() {
        return effectiveValueToNodeSourcesMap.size();
    }

    private long getUniqueYamlValuesCount() {
        return nodeToYamlOverrideMap.values()
                .stream()
                .distinct()
                .count();
    }

    private long getUniqueSourcesCount() {
        return effectiveValueToNodeSourcesMap.values()
                .stream()
                .flatMap(Set::stream)
                .map(NodeSource::getSource)
                .distinct()
                .count();
    }

    private boolean didAnyNodesError() {
        return !unreachableNodes.isEmpty();
    }

    private long getNodeCount() {
        return nodeToYamlOverrideMap.size();
    }

    private void refreshYamlOverrideForAllNodes() {
        // For each node fire off a request to get the yaml override for that node
        unreachableNodes.clear();
        nodeClient.listEnabledNodes(
                guard((final List<String> nodeNames) -> nodeNames.forEach(this::refreshYamlOverrideForNode)),
                error -> {
                    if (!isDisposed()) {
                        showError(error, "Error getting list of all nodes", null);
                    }
                },
                taskMonitor());
    }

    private void updateEffectiveValueForNode(final String nodeName,
                                             final OverrideValue<String> yamlOverride) {
        final String effectiveValueFromNode;
        if (yamlOverride == null) {
            effectiveValueFromNode = "UNKNOWN";
        } else if (yamlOverride.equals(ERROR_VALUE)) {
            effectiveValueFromNode = "Error fetching YAML value for node " + nodeName;
        } else {
            effectiveValueFromNode = configProperty.getEffectiveValue(yamlOverride)
                    .orElse(MAGIC_NULL);
        }

        final NodeSource newNodeSource = new NodeSource(
                nodeName,
                configProperty.getSource(
                        configProperty.getDatabaseOverrideValue(),
                        yamlOverride).getName());

        // Remove this node's value from the map
        final List<String> keysToRemove = new ArrayList<>();
        effectiveValueToNodeSourcesMap.forEach((effectiveValue, nodeSources) -> {
            nodeSources.removeIf(existingNodeSource ->
                    existingNodeSource.getNodeName().equals(nodeName));

            if (nodeSources.isEmpty()) {
                keysToRemove.add(effectiveValue);
            }
        });

        // Remove entries with no nodes
        keysToRemove.forEach(effectiveValueToNodeSourcesMap::remove);

        // Now add our new value into the map
        effectiveValueToNodeSourcesMap.computeIfAbsent(
                        effectiveValueFromNode,
                        k -> new HashSet<>())
                .add(newNodeSource);
    }

    private void updateAllNodeEffectiveValues() {
        nodeToYamlOverrideMap.forEach(this::updateEffectiveValueForNode);
    }

    private void refreshYamlOverrideForNode(final String nodeName) {
        restFactory
                .create(GLOBAL_CONFIG_RESOURCE)
                .method(res -> res.getYamlValueByNodeAndName(configProperty.getName().toString(), nodeName))
                .onSuccess(yamlOverride -> {
                    // Add the node's result to our maps
                    nodeToYamlOverrideMap.put(nodeName, yamlOverride);
                    updateEffectiveValueForNode(nodeName, yamlOverride);
                    delayedUpdate.update();
                })
                .onFailure(error -> {
                    unreachableNodes.add(nodeName);
                    nodeToYamlOverrideMap.remove(nodeName);
                    delayedUpdate.update();
                })
                .owner(this)
                .taskMonitorFactory(taskMonitor())
                .exec();
    }

    private void setUiYamlValueText() {
        String text = "";
        if (getUniqueYamlValuesCount() > 1) {
            text = MULTIPLE_YAML_VALUES_MSG;
        } else if (configProperty.hasYamlOverride()) {
            text = configProperty
                    .getYamlOverrideValue()
                    .getValueOrElse("");
        }
        view.setYamlValue(text);
    }

    private void setUiSourceText() {
        final String text;
        if (getUniqueSourcesCount() > 1) {
            text = MULTIPLE_SOURCES_MSG;
        } else {
            text = configProperty.getSource().getName();
        }
        view.setSource(text);
    }

    private void setUiEffectiveValueText() {
        final String text;
        if (getUniqueEffectiveValuesCount() > 1) {
            text = MULTIPLE_EFFECTIVE_VALUES_MSG;
        } else {
            text = configProperty.getEffectiveValue().orElse(null);
        }
        view.setEffectiveValue(text);
    }

    private void read() {
        view.setPassword(configProperty.isPassword());
        view.setRequireRestart(configProperty.isRequireRestart());
        view.setRequireUiRestart(configProperty.isRequireUiRestart());
        view.setName(configProperty.getName().toString());
        view.setUseOverride(configProperty.hasDatabaseOverride());
        String databaseOverrideValue = "";
        if (configProperty.hasDatabaseOverride()) {
            databaseOverrideValue = configProperty
                    .getDatabaseOverrideValue()
                    .getValueOrElse("");
        }
        setUiYamlValueText();
        view.setDefaultValue(configProperty.getDefaultValue().orElse(""));
        view.setDatabaseValue(databaseOverrideValue);
        setUiEffectiveValueText();
        view.setDescription(configProperty.getDescription());
        view.setDataType(configProperty.getDataTypeName());
        setUiSourceText();

        view.setEditable(configProperty.isEditable());
    }

    private void write(final HidePopupRequestEvent event) {
        refreshValuesOnChange();

        final ConfigProperty configPropertyToSave = configProperty;
        if (configPropertyToSave.getId() == null) {
            // No ID so this doesn't exist in the DB
            restFactory
                    .create(GLOBAL_CONFIG_RESOURCE)
                    .method(res -> res.create(configPropertyToSave))
                    .onSuccess(saved -> afterSave(saved, event))
                    .onFailure(error -> showError(error, "Error creating property", event::reset))
                    .owner(this)
                    .taskMonitorFactory(taskMonitor())
                    .exec();
        } else {
            restFactory
                    .create(GLOBAL_CONFIG_RESOURCE)
                    .method(res -> res.update(configPropertyToSave.getName().toString(), configPropertyToSave))
                    .onSuccess(saved -> afterSave(saved, event))
                    .onFailure(error -> showError(error, "Error updating property", event::reset))
                    .owner(this)
                    .taskMonitorFactory(taskMonitor())
                    .exec();
        }
    }

    private void afterSave(final ConfigProperty saved, final HidePopupRequestEvent event) {
        configProperty = saved;
        event.hide();
        // Refresh client properties in case they were affected by this change.
        uiConfigCache.refresh(result -> {
        }, taskMonitor());
    }

    private void showError(final RestError error, final String message, final AlertCallback callback) {
        AlertEvent.fireError(this, message + " - " + error.getMessage(), null, callback);
    }

    private void refreshValuesOnChange() {
        if (view.isUseOverride()) {
            final String value = view.getDatabaseValue();
            configProperty = configProperty.copy()
                    .databaseOverrideValue(OverrideValue.with(value.trim()))
                    .build();
        } else {
            // Don't clear the db override field on screen in case they unticked by accident
            configProperty = configProperty.copy()
                    .databaseOverrideValue(OverrideValue.unSet(String.class))
                    .build();
        }

        // Refresh the edit status of the override fields
        view.setEditable(configProperty.isEditable());

        updateAllNodeEffectiveValues();
        updateWarningState();

        setUiYamlValueText();
        setUiEffectiveValueText();
        setUiSourceText();
    }

    private void showHelp(final String anchor) {
        uiConfigCache.get(guard(result -> {
            if (result != null) {
                final String helpUrl = result.getHelpUrlProperties();
                if (helpUrl != null && !helpUrl.trim().isEmpty()) {
                    Window.open(helpUrl + formatAnchor(anchor), "_blank", "");
                } else {
                    AlertEvent.fireError(this, "Help is not configured!", null);
                }
            }
        }), taskMonitor());
    }

    private static String formatAnchor(final String name) {
        return "#" + name.replace(' ', '-').toLowerCase();
    }

}
