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

import stroom.alert.client.event.AlertEvent;
import stroom.config.global.shared.ConfigProperty;
import stroom.config.global.shared.GlobalConfigCriteria;
import stroom.config.global.shared.GlobalConfigResource;
import stroom.config.global.shared.ListConfigResponse;
import stroom.data.client.presenter.CriteriaUtil;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.dispatch.client.RestFactory;
import stroom.node.client.NodeManager;
import stroom.svg.client.Preset;
import stroom.util.client.DataGridUtil;
import stroom.util.client.DelayedUpdate;
import stroom.util.shared.PageRequest;
import stroom.widget.button.client.ButtonView;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.user.cellview.client.ColumnSortEvent;
import com.google.gwt.user.client.Timer;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class ManageGlobalPropertyListPresenter
        extends MyPresenterWidget<PagerView>
        implements ColumnSortEvent.Handler {

    private static final String NODES_UNAVAILABLE_MSG = "[Error getting values]";
    private static final String NODES_UNAVAILABLE_SHORT_MSG = "[Error]";
    private static final String MULTIPLE_VALUES_MSG = "[Multiple values]";
    private static final String MULTIPLE_SOURCES_MSG = "[Multiple]";
    private static final String ERROR_CSS_COLOUR = "red";

    // This is the delay between getting the list of props and hitting all the other nodes
    // to get their specific values.  Too low and we pepper the nodes for each key press of the filter.
    // Too high and there is too big a delay in showing props with mixed source
    private static final int REFRESH_ALL_NODES_TIMER_DELAY_MS = 500;

    private static final int UPDATE_MAPS_TIMER_DELAY_MS = 50;

    private static final GlobalConfigResource GLOBAL_CONFIG_RESOURCE_RESOURCE = GWT.create(GlobalConfigResource.class);

    private final MyDataGrid<ConfigPropertyRow> dataGrid;
    private final MultiSelectionModelImpl<ConfigPropertyRow> selectionModel;
    private final ListDataProvider<ConfigPropertyRow> dataProvider;
    private final RestFactory restFactory;
    private final NodeManager nodeManager;
    private final Set<String> unreachableNodes = new HashSet<>();

    // propName => (node => effectiveValue)
    private final Map<String, Map<String, String>> nodeToClusterEffectiveValuesMap = new HashMap<>();
    // propName => (effectiveValues)
    private Map<String, Set<String>> propertyToUniqueEffectiveValuesMap = new HashMap<>();
    // propName => (node => source)
    private final Map<String, Map<String, String>> nodeToClusterSourcesMap = new HashMap<>();
    // propName => (sources)
    private Map<String, Set<String>> propertyToUniqueSourcesMap = new HashMap<>();

    private final DelayedUpdate refreshAllNodesTimer = new DelayedUpdate(REFRESH_ALL_NODES_TIMER_DELAY_MS,
            this::refreshPropertiesForAllNodes);

    // This is node that responded to the top level request
    private String lastNodeName;

    private final DelayedUpdate updateChildMapsTimer = new DelayedUpdate(UPDATE_MAPS_TIMER_DELAY_MS,
            this::updatePropertyKeyedMaps);

    private final NameFilterTimer nameFilterTimer = new NameFilterTimer();

    private final GlobalConfigCriteria criteria = new GlobalConfigCriteria(
            PageRequest.unlimited(),
            new ArrayList<>(),
            null);

    @Inject
    public ManageGlobalPropertyListPresenter(final EventBus eventBus,
                                             final PagerView view,
                                             final RestFactory restFactory,
                                             final NodeManager nodeManager) {
        super(eventBus, view);

        dataGrid = new MyDataGrid<>(this);
        dataGrid.setMultiLine(true);
        selectionModel = dataGrid.addDefaultSelectionModel(false);
        view.setDataWidget(dataGrid);

        this.restFactory = restFactory;
        this.nodeManager = nodeManager;

        initColumns();

        dataProvider = new ListDataProvider<>();
        dataProvider.addDataDisplay(dataGrid);
        dataProvider.setListUpdater(this::refreshTable);
    }

    @Override
    protected void onBind() {
        registerHandler(dataGrid.addColumnSortHandler(event -> refresh()));
    }

    private void resetLocalState() {
        refreshAllNodesTimer.reset();
        updateChildMapsTimer.reset();

        nodeToClusterEffectiveValuesMap.clear();
        propertyToUniqueEffectiveValuesMap.clear();
        nodeToClusterSourcesMap.clear();
        propertyToUniqueSourcesMap.clear();
        unreachableNodes.clear();
    }

    private void refreshTable(final Range range) {

        CriteriaUtil.setRange(criteria, range);
        CriteriaUtil.setSortList(criteria, dataGrid.getColumnSortList());

        resetLocalState();

//        GWT.log("Refresh table called");

        restFactory
                .create(GLOBAL_CONFIG_RESOURCE_RESOURCE)
                .method(res -> res.list(criteria))
                .onSuccess(listConfigResponse -> {

                    lastNodeName = listConfigResponse.getNodeName();

                    // Build the table based on what we know from one node
                    final List<ConfigPropertyRow> rows = listConfigResponse.getValues().stream()
                            .map(ConfigPropertyRow::new)
                            .collect(Collectors.toList());

//                GWT.log("Offset: " + listConfigResponse.getPageResponse().getOffset()
//                    + " total: " + listConfigResponse.getPageResponse().getTotal());

                    dataProvider.setPartialList(
                            rows,
                            listConfigResponse.getPageResponse()
                                    .getTotal()
                                    .intValue());

                    // The timer will fetch the node specific values for the other nodes
                    // so we need to process the values for this node
                    handleNodeResponse(listConfigResponse);

                    // now we have the props from one node, go off and get all the values/sources
                    // from all the nodes. Use a timer to delay it a bit
                    refreshAllNodesTimer.update();
                })
                .onFailure(caught ->
                        AlertEvent.fireError(
                                ManageGlobalPropertyListPresenter.this,
                                caught.getMessage(),
                                null))
                .taskMonitorFactory(getView())
                .exec();
    }

    private void refreshPropertiesForAllNodes() {
        // Only care about enabled nodes
        unreachableNodes.clear();
        // No point hitting the node that we hit at the top level again as we already have its data
        nodeManager.listEnabledNodes(
                nodeNames ->
                        nodeNames
                                .stream()
                                .filter(nodeName -> !nodeName.equals(lastNodeName))
                                .forEach(this::refreshPropertiesForNode),
                error ->
                        showError(
                                error.getException(),
                                "Error getting list of all nodes. Only properties for one node will be shown"),
                getView());
    }

    private void refreshPropertiesForNode(final String nodeName) {
//        GWT.log("Refreshing " + nodeName);
        criteria.setPageRequest(new PageRequest(
                dataGrid.getVisibleRange().getStart(),
                dataGrid.getVisibleRange().getLength()));

        restFactory
                .create(GLOBAL_CONFIG_RESOURCE_RESOURCE)
                .method(res -> res.listByNode(nodeName, criteria))
                .onSuccess(this::handleNodeResponse)
                .onFailure(throwable -> {
                    unreachableNodes.add(nodeName);

                    nodeToClusterEffectiveValuesMap.keySet().forEach(
                            propName -> {
                                nodeToClusterEffectiveValuesMap.computeIfAbsent(
                                                propName,
                                                k -> new HashMap<>())
                                        .remove(nodeName);

                                nodeToClusterSourcesMap.computeIfAbsent(
                                                propName,
                                                k -> new HashMap<>())
                                        .remove(nodeName);
                            });

                    // kick off the delayed action to update the maps keyed on prop name,
                    // unless another node has already kicked it off
                    updateChildMapsTimer.update();
                })
                .taskMonitorFactory(getView())
                .exec();
    }

    private void handleNodeResponse(final ListConfigResponse listConfigResponse) {
//        GWT.log("Handling response for node " + listConfigResponse.getNodeName());
        unreachableNodes.remove(listConfigResponse.getNodeName());

        // Add the node's result to our maps
        listConfigResponse.getValues().forEach(configProperty -> {
            final String effectiveValue = configProperty.getEffectiveValue().orElse(null);
            final String source = configProperty.getSource().getName();

//            if (configProperty.getNameAsString().equals("stroom.statistics.sql.db.connectionPool.maxPoolSize")) {
//                GWT.log(listConfigResponse.getNodeName() + " - eff: " + effectiveValue + " source " + source);
//            }

            updateNodeKeyedMaps(
                    listConfigResponse.getNodeName(),
                    configProperty.getNameAsString(),
                    effectiveValue,
                    source);

            // kick off the delayed action to update the maps keyed on prop name,
            // unless another node has already kicked it off
            updateChildMapsTimer.update();
        });
    }

    private void updateNodeKeyedMaps(final String nodeName,
                                     final String propName,
                                     final String effectiveValue,
                                     final String source) {

        nodeToClusterEffectiveValuesMap.computeIfAbsent(
                        propName,
                        k -> new HashMap<>())
                .put(nodeName, effectiveValue);

//        if (propName.equals("stroom.statistics.sql.db.connectionPool.maxPoolSize")) {
//            GWT.log(nodeName + " - " + effectiveValue + " - "
//                    + nodeToClusterEffectiveValuesMap.get(propName).get(nodeName));
//        }

        nodeToClusterSourcesMap.computeIfAbsent(
                        propName,
                        k -> new HashMap<>())
                .put(nodeName, source);
    }

    private void populateDataProviderFromMaps() {
        final List<ConfigPropertyRow> newRows = dataProvider.getList()
                .stream()
                .map(row -> {
                    final String effectiveValueStr;
                    final Set<String> effectiveValues = propertyToUniqueEffectiveValuesMap.get(row.getNameAsString());

                    if (effectiveValues == null) {
                        effectiveValueStr = NODES_UNAVAILABLE_MSG;
                    } else {
                        if (effectiveValues.size() <= 1) {
                            effectiveValueStr = row.getEffectiveValueAsString();
                        } else {
//                            GWT.log(row.configProperty.getNameAsString() + effectiveValues);
                            effectiveValueStr = MULTIPLE_VALUES_MSG;
                        }
                    }

                    final String sourceStr;
                    final Set<String> sources = propertyToUniqueSourcesMap.get(row.getNameAsString());
                    if (sources == null) {
                        sourceStr = NODES_UNAVAILABLE_SHORT_MSG;
                    } else {
                        if (sources.size() <= 1) {
                            sourceStr = row.getSourceAsString();
                        } else {
                            sourceStr = MULTIPLE_SOURCES_MSG;
                        }
                    }

                    return new ConfigPropertyRow(row.getConfigProperty(), effectiveValueStr, sourceStr);
                })
                .collect(Collectors.toList());
        // We are not changing the total so re-use the existing one
        dataProvider.setPartialList(newRows, dataGrid.getRowCount());
    }

    private void updatePropertyKeyedMaps() {
        // Walk the node keyed maps to rebuild the property keyed maps
        // to build a picture of all values over the cluster for each prop
        propertyToUniqueEffectiveValuesMap = nodeToClusterEffectiveValuesMap.entrySet()
                .stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry ->
                                new HashSet<>(entry.getValue().values())
                ));

        propertyToUniqueSourcesMap = nodeToClusterSourcesMap.entrySet()
                .stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry ->
                                new HashSet<>(entry.getValue().values())
                ));

        populateDataProviderFromMaps();

        // Set errors.
        final String errors = unreachableNodes
                .stream()
                .sorted(Comparator.naturalOrder())
                .collect(Collectors.joining("\n"));
        ErrorEvent.fire(this, errors);
    }

    private void initColumns() {
        // Name.
        dataGrid.addResizableColumn(
                DataGridUtil.copyTextColumnBuilder((ConfigPropertyRow row) ->
                                row.getNameAsString(), getEventBus())
                        .withSorting(GlobalConfigResource.FIELD_DEF_NAME.getDisplayName())
                        .build(),
                GlobalConfigResource.FIELD_DEF_NAME.getDisplayName(),
                450);

        // Effective Value
        dataGrid.addResizableColumn(
                DataGridUtil.htmlColumnBuilder(
                                DataGridUtil.highlightedCellExtractor(
                                        ConfigPropertyRow::getEffectiveValueAsString,
                                        (ConfigPropertyRow row) ->
                                                MULTIPLE_VALUES_MSG.equals(row.getEffectiveValueAsString()),
                                        ERROR_CSS_COLOUR))
                        .withSorting(GlobalConfigResource.FIELD_DEF_VALUE.getDisplayName())
                        .build(),
                GlobalConfigResource.FIELD_DEF_VALUE.getDisplayName(),
                300);

        // Source
        dataGrid.addResizableColumn(
                DataGridUtil.htmlColumnBuilder(
                                DataGridUtil.highlightedCellExtractor(
                                        ConfigPropertyRow::getSourceAsString,
                                        (ConfigPropertyRow row) ->
                                                MULTIPLE_SOURCES_MSG.equals(row.getSourceAsString()),
                                        ERROR_CSS_COLOUR))
                        .withSorting(GlobalConfigResource.FIELD_DEF_SOURCE.getDisplayName())
                        .build(),
                GlobalConfigResource.FIELD_DEF_SOURCE.getDisplayName(),
                75);

        // Description
        dataGrid.addAutoResizableColumn(
                DataGridUtil.htmlColumnBuilder(
                                (ConfigPropertyRow row) ->
                                        SafeHtmlUtils.fromString(row.getDescription()))
                        .build(),
                GlobalConfigResource.FIELD_DEF_DESCRIPTION.getDisplayName(),
                750);

        DataGridUtil.addEndColumn(dataGrid);
    }

    public ButtonView addButton(final Preset preset) {
        return getView().addButton(preset);
    }

    @Override
    protected void onReveal() {
        super.onReveal();
        refresh();
    }

    public void refresh() {
        dataProvider.refresh(false);
    }

    public ConfigProperty getSelectedItem() {
        return selectionModel.getSelected().getConfigProperty();
    }

    void setPartialName(final String partialName) {
        nameFilterTimer.setName(partialName);
        nameFilterTimer.cancel();
        nameFilterTimer.schedule(400);
    }

    void clearFilter() {
        this.criteria.setQuickFilterInput(null);

        if (!(lastNodeName == null || lastNodeName.isEmpty())) {
            refresh();
        }
        lastNodeName = null;
    }

    private void showError(final Throwable throwable, final String message) {
        AlertEvent.fireError(
                ManageGlobalPropertyListPresenter.this,
                message + " - " + throwable.getMessage(),
                null,
                null);
    }

    @Override
    public void onColumnSort(final ColumnSortEvent event) {
        // TODO implement sorting for Name and Source
    }

    public HandlerRegistration addErrorHandler(final ErrorEvent.Handler handler) {
        return this.addHandlerToSource(ErrorEvent.getType(), handler);
    }

    public MultiSelectionModelImpl<ConfigPropertyRow> getSelectionModel() {
        return selectionModel;
    }

    public static class ConfigPropertyRow {

        private final ConfigProperty configProperty;
        private final String effectiveValue;
        private final String source;

        public ConfigPropertyRow(final ConfigProperty configProperty,
                                 final String effectiveValue,
                                 final String source) {
            this.configProperty = configProperty;
            this.effectiveValue = effectiveValue;
            this.source = source;
        }

        public ConfigPropertyRow(final ConfigProperty configProperty) {
            this.configProperty = configProperty;
            this.effectiveValue = null;
            this.source = null;
        }

        public ConfigProperty getConfigProperty() {
            return configProperty;
        }

        public String getEffectiveValueAsString() {
            return effectiveValue != null
                    ? effectiveValue
                    : configProperty.getEffectiveValueMasked().orElse(null);
        }

        public String getSourceAsString() {
            return source != null
                    ? source
                    : configProperty.getSource().getName();
        }

        public String getNameAsString() {
            return configProperty.getNameAsString();
        }

        public String getDescription() {
            return configProperty.getDescription();
        }
    }

    private class NameFilterTimer extends Timer {

        private String name;

        @Override
        public void run() {
            String filter = name;
            if (filter != null) {
                filter = filter.trim();
                if (filter.length() == 0) {
                    filter = null;
                }
            }

            if (!Objects.equals(filter, criteria.getQuickFilterInput())) {
                criteria.setQuickFilterInput(filter);
                // Need to reset the range else the name criteria can push us outside the page we are on
                final Range range = dataGrid.getVisibleRange();
                dataGrid.setVisibleRange(0, range.getLength());
                refresh();
            }
        }

        public void setName(final String name) {
            this.name = name;
        }
    }
}
