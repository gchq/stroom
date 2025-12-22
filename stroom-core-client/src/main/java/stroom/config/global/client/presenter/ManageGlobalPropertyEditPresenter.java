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

package stroom.config.global.client.presenter;

import stroom.alert.client.event.AlertCallback;
import stroom.alert.client.event.AlertEvent;
import stroom.config.global.shared.ConfigProperty;
import stroom.config.global.shared.GlobalConfigResource;
import stroom.config.global.shared.OverrideValue;
import stroom.dispatch.client.RestError;
import stroom.dispatch.client.RestFactory;
import stroom.node.client.NodeManager;
import stroom.security.client.api.ClientSecurityContext;
import stroom.svg.client.Preset;
import stroom.svg.client.SvgPresets;
import stroom.ui.config.client.UiConfigCache;
import stroom.util.client.DelayedUpdate;
import stroom.widget.button.client.ButtonView;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.util.client.MouseUtil;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.Focus;
import com.google.gwt.user.client.ui.HasText;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class ManageGlobalPropertyEditPresenter
        extends MyPresenterWidget<ManageGlobalPropertyEditPresenter.GlobalPropertyEditView>
        implements ManageGlobalPropertyEditUiHandlers {

    private static final GlobalConfigResource GLOBAL_CONFIG_RESOURCE_RESOURCE = GWT.create(GlobalConfigResource.class);

    private static final OverrideValue<String> ERROR_VALUE = OverrideValue.with("[[ERROR]]");
    private static final String MAGIC_NULL = "NULL";
    private static final String MULTIPLE_YAML_VALUES_MSG = "[Multiple YAML values exist in the cluster]";
    private static final String MULTIPLE_EFFECTIVE_VALUES_MSG = "[Multiple effective values exist in the cluster]";
    private static final String MULTIPLE_SOURCES_MSG = "[Configured from multiple sources]";
    private static final String NODE_UNREACHABLE_MSG = "[Node unreachable]";
    private static final String UNKNOWN_MSG = "[Unknown]";

    private final RestFactory restFactory;
    private final NodeManager nodeManager;
    private final Set<String> unreachableNodes = new HashSet<>();
    private final ClientSecurityContext securityContext;
    private final UiConfigCache clientPropertyCache;
    private ConfigProperty configProperty;

    // node => yamlOverride
    private final Map<String, OverrideValue<String>> nodeToYamlOverrideMap = new HashMap<>();

    // effectiveValue => (sources)
    // Allows us to see which nodes have which effective values, e.g. you may have some nodes
    // with one value and the rest with another.
    private final Map<String, Set<NodeSource>> effectiveValueToNodeSourcesMap = new HashMap<>();

    private final Provider<ConfigPropertyClusterValuesPresenter> clusterValuesPresenterProvider;

    private final ButtonView effectiveValueWarningsButton;
    private final ButtonView effectiveValueInfoButton;
    private final ButtonView dataTypeHelpButton;

    private final DelayedUpdate delayedUpdate = new DelayedUpdate(50, () -> {
        updateWarningState();
        refreshValuesOnChange();
    });

    @Inject
    public ManageGlobalPropertyEditPresenter(
            final EventBus eventBus,
            final GlobalPropertyEditView view,
            final RestFactory restFactory,
            final NodeManager nodeManager,
            final ClientSecurityContext securityContext,
            final UiConfigCache clientPropertyCache,
            final Provider<ConfigPropertyClusterValuesPresenter> clusterValuesPresenterProvider) {

        super(eventBus, view);
        this.restFactory = restFactory;
        this.nodeManager = nodeManager;
        this.securityContext = securityContext;
        this.clientPropertyCache = clientPropertyCache;
        this.clusterValuesPresenterProvider = clusterValuesPresenterProvider;

        this.effectiveValueWarningsButton = view.addEffectiveValueIcon(
                SvgPresets.ALERT.title("Click to see cluster values"));
        this.effectiveValueInfoButton = view.addEffectiveValueIcon(
                SvgPresets.INFO.title("All nodes have the same effective value"));
        this.dataTypeHelpButton = view.addDataTypeHelpIcon(SvgPresets.HELP);

        this.effectiveValueWarningsButton.setVisible(false);
        this.effectiveValueWarningsButton.setVisible(false);
        this.dataTypeHelpButton.setVisible(true);

        view.setUiHandlers(this);
    }

    @Override
    protected void onBind() {
        final ClickHandler iconClickHandler = event -> {
            if (MouseUtil.isPrimary(event)) {
                onOpenClusterValues();
            }
        };

        registerHandler(effectiveValueInfoButton.addClickHandler(iconClickHandler));
        registerHandler(effectiveValueWarningsButton.addClickHandler(iconClickHandler));
        registerHandler(dataTypeHelpButton.addClickHandler(event -> {
            if (MouseUtil.isPrimary(event)) {
                showHelp("Data Types");
            }
        }));
    }

    private void onOpenClusterValues() {
        if (clusterValuesPresenterProvider != null) {
            final ConfigPropertyClusterValuesPresenter clusterValuesPresenter = clusterValuesPresenterProvider.get();

            // Get the position of this popup so we can show the cluster values popup at a slight
            // offset to make it clear it is above the other one. Offsets are not equal to account
            // for the title bar
            final PopupPosition offsetPopupPosition = new PopupPosition(
                    getView().asWidget().getElement().getAbsoluteLeft() + 20,
                    getView().asWidget().getElement().getAbsoluteTop() + 10);


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

            clusterValuesPresenter.show(
                    getEntity(),
                    modifiedEffectiveValueToNodeSourcesMap,
                    offsetPopupPosition);
        }
    }

    protected ClientSecurityContext getSecurityContext() {
        return securityContext;
    }

    void showEntity(final ConfigProperty configProperty, final Runnable hideRunnable) {
        if (configProperty.getId() != null) {
            updateValuesFromResource(configProperty.getName().toString(), hideRunnable);
        } else {
            // new configProperty
            show(configProperty, hideRunnable);
        }
    }

    private void updateWarningState() {
        final long uniqueEffectiveValuesCount = getUniqueEffectiveValuesCount();
        final long uniqueSourcesCount = getUniqueSourcesCount();
        final long nodeCount = getNodeCount();

        if (nodeCount == 1 && unreachableNodes.size() == 0) {
            // Single node cluster so no need for cluster values screen
            effectiveValueInfoButton.setVisible(false);
            effectiveValueWarningsButton.setVisible(false);
        } else {
            if (uniqueEffectiveValuesCount > 1 || uniqueSourcesCount > 1 || didAnyNodesError()) {
                final String msg;

                if (didAnyNodesError()) {
                    msg = "Error fetching values from all nodes in the cluster";
                } else if (uniqueEffectiveValuesCount > 1) {
                    msg = "Multiple unique values exist in the cluster (" + uniqueEffectiveValuesCount + ")";
                } else {
                    msg = "Multiple value sources exist in the cluster (" + uniqueSourcesCount + ")";
                }

                // show warning
                effectiveValueWarningsButton.setTitle(msg);
                effectiveValueInfoButton.setVisible(false);
                effectiveValueWarningsButton.setVisible(true);
            } else {
                // All good so show info
                effectiveValueInfoButton.setVisible(true);
                effectiveValueWarningsButton.setVisible(false);
            }
        }
    }

    private void updateValuesFromResource(final String propertyName, final Runnable hideRunnable) {
        restFactory
                .create(GLOBAL_CONFIG_RESOURCE_RESOURCE)
                .method(res -> res.getPropertyByName(propertyName))
                .onSuccess(configProperty ->
                        show(configProperty, hideRunnable))
                .onFailure(error ->
                        showError(error, "Error fetching property " + propertyName, null))
                .taskMonitorFactory(this)
                .exec();
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
        return unreachableNodes.size() > 0;
    }

    private long getNodeCount() {
        return nodeToYamlOverrideMap.size();
    }

    private void refreshYamlOverrideForAllNodes() {
        // For each node fire off a request to get the yaml override for that node
        unreachableNodes.clear();
        nodeManager.listEnabledNodes(
                nodeNames -> nodeNames.forEach(this::refreshYamlOverrideForNode),
                throwable -> showError(throwable, "Error getting list of all nodes", null),
                this);
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
        this.effectiveValueToNodeSourcesMap.computeIfAbsent(
                        effectiveValueFromNode,
                        k -> new HashSet<>())
                .add(newNodeSource);
    }

    private void updateAllNodeEffectiveValues() {
        nodeToYamlOverrideMap.forEach(this::updateEffectiveValueForNode);
    }

    private void refreshYamlOverrideForNode(final String nodeName) {
        restFactory
                .create(GLOBAL_CONFIG_RESOURCE_RESOURCE)
                .method(res -> res.getYamlValueByNodeAndName(configProperty.getName().toString(), nodeName))
                .onSuccess(yamlOverride -> {
                    // Add the node's result to our maps
                    refreshYamlOverrideForNode(nodeName, yamlOverride);
                })
                .onFailure(throwable -> {
                    unreachableNodes.add(nodeName);
                    nodeToYamlOverrideMap.remove(nodeName);

//                    updateEffectiveValueForNode(nodeName, ERROR_VALUE);
                    delayedUpdate.update();
                })
                .taskMonitorFactory(this)
                .exec();
    }

    private void refreshYamlOverrideForNode(final String nodeName,
                                            final OverrideValue<String> yamlOverride) {
        nodeToYamlOverrideMap.put(nodeName, yamlOverride);
        updateEffectiveValueForNode(nodeName, yamlOverride);
        delayedUpdate.update();
    }

    private void show(final ConfigProperty configProperty,
                      final Runnable hideRunnable) {
        setEntity(configProperty);
        // find out the yaml values for each node in the cluster
        refreshYamlOverrideForAllNodes();
        updateAllNodeEffectiveValues();

        final String caption = getEntityDisplayType() + " - " + configProperty.getName();
        final PopupType popupType = PopupType.OK_CANCEL_DIALOG;

        read();
        ShowPopupEvent.builder(this)
                .popupType(popupType)
                .popupSize(getPopupSize())
                .caption(caption)
                .onShow(e -> getView().focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        write(e);
                    } else {
                        e.hide();
                    }
                })
                .onHide(e -> hideRunnable.run())
                .fire();
    }

    private ConfigProperty getEntity() {
        return configProperty;
    }

    private void setEntity(final ConfigProperty entity) {
        this.configProperty = entity;
    }

    private String getEntityDisplayType() {
        return "Application Property";
    }

    private void setUiYamlValueText() {
        String text = "";
//        if (didAnyNodesError()) {
//            text = NODES_UNAVAILABLE_MSG;
//        } else

        if (getUniqueYamlValuesCount() > 1) {
            text = MULTIPLE_YAML_VALUES_MSG;
        } else {
            if (getEntity().hasYamlOverride()) {
                text = getEntity()
                        .getYamlOverrideValue()
                        .getValueOrElse("");
            }
        }
        getView().getYamlValue().setText(text);
    }

    private void setUiSourceText() {
        final String text;
//        if (didAnyNodesError()) {
//            text = NODES_UNAVAILABLE_MSG;
//        } else

        if (getUniqueSourcesCount() > 1) {
            text = MULTIPLE_SOURCES_MSG;
        } else {
            text = getEntity().getSource().getName();
        }
        getView().getSource().setText(text);
    }

    private void setUiEffectiveValueText() {
        final String text;
//        if (didAnyNodesError()) {
//            text = NODES_UNAVAILABLE_MSG;
//        } else
        if (getUniqueEffectiveValuesCount() > 1) {
            text = MULTIPLE_EFFECTIVE_VALUES_MSG;
        } else {
            text = getEntity().getEffectiveValue().orElse(null);
        }
        getView().getEffectiveValue().setText(text);
    }

    private void read() {
        getView().setPasswordStyle(getEntity().isPassword());
        getView().setRequireRestart(getEntity().isRequireRestart());
        getView().setRequireUiRestart(getEntity().isRequireUiRestart());
        getView().getName().setText(getEntity().getName().toString());
        getView().setUseOverride(getEntity().hasDatabaseOverride());
        String databaseOverrideValue = "";
        if (getEntity().hasDatabaseOverride()) {
            databaseOverrideValue = getEntity()
                    .getDatabaseOverrideValue()
                    .getValueOrElse("");
        }
        setUiYamlValueText();
        getView().getDefaultValue().setText(getEntity()
                .getDefaultValue()
                .orElse(""));
        getView().getDatabaseValue().setText(databaseOverrideValue);
        setUiEffectiveValueText();
        getView().getDescription().setText(getEntity().getDescription());
        getView().getDataType().setText(getEntity().getDataTypeName());
        setUiSourceText();

        getView().setEditable(getEntity().isEditable());
    }

    private void write(final HidePopupRequestEvent event) {
        refreshValuesOnChange();

        final ConfigProperty configPropertyToSave = getEntity();


        if (configPropertyToSave.getId() == null) {
            // No ID so this doesn't exist in the DB
            restFactory
                    .create(GLOBAL_CONFIG_RESOURCE_RESOURCE)
                    .method(res -> res.create(configPropertyToSave))
                    .onSuccess(savedConfigProperty -> {
                        setEntity(savedConfigProperty);
                        event.hide();
                        // Refresh client properties in case they were affected by this change.
                        clientPropertyCache.refresh(result -> {
                        }, this);
                    })
                    .onFailure(throwable ->
                            showError(throwable, "Error creating property", event::reset))
                    .taskMonitorFactory(this)
                    .exec();
        } else {
            restFactory
                    .create(GLOBAL_CONFIG_RESOURCE_RESOURCE)
                    .method(res -> res.update(configPropertyToSave.getName().toString(), configPropertyToSave))
                    .onSuccess(savedConfigProperty -> {
                        setEntity(savedConfigProperty);
                        event.hide();
                        // Refresh client properties in case they were affected by this change.
                        clientPropertyCache.refresh(result -> {
                        }, this);
                    })
                    .onFailure(throwable ->
                            showError(throwable, "Error updating property", event::reset))
                    .taskMonitorFactory(this)
                    .exec();
        }
    }

    private void showError(final RestError error, final String message, final AlertCallback callback) {
        AlertEvent.fireError(
                ManageGlobalPropertyEditPresenter.this,
                message + " - " + error.getMessage(),
                null,
                callback);
    }

    private void refreshValuesOnChange() {
        if (getView().getUseOverride()) {
            final String value = getView().getDatabaseValue().getText();
            getEntity().setDatabaseOverrideValue(OverrideValue.with(value.trim()));
        } else {
            getEntity().setDatabaseOverrideValue(OverrideValue.unSet(String.class));

            // Don't clear the db override field on screen in case they unticked
            // by accident
        }


        // Refresh the edit status of the override fields
        getView().setEditable(getEntity().isEditable());

        updateAllNodeEffectiveValues();
        updateWarningState();

        getView().getSource().setText(getEntity().getSource().getName());

        setUiYamlValueText();
        setUiEffectiveValueText();
        setUiSourceText();
    }

    private PopupSize getPopupSize() {
        return PopupSize.resizableX(500);
    }

    @Override
    public void onChangeUseOverride() {
        refreshValuesOnChange();
    }

    @Override
    public void onChangeOverrideValue() {
        refreshValuesOnChange();
    }

    private void showHelp(final String anchor) {
        clientPropertyCache.get(result -> {
            if (result != null) {
                final String helpUrl = result.getHelpUrlProperties();
                if (helpUrl != null && helpUrl.trim().length() > 0) {
                    final String url = helpUrl + formatAnchor(anchor);
                    Window.open(url, "_blank", "");
                } else {
                    AlertEvent.fireError(
                            ManageGlobalPropertyEditPresenter.this,
                            "Help is not configured!",
                            null);
                }
            }
        }, this);
    }

    protected String formatAnchor(final String name) {
        return "#" + name.replace(' ', '-').toLowerCase();
    }

    public interface GlobalPropertyEditView extends View, Focus, HasUiHandlers<ManageGlobalPropertyEditUiHandlers> {

        HasText getName();

        HasText getDescription();

        HasText getDefaultValue();

        HasText getYamlValue();

        boolean getUseOverride();

        HasText getDatabaseValue();

        HasText getEffectiveValue();

        HasText getSource();

        HasText getDataType();

        void setPasswordStyle(boolean password);

        void setRequireRestart(boolean requiresRestart);

        void setRequireUiRestart(boolean requiresRestart);

        void setUseOverride(boolean useOverride);

        void setEditable(boolean edit);

        ButtonView addYamlValueWarningIcon(Preset preset);

        ButtonView addEffectiveValueIcon(Preset preset);

        ButtonView addDataTypeHelpIcon(Preset preset);
    }
}
