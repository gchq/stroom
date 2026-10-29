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

import stroom.alert.client.event.AlertEvent;
import stroom.config.global.client.ListDataProvider;
import stroom.config.global.shared.ConfigProperty;
import stroom.config.global.shared.GlobalConfigCriteria;
import stroom.config.global.shared.GlobalConfigResource;
import stroom.config.global.shared.ListConfigResponse;
import stroom.data.client.CriteriaUtil;
import stroom.dispatch.client.RestFactory;
import stroom.node.client.NodeClient;
import stroom.util.client.DelayedUpdate;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PageRequest;
import stroom.util.shared.TokenError;
import stroom.widget.button.client.Toolbar;
import stroom.widget.component.client.Controller;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.Timer;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/// The global properties ([ManageGlobalPropertyList]) as one node has them, then each node's values
/// and sources fetched to show where they differ across the cluster; filtered by name.
public class ManageGlobalPropertyListController extends Controller {

    private static final String NODES_UNAVAILABLE_MSG = "[Error getting values]";
    private static final String NODES_UNAVAILABLE_SHORT_MSG = "[Error]";
    private static final String MULTIPLE_VALUES_MSG = ManageGlobalPropertyList.MULTIPLE_VALUES_MSG;
    private static final String MULTIPLE_SOURCES_MSG = ManageGlobalPropertyList.MULTIPLE_SOURCES_MSG;

    // This is the delay between getting the list of props and hitting all the other nodes
    // to get their specific values.  Too low and we pepper the nodes for each key press of the filter.
    // Too high and there is too big a delay in showing props with mixed source
    private static final int REFRESH_ALL_NODES_TIMER_DELAY_MS = 500;

    private static final int UPDATE_MAPS_TIMER_DELAY_MS = 50;

    private static final GlobalConfigResource GLOBAL_CONFIG_RESOURCE_RESOURCE = GWT.create(GlobalConfigResource.class);

    private final ManageGlobalPropertyList view;
    private final ListDataProvider<ConfigPropertyRow> dataProvider;
    private final RestFactory restFactory;
    private final NodeClient nodeClient;
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
    private Consumer<String> filterErrorConsumer;

    private final DelayedUpdate updateChildMapsTimer = new DelayedUpdate(UPDATE_MAPS_TIMER_DELAY_MS,
            this::updatePropertyKeyedMaps);

    private final NameFilterTimer nameFilterTimer = new NameFilterTimer();

    private final GlobalConfigCriteria criteria = new GlobalConfigCriteria(
            PageRequest.unlimited(),
            new ArrayList<>(),
            null);

    private Consumer<String> onErrors = errors -> {
    };

    /// Made by GIN, with its content.
    ///
    /// @param eventBus    The application's event bus.
    /// @param view        The content.
    /// @param restFactory Fetches the properties.
    /// @param nodeClient  Lists the nodes.
    @Inject
    public ManageGlobalPropertyListController(final EventBus eventBus,
                                              final ManageGlobalPropertyList view,
                                              final RestFactory restFactory,
                                              final NodeClient nodeClient) {
        super(eventBus);
        this.view = view;
        this.restFactory = restFactory;
        this.nodeClient = nodeClient;
        initWidget(view);

        dataProvider = new ListDataProvider<>();
        view.setDataProvider(dataProvider);
        dataProvider.setListUpdater(this::refreshTable);

        view.setOnSortChange(this::refresh);
        // Timers stop with the list
        onDispose(refreshAllNodesTimer::reset);
        onDispose(updateChildMapsTimer::reset);
        onDispose(nameFilterTimer::cancel);
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
        CriteriaUtil.setSortList(criteria, view.getSortList());

        resetLocalState();

        restFactory
                .create(GLOBAL_CONFIG_RESOURCE_RESOURCE)
                .method(res -> res.list(criteria))
                .onSuccess(listConfigResponse -> {
                    if (filterErrorConsumer != null) {
                        filterErrorConsumer.accept(
                                NullSafe.get(listConfigResponse.getFilterError(), TokenError::getText));
                    }

                    lastNodeName = listConfigResponse.getNodeName();

                    // Build the table based on what we know from one node
                    final List<ConfigPropertyRow> rows = listConfigResponse.getValues().stream()
                            .map(ConfigPropertyRow::new)
                            .collect(Collectors.toList());

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
                                ManageGlobalPropertyListController.this,
                                caught.getMessage(),
                                null))
                .owner(this)
                .taskMonitorFactory(view.getTaskMonitor())
                .exec();
    }

