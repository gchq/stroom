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

package stroom.config.global.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.config.global.shared.ConfigProperty;
import stroom.config.global.shared.GlobalConfigCriteria;
import stroom.config.global.shared.GlobalConfigResource;
import stroom.config.global.shared.ListConfigResponse;
import stroom.data.grid.client.DataGridView;
import stroom.data.grid.client.DataGridViewImpl;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.node.client.NodeCache;
import stroom.svg.client.SvgPreset;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.PageRequest;
import stroom.widget.button.client.ButtonView;

import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.user.cellview.client.ColumnSortEvent;
import com.google.gwt.user.client.Timer;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class ManageGlobalPropertyListPresenter
        extends MyPresenterWidget<DataGridView<ManageGlobalPropertyListPresenter.ConfigPropertyRow>>
        implements ColumnSortEvent.Handler {

    private static final String NODES_UNAVAILABLE_MSG = "[Error getting values from some nodes]";
    private static final String NODES_UNAVAILABLE_SHORT_MSG = "[Error]";
    private static final String MULTIPLE_VALUES_MSG = "[Multiple effective values exist in the cluster]";
    private static final String MULTIPLE_SOURCES_MSG = "[Multiple]";
    private static final String ERROR_CSS_COLOUR = "red";
    private static final int TIMER_DELAY_MS = 50;

    private static final GlobalConfigResource GLOBAL_CONFIG_RESOURCE_RESOURCE = GWT.create(GlobalConfigResource.class);
    private static final String ERROR_VALUE = "[[ERROR]]";

    private final ListDataProvider<ConfigPropertyRow> dataProvider;
    private final RestFactory restFactory;
    private final NodeCache nodeCache;

    // propName => (node => effectiveValue)
    private final Map<String, Map<String, String>> nodeToClusterEffectiveValuesMap = new HashMap<>();
    // propName => (effectiveValues)
    private Map<String, Set<String>> propertyToUniqueEffectiveValuesMap = new HashMap<>();
    // propName => (node => source)
    private final Map<String, Map<String, String>> nodeToClusterSourcesMap = new HashMap<>();
    // propName => (sources)
    private Map<String, Set<String>> propertyToUniqueSourcesMap = new HashMap<>();

    private final Timer refreshAllNodesTimer = new Timer() {
        @Override
        public void run() {
            refreshPropertiesForAllNodes();
        }
    };

    private final Timer updateChildMapsTimer = new Timer() {
        @Override
        public void run() {
            updatePropertyKeyedMaps();
        }
    };

    private final NameFilterTimer nameFilterTimer = new NameFilterTimer();

    private final GlobalConfigCriteria criteria = new GlobalConfigCriteria(
            new PageRequest(0L, Integer.MAX_VALUE),
            new ArrayList<>(),
            null);

    @Inject
    public ManageGlobalPropertyListPresenter(final EventBus eventBus,
                                             final RestFactory restFactory,
                                             final NodeCache nodeCache) {
        super(eventBus, new DataGridViewImpl<>(true));
        this.restFactory = restFactory;
        this.nodeCache = nodeCache;

        initColumns();

        dataProvider = new ListDataProvider<>();
        dataProvider.addDataDisplay(getView().getDataDisplay());
        dataProvider.setListUpdater(this::refreshTable);
    }

    private void refreshTable(final Range range) {

        criteria.setPageRequest(new PageRequest((long) range.getStart(), range.getLength()));

        final Rest<ListConfigResponse> rest = restFactory.create();
        rest
                .onSuccess(listConfigResponse -> {

                    // Build the table based on what we know from one node
                    final List<ConfigPropertyRow> rows = listConfigResponse.getValues().stream()
                            .map(ConfigPropertyRow::new)
                            .collect(Collectors.toList());

//                GWT.log("Offset: " + listConfigResponse.getPageResponse().getOffset()
//                    + " total: " + listConfigResponse.getPageResponse().getTotal());

                    dataProvider.setPartialList(rows, listConfigResponse.getPageResponse().getTotal().intValue());

                    // now we have the props from one node, go off and get all the values/sources
                    // from all the nodes. Use a timer to delay it a bit
                    if (!refreshAllNodesTimer.isRunning()) {
                        refreshAllNodesTimer.schedule(TIMER_DELAY_MS);
                    }
                })
                .onFailure(throwable -> {
                    // TODO
                })
                .call(GLOBAL_CONFIG_RESOURCE_RESOURCE)
                .list(criteria);
    }

    private void refreshPropertiesForAllNodes() {
        // Only care about enable nodes
        nodeCache.listEnabledNodes(
                nodeNames -> nodeNames.forEach(this::refreshPropertiesForNode),
                throwable -> showError(throwable, "Error getting list of all nodes. Only properties for one node will be shown"));
    }

    private void refreshPropertiesForNode(final String nodeName) {
        final Rest<ListConfigResponse> listPropertiesRest = restFactory.create();

        criteria.setPageRequest(new PageRequest(
                (long) getView().getVisibleRange().getStart(),
                getView().getVisibleRange().getLength()));

        listPropertiesRest
                .onSuccess(listConfigResponse -> {

                    // Add the node's result to our maps
                    listConfigResponse.getValues().forEach(configProperty -> {
                        final String effectiveValue = configProperty.getEffectiveValue().orElse(null);
                        final String source = configProperty.getSource().getName();

                        updateNodeKeyedMaps(nodeName, configProperty.getNameAsString(), effectiveValue, source);

                        // kick off the delayed action to update the maps keyed on prop name,
                        // unless another node has already kicked it off
                        if (!updateChildMapsTimer.isRunning()) {
                            updateChildMapsTimer.schedule(TIMER_DELAY_MS);
                        }
                    });
                })
                .onFailure(
                        throwable -> nodeToClusterEffectiveValuesMap.keySet().forEach(
                                propName -> updateNodeKeyedMaps(nodeName, propName, ERROR_VALUE, ERROR_VALUE)))
                .call(GLOBAL_CONFIG_RESOURCE_RESOURCE)
                .listByNode(nodeName, criteria);
    }

    private void updateNodeKeyedMaps(final String nodeName,
                                     final String propName,
                                     final String effectiveValue,
                                     final String source) {

        nodeToClusterEffectiveValuesMap.computeIfAbsent(
                propName,
                k -> new HashMap<>())
                .put(nodeName, effectiveValue);

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

                    if (effectiveValues == null || effectiveValues.contains(ERROR_VALUE)) {
                        effectiveValueStr = NODES_UNAVAILABLE_MSG;
                    } else {
                        if (effectiveValues.size() <= 1) {
                            effectiveValueStr = row.getEffectiveValueAsString();
                        } else {
                            effectiveValueStr = MULTIPLE_VALUES_MSG;
                        }
                    }

                    final String sourceStr;
                    final Set<String> sources = propertyToUniqueSourcesMap.get(row.getNameAsString());
                    if (sources == null || sources.contains(ERROR_VALUE)) {
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
        dataProvider.setPartialList(newRows, getView().getDataDisplay().getRowCount());
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
    }

    private void initColumns() {
        // Name.
        getView().addResizableColumn(
                DataGridUtil.htmlColumnBuilder(ConfigPropertyRow::getNameAsString, SafeHtmlUtils::fromString)
                        .topAligned()
                        .withSorting(GlobalConfigResource.FIELD_DEF_NAME.getDisplayName())
                        .withStyleName(getView().getResources().dataGridStyle().dataGridCellVerticalTop())
                        .build(),
                GlobalConfigResource.FIELD_DEF_NAME.getDisplayName(),
                450);

        // Effective Value
        getView().addResizableColumn(
                DataGridUtil.htmlColumnBuilder(
                        DataGridUtil.highlightedCellExtractor(
                                ConfigPropertyRow::getEffectiveValueAsString,
                                (ConfigPropertyRow row) -> MULTIPLE_VALUES_MSG.equals(row.getEffectiveValueAsString()),
                                ERROR_CSS_COLOUR))
                        .withSorting(GlobalConfigResource.FIELD_DEF_EFFECTIVE_VALUE.getDisplayName())
                        .withStyleName(getView().getResources().dataGridStyle().dataGridCellVerticalTop())
                        .build(),
                GlobalConfigResource.FIELD_DEF_EFFECTIVE_VALUE.getDisplayName(),
                300);

        // Source
        getView().addResizableColumn(
                DataGridUtil.htmlColumnBuilder(
                        DataGridUtil.highlightedCellExtractor(
                                ConfigPropertyRow::getSourceAsString,
                                (ConfigPropertyRow row) -> MULTIPLE_SOURCES_MSG.equals(row.getSourceAsString()),
                                ERROR_CSS_COLOUR))
                        .withSorting(GlobalConfigResource.FIELD_DEF_SOURCE.getDisplayName())
                        .withStyleName(getView().getResources().dataGridStyle().dataGridCellVerticalTop())
                        .build(),
                GlobalConfigResource.FIELD_DEF_SOURCE.getDisplayName(),
                75);

        // Description
        getView().addResizableColumn(
                DataGridUtil.htmlColumnBuilder(ConfigPropertyRow::getDescription, SafeHtmlUtils::fromString)
                        .topAligned()
                        .withStyleName(getView().getResources().dataGridStyle().dataGridCellWrapText())
                        .withStyleName(getView().getResources().dataGridStyle().dataGridCellVerticalTop())
                        .build(),
                GlobalConfigResource.FIELD_DEF_DESCRIPTION.getDisplayName(),
                750);

        DataGridUtil.addEndColumn(getView());
        DataGridUtil.addColumnSortHandler(getView(), criteria, this::refresh);
    }

//    private Column<ConfigPropertyRow, String> buildDescriptionColumn() {
//        return new Column<ConfigPropertyRow, String>(new TextCell()) {
//            @Override
//            public String getValue(final ConfigPropertyRow row) {
//                if (row == null) {
//                    return null;
//                }
//                return row.getDescription();
//            }
//
//            @Override
//            public String getCellStyleNames(Cell.Context context, ConfigPropertyRow object) {
//                return super.getCellStyleNames(context, object) + " "
//                        + getView().getResources().dataGridStyle().dataGridCellWrapText() + " "
//                        + getView().getResources().dataGridStyle().dataGridCellVerticalTop();
//            }
//        };
//    }
//
//    private Column<ConfigPropertyRow, SafeHtml> buildSafeHtmlColumn(final Function<ConfigPropertyRow, SafeHtml> valueFunc) {
//        // TODO use OrderByColumn
//        return new Column<ConfigPropertyRow, SafeHtml>(new SafeHtmlCell()) {
//            @Override
//            public SafeHtml getValue(final ConfigPropertyRow row) {
//                if (row == null) {
//                    return null;
//                }
//                return valueFunc.apply(row);
//            }
//
//            @Override
//            public String getCellStyleNames(Cell.Context context, ConfigPropertyRow object) {
//                return super.getCellStyleNames(context, object) + " "
//                        + getView().getResources().dataGridStyle().dataGridCellVerticalTop();
//            }
//        };
//    }
//
//    private void addColumn(Column<ConfigPropertyRow, ?> column, String name, int width) {
//        column.setVerticalAlignment(HasVerticalAlignment.ALIGN_TOP);
//        getView().addResizableColumn(column, name, width);
//    }

    public ButtonView addButton(final SvgPreset preset) {
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
        return getView().getSelectionModel().getSelected().getConfigProperty();
    }

    void setPartialName(final String partialName) {
        nameFilterTimer.setName(partialName);
        nameFilterTimer.cancel();
        nameFilterTimer.schedule(400);
    }

    void clearFilter() {
        this.criteria.setQuickFilterInput(null);
        refresh();
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
                Range range = getView().getVisibleRange();
                getView().getDataDisplay().setVisibleRange(0, range.getLength());
                refresh();
            }
        }

        public void setName(final String name) {
            this.name = name;
        }
    }
}
