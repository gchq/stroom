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

package stroom.config.global.client.presenter;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.HasText;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;
import stroom.alert.client.event.AlertEvent;
import stroom.config.global.shared.ConfigProperty;
import stroom.config.global.shared.GlobalConfigResource;
import stroom.config.global.shared.OverrideValue;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.node.client.NodeCache;
import stroom.node.shared.FetchNodeStatusResponse;
import stroom.security.client.api.ClientSecurityContext;
import stroom.svg.client.SvgPreset;
import stroom.svg.client.SvgPresets;
import stroom.ui.config.client.UiConfigCache;
import stroom.widget.button.client.ButtonView;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.DefaultPopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ManageGlobalPropertyEditPresenter
        extends MyPresenterWidget<ManageGlobalPropertyEditPresenter.GlobalPropertyEditView>
        implements ManageGlobalPropertyEditUiHandlers {

    private static final GlobalConfigResource GLOBAL_CONFIG_RESOURCE_RESOURCE = GWT.create(GlobalConfigResource.class);

    private static final OverrideValue<String> ERROR_VALUE = OverrideValue.with("[[ERROR]]");
    private static final String MAGIC_NULL = "NULL";
    private static final String NODES_UNAVAILABLE_MSG = "[Error getting values from some nodes]";
    private static final String MULTIPLE_YAML_VALUES_MSG = "[Multiple YAML values exist in the cluster]";
    private static final String MULTIPLE_EFFECTIVE_VALUES_MSG = "[Multiple effective values exist in the cluster]";
    private static final String MULTIPLE_SOURCES_MSG = "[Configured from multiple sources]";

    private final RestFactory restFactory;
    private final NodeCache nodeCache;
    private final ClientSecurityContext securityContext;
    private final UiConfigCache clientPropertyCache;
    private ConfigProperty configProperty;

    // node => yamlOverride
    private Map<String, OverrideValue<String>> nodeToYamlOverrideMap = new HashMap<>();

    // effectiveValue => (sources)
    private Map<String, Set<NodeSource>> effectiveValueToNodeSourcesMap = new HashMap<>();

    private Provider<ConfigPropertyClusterValuesPresenter> clusterValuesPresenterProvider;

    private final ButtonView effectiveValueWarningsButton;
    private final ButtonView effectiveValueInfoButton;
    private final ButtonView dataTypeHelpButton;

    @Inject
    public ManageGlobalPropertyEditPresenter(final EventBus eventBus,
                                             final GlobalPropertyEditView view,
                                             final RestFactory restFactory,
                                             final NodeCache nodeCache,
                                             final ClientSecurityContext securityContext,
                                             final UiConfigCache clientPropertyCache,
                                             final Provider<ConfigPropertyClusterValuesPresenter> clusterValuesPresenterProvider) {
        super(eventBus, view);
        this.restFactory = restFactory;
        this.nodeCache = nodeCache;
        this.securityContext = securityContext;
        this.clientPropertyCache = clientPropertyCache;
        this.clusterValuesPresenterProvider = clusterValuesPresenterProvider;

        this.effectiveValueWarningsButton = view.addEffectiveValueIcon(SvgPresets.ALERT.title("Click to see cluster values"));
        this.effectiveValueInfoButton = view.addEffectiveValueIcon(SvgPresets.INFO.title("All nodes have the same effective value"));
        this.dataTypeHelpButton = view.addDataTypeHelpIcon(SvgPresets.HELP);

        this.effectiveValueWarningsButton.setVisible(false);
        this.effectiveValueWarningsButton.setVisible(false);
        this.dataTypeHelpButton.setVisible(true);

        view.setUiHandlers(this);
    }

    @Override
    protected void onBind() {
        ClickHandler iconClickHandler = event -> {
            if ((event.getNativeButton() & NativeEvent.BUTTON_LEFT) != 0) {
                onOpenClusterValues();
            }
        };

        registerHandler(effectiveValueInfoButton.addClickHandler(iconClickHandler));
        registerHandler(effectiveValueWarningsButton.addClickHandler(iconClickHandler));
        registerHandler(dataTypeHelpButton.addClickHandler(event -> {
            if ((event.getNativeButton() & NativeEvent.BUTTON_LEFT) != 0) {
                showHelp("Data Types");
            }
        }));
    }

    private void onOpenClusterValues() {
        final PopupUiHandlers popupUiHandlers = new DefaultPopupUiHandlers() {
            @Override
            public void onHide(final boolean autoClose, final boolean ok) {

            }
        };

        if (clusterValuesPresenterProvider != null) {
            final ConfigPropertyClusterValuesPresenter clusterValuesPresenter = clusterValuesPresenterProvider.get();

            // Get the position of this popup so we can show the cluster values popup at a slight
            // offset to make it clear it is above the other one. Offsets are not equal to account
            // for the title bar
            final PopupPosition offsetPopupPosition = new PopupPosition(
                    getView().asWidget().getElement().getAbsoluteLeft() + 20,
                    getView().asWidget().getElement().getAbsoluteTop() + 10);

            clusterValuesPresenter.show(
                    getEntity(),
                    effectiveValueToNodeSourcesMap,
                    offsetPopupPosition,
                    popupUiHandlers);
        }
    }

    protected ClientSecurityContext getSecurityContext() {
        return securityContext;
    }

    void showEntity(final ConfigProperty configProperty, final PopupUiHandlers popupUiHandlers) {

        if (configProperty.getId() != null) {
            updateValuesFromResource(configProperty.getName().toString(), popupUiHandlers);

        } else {
            // new configProperty
            setEntity(configProperty);
            showPopup(popupUiHandlers);
        }
        // find out the yaml values for each node in the cluster
        refreshYamlOverrideForAllNodes();
        updateAllNodeEffectiveValues();
    }

    private void updateWarningState() {
        final long uniqueEffectiveValuesCount = getUniqueEffectiveValuesCount();
        final long uniqueSourcesCount = getUniqueSourcesCount();
        final long nodeCount = getNodeCount();

        if (nodeCount == 1) {
            // Single node cluster so no need for cluster values screen
            effectiveValueInfoButton.setVisible(false);
            effectiveValueWarningsButton.setVisible(false);
        } else {
            if (uniqueEffectiveValuesCount > 1 || uniqueSourcesCount > 1) {
                String msg;

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

    private void updateValuesFromResource(final String propertyName, final PopupUiHandlers popupUiHandlers) {
        final Rest<ConfigProperty> fetchPropertyRest = restFactory.create();

        fetchPropertyRest
                .onSuccess(configProperty -> {
                    setEntity(configProperty);
                    showPopup(popupUiHandlers);
                })
                .onFailure(throwable -> {
                    showError(throwable, "Error fetching property " + propertyName);
                })
                .call(GLOBAL_CONFIG_RESOURCE_RESOURCE)
                .getPropertyByName(propertyName);
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
        return nodeToYamlOverrideMap.containsValue(ERROR_VALUE);
    }

    private long getNodeCount() {
        return nodeToYamlOverrideMap.size();
    }

    private void refreshYamlOverrideForAllNodes() {
        final Rest<FetchNodeStatusResponse> fetchAllNodes = restFactory.create();

        // For each node fire off a request to get the yaml override for that node
        nodeCache.listEnabledNodes(
                nodeNames -> nodeNames.forEach(this::refreshYamlOverrideForNode),
                throwable -> showError(throwable, "Error getting list of all nodes"));
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

        // Add our value into the map
        this.effectiveValueToNodeSourcesMap.computeIfAbsent(
                effectiveValueFromNode,
                k -> new HashSet<>())
                .add(newNodeSource);

        final List<String> keysToRemove = new ArrayList<>();
        effectiveValueToNodeSourcesMap.forEach((effectiveValue, nodeSources) -> {

            if (!effectiveValue.equals(effectiveValueFromNode)) {
                nodeSources.removeIf(existingNodeSource ->
                        existingNodeSource.getNodeName().equals(nodeName));

                if (nodeSources.isEmpty()) {
                    keysToRemove.add(effectiveValue);
                }
            }
        });
        // Remove entries with no nodes
        keysToRemove.forEach(key ->
                effectiveValueToNodeSourcesMap.remove(key));
    }

    private void updateAllNodeEffectiveValues() {
        nodeToYamlOverrideMap.forEach(this::updateEffectiveValueForNode);
    }

    private void refreshYamlOverrideForNode(final String nodeName) {
        final Rest<OverrideValue<String>> fetchNodeYamlOverrideRest = restFactory.create();

        fetchNodeYamlOverrideRest
                .onSuccess(yamlOverride -> {
                    // Add the node's result to our maps
                    refreshYamlOverrideForNode(nodeName, yamlOverride);
                })
                .onFailure(throwable -> refreshYamlOverrideForNode(
                        nodeName, ERROR_VALUE))
                .call(GLOBAL_CONFIG_RESOURCE_RESOURCE)
                .getYamlValueByNodeAndName(configProperty.getName().toString(), nodeName);
    }

    private void refreshYamlOverrideForNode(final String nodeName,
                                            final OverrideValue<String> yamlOverride) {
        nodeToYamlOverrideMap.put(nodeName, yamlOverride);
        updateEffectiveValueForNode(nodeName, yamlOverride);
        updateWarningState();
        refreshValuesOnChange();
    }

    private void showPopup(final PopupUiHandlers popupUiHandlers) {
        final String caption = getEntityDisplayType() + " - " + configProperty.getName();
        final PopupType popupType = PopupType.OK_CANCEL_DIALOG;

        final PopupUiHandlers internalPopupUiHandlers = new PopupUiHandlers() {
            @Override
            public void onHideRequest(final boolean autoClose, final boolean ok) {
                if (ok) {
                    write(true);
                } else {
                    hide();
                }

                popupUiHandlers.onHideRequest(autoClose, ok);
            }

            @Override
            public void onHide(final boolean autoClose, final boolean ok) {
                popupUiHandlers.onHide(autoClose, ok);
            }
        };

        read();
        ShowPopupEvent.fire(
                ManageGlobalPropertyEditPresenter.this,
                ManageGlobalPropertyEditPresenter.this,
                popupType,
                getPopupSize(),
                caption,
                internalPopupUiHandlers);
    }

    protected void hide() {
        HidePopupEvent.fire(
                ManageGlobalPropertyEditPresenter.this,
                ManageGlobalPropertyEditPresenter.this);
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
        if (didAnyNodesError()) {
            text = NODES_UNAVAILABLE_MSG;
        } else if (getUniqueYamlValuesCount() > 1) {
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
        String text = "";
        if (didAnyNodesError()) {
            text = NODES_UNAVAILABLE_MSG;
        } else if (getUniqueSourcesCount() > 1) {
            text = MULTIPLE_SOURCES_MSG;
        } else {
            text = getEntity().getSource().getName();
        }
        getView().getSource().setText(text);
    }

    private void setUiEffectiveValueText() {
        String text = "";
        if (didAnyNodesError()) {
            text = NODES_UNAVAILABLE_MSG;
        } else if (getUniqueEffectiveValuesCount() > 1) {
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

    private void write(final boolean hideOnSave) {
        refreshValuesOnChange();

        ConfigProperty configPropertyToSave = getEntity();

        Rest<ConfigProperty> restCall = restFactory.create();
        restCall
                .onSuccess(savedConfigProperty -> {
                    setEntity(savedConfigProperty);
                    if (hideOnSave) {
                        hide();
                        // Refresh client properties in case they were affected by this change.
                        clientPropertyCache.refresh();
                    }
                });

        if (configPropertyToSave.getId() == null) {
            // No ID so this doesn't exist in the DB
            restCall
                    .onFailure(throwable ->
                            showError(throwable, "Error creating property"))
                    .call(GLOBAL_CONFIG_RESOURCE_RESOURCE)
                    .create(configPropertyToSave);
        } else {
            restCall
                    .onFailure(throwable ->
                            showError(throwable, "Error updating property"))
                    .call(GLOBAL_CONFIG_RESOURCE_RESOURCE)
                    .update(configPropertyToSave.getName().toString(), configPropertyToSave);
        }
    }

    private void showError(final Throwable throwable, final String message) {
        AlertEvent.fireError(
                ManageGlobalPropertyEditPresenter.this,
                message + " - " + throwable.getMessage(),
                null,
                null);
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

    protected PopupSize getPopupSize() {
        return new PopupSize(
                700, 519,
                700, 519,
                1024, 519,
                true);
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
        clientPropertyCache.get()
                .onSuccess(result -> {
                    final String helpUrl = result.getHelpUrl();
                    if (helpUrl != null && helpUrl.trim().length() > 0) {
                        String url = helpUrl + "/user-guide/properties.html" + formatAnchor(anchor);
                        Window.open(url, "_blank", "");
                    } else {
                        AlertEvent.fireError(
                                ManageGlobalPropertyEditPresenter.this,
                                "Help is not configured!",
                                null);
                    }
                })
                .onFailure(caught -> AlertEvent.fireError(
                        ManageGlobalPropertyEditPresenter.this,
                        caught.getMessage(),
                        null));
    }

    protected String formatAnchor(String name) {
        return "#" + name.replace(" ", "-").toLowerCase();
    }

    public interface GlobalPropertyEditView extends View, HasUiHandlers<ManageGlobalPropertyEditUiHandlers> {
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

        ButtonView addYamlValueWarningIcon(SvgPreset preset);

        ButtonView addEffectiveValueIcon(SvgPreset preset);

        ButtonView addDataTypeHelpIcon(SvgPreset preset);
    }

}