    private void refreshPropertiesForAllNodes() {
        // Only care about enabled nodes
        unreachableNodes.clear();
        // No point hitting the node that we hit at the top level again as we already have its data
        nodeClient.listEnabledNodes(
                nodeNames ->
                        nodeNames
                                .stream()
                                .filter(nodeName -> !nodeName.equals(lastNodeName))
                                .forEach(this::refreshPropertiesForNode),
                error ->
                        showError(
                                error.getException(),
                                "Error getting list of all nodes. Only properties for one node will be shown"),
                view.getTaskMonitor());
    }

    private void refreshPropertiesForNode(final String nodeName) {
        criteria.setPageRequest(new PageRequest(
                view.getVisibleRange().getStart(),
                view.getVisibleRange().getLength()));

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
                .owner(this)
                .taskMonitorFactory(view.getTaskMonitor())
                .exec();
    }

    private void handleNodeResponse(final ListConfigResponse listConfigResponse) {
        unreachableNodes.remove(listConfigResponse.getNodeName());

        // Add the node's result to our maps
        listConfigResponse.getValues().forEach(configProperty -> {
            final String effectiveValue = configProperty.getEffectiveValue().orElse(null);
            final String source = configProperty.getSource().getName();

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
        dataProvider.setPartialList(newRows, view.getRowCount());
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
        onErrors.accept(errors);
    }

    /// The toolbar beside the list's pager, for the screen's own buttons.
    ///
    /// @return The toolbar.
    public Toolbar getToolbar() {
        return view.getToolbar();
    }

    /// @param filterErrorConsumer Given why the filter was rejected after each fetch, or `null` if
    ///                            it wasn't.
    public void setFilterErrorConsumer(final Consumer<String> filterErrorConsumer) {
        this.filterErrorConsumer = filterErrorConsumer;
    }

    /// Fetches the properties again.
    public void refresh() {
        dataProvider.refresh(false);
    }

    /// @return The property selected.
    public ConfigProperty getSelectedItem() {
        return view.getSelectionModel().getSelected().getConfigProperty();
    }

    /// Filters the properties by name, after a pause for typing.
    ///
    /// @param partialName Part of a property's name.
    public void setPartialName(final String partialName) {
        nameFilterTimer.setName(partialName);
        nameFilterTimer.cancel();
        nameFilterTimer.schedule(400);
    }

    /// Shows every property again.
    public void clearFilter() {
        this.criteria.setQuickFilter(null);

        if (!(lastNodeName == null || lastNodeName.isEmpty())) {
            refresh();
        }
        lastNodeName = null;
    }

    private void showError(final Throwable throwable, final String message) {
        AlertEvent.fireError(
                ManageGlobalPropertyListController.this,
                message + " - " + throwable.getMessage(),
                null,
                null);
    }

    /// @param onErrors What to run with the errors (the nodes that couldn't be reached, one per
    ///                 line) once the properties are fetched.
    public void setOnErrors(final Consumer<String> onErrors) {
        this.onErrors = onErrors;
    }

    /// @return The selection, one property at most.
    public MultiSelectionModelImpl<ConfigPropertyRow> getSelectionModel() {
        return view.getSelectionModel();
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

            if (!Objects.equals(filter, criteria.getQuickFilter())) {
                criteria.setQuickFilter(filter);
                // Need to reset the range else the name criteria can push us outside the page we are on
                view.resetRange();
                refresh();
            }
        }

        public void setName(final String name) {
            this.name = name;
        }
    }
}
