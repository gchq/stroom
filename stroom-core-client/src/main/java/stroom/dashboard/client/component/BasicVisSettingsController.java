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

package stroom.dashboard.client.component;

import stroom.dashboard.client.main.DashboardPane;
import stroom.dashboard.client.vis.VisFieldOption;
import stroom.dashboard.client.vis.VisInputChoices;
import stroom.dashboard.shared.ComponentConfig;
import stroom.dashboard.shared.VisComponentSettings;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.explorer.client.component.DocSelectionController;
import stroom.query.api.Column;
import stroom.security.shared.DocumentPermission;
import stroom.util.client.JSONUtil;
import stroom.visualisation.shared.VisInput;
import stroom.visualisation.shared.VisualisationDoc;
import stroom.visualisation.shared.VisualisationResource;
import stroom.widget.component.client.Layer;
import stroom.widget.datepicker.client.component.DateTimeBoxController;
import stroom.widget.tab.client.TabData;

import com.google.gwt.core.client.GWT;
import com.google.gwt.json.client.JSONArray;
import com.google.gwt.json.client.JSONObject;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/// A Visualisation pane's Basic settings tab ([BasicVisSettings]): its name, its table and its
/// visualisation. Choosing a visualisation adds a tab to the settings dialog for each of the
/// visualisation's own tabs of settings (through [#setOnAddTab] and [#setOnRemoveTab]), whose field
/// controls offer the table's columns.
public class BasicVisSettingsController extends BasicSettingsTabController<BasicVisSettings> {

    private static final VisualisationResource VISUALISATION_RESOURCE = GWT.create(VisualisationResource.class);

    private final DocSelectionController visualisation;
    private final RestFactory restFactory;
    private final Provider<DateTimeBoxController> dateTimeBoxControllerProvider;
    private final Map<TabData, DynamicSettingsPane> dynamicSettingsMap = new HashMap<>();
    // What opens the tabs' date dialogs, released with their tabs
    private final List<DateTimeBoxController> dateTimeBoxControllers = new ArrayList<>();
    private BiFunction<String, Layer, TabData> onAddTab = (name, layer) -> null;
    private Consumer<TabData> onRemoveTab = tab -> {
    };
    private DocRef currentVisualisation;
    private JSONObject dynamicSettings;
    // The table's columns, which the field controls can choose
    private List<VisFieldOption> fieldOptions = new ArrayList<>();
    // The visualisation's typed inputs, by id
    private Map<String, VisInput> inputs = new HashMap<>();

    /// Made by GIN, with its content.
    ///
    /// @param eventBus                      The application's event bus.
    /// @param view                          The content.
    /// @param docSelectionProvider          Makes the visualisation's picker.
    /// @param restFactory                   Fetches the visualisation's settings.
    /// @param dateTimeBoxControllerProvider Opens the date dialogs of the visualisation's settings.
    @Inject
    public BasicVisSettingsController(final EventBus eventBus,
                                      final BasicVisSettings view,
                                      final Provider<DocSelectionController> docSelectionProvider,
                                      final RestFactory restFactory,
                                      final Provider<DateTimeBoxController> dateTimeBoxControllerProvider) {
        super(eventBus, view);
        this.visualisation = make(docSelectionProvider);
        visualisation.attach(view.getVisualisationBox());
        this.dateTimeBoxControllerProvider = dateTimeBoxControllerProvider;
        this.restFactory = restFactory;
        view.setOnTableChange(this::onTableChange);

        visualisation.setIncludedTypes(VisualisationDoc.TYPE);
        visualisation.setRequiredPermissions(DocumentPermission.USE);

        own(visualisation.addSelectionListener(event -> {
            if (!Objects.equals(currentVisualisation, visualisation.getSelectedEntityReference())) {
                writeDynamicSettings(dynamicSettings);
                loadVisualisation(visualisation.getSelectedEntityReference(), dynamicSettings);
            }
        }));
    }

    private void onTableChange() {
        updateFieldNames(getView().getTable());
    }

    private void updateFieldNames(final DashboardPane component) {
        final Map<String, VisFieldOption> optionsByName = new HashMap<>();
        if (component instanceof final TableController tableController) {
            final List<Column> columns = tableController.getTableComponentSettings().getColumns();
            if (columns != null) {
                for (final Column column : columns) {
                    optionsByName.putIfAbsent(column.getName(), new VisFieldOption(column.getName(),
                            column.getFormat() == null
                                    ? null
                                    : column.getFormat().getType()));
                }
            }
        }
        fieldOptions = new ArrayList<>(optionsByName.values());
        fieldOptions.sort(Comparator.comparing(VisFieldOption::getName));

        for (final DynamicSettingsPane dynamicSettings : dynamicSettingsMap.values()) {
            dynamicSettings.setFields(fieldOptions);
        }
    }

    private void loadVisualisation(final DocRef docRef, final JSONObject dynamicSettings) {
        currentVisualisation = docRef;
        if (docRef != null) {
            restFactory
                    .create(VISUALISATION_RESOURCE)
                    .method(res -> res.fetch(docRef.getUuid()))
                    .onSuccess(result -> {
                        String jsonString = "";
                        if (result != null && result.getSettings() != null) {
                            jsonString = result.getSettings();
                        }
                        final JSONObject settings = JSONUtil.getObject(JSONUtil.parse(jsonString));
                        readSettings(settings, dynamicSettings);
                    })
                    .owner(this)
                    .taskMonitorFactory(taskMonitor())
                    .exec();
        }
    }

    private void readSettings(final JSONObject possibleSettings, final JSONObject dynamicSettings) {
        clearTabs();

        inputs = VisInputChoices.parseInputs(possibleSettings);
        if (possibleSettings != null) {
            final JSONArray tabs = JSONUtil.getArray(possibleSettings.get("tabs"));
            if (tabs != null) {
                for (int i = 0; i < tabs.size(); i++) {
                    // Add a tab for each item in the tabs array.
                    addTab(JSONUtil.getObject(tabs.get(i)));
                }
            }
        }

        readDynamicSettings(dynamicSettings);
    }

    private void addTab(final JSONObject tab) {
        if (tab != null) {
            final String name = JSONUtil.getString(tab.get("name"));
            final JSONArray controls = JSONUtil.getArray(tab.get("controls"));
            if (controls != null) {
                final DynamicSettingsPane dynamicSettingsPane = new DynamicSettingsPane();
                dynamicSettingsPane.setOnDateTimeBoxAdded(box ->
                        dateTimeBoxControllers.add(make(dateTimeBoxControllerProvider).attach(box)));
                final TabData t = onAddTab.apply(name, dynamicSettingsPane);
                dynamicSettingsMap.put(t, dynamicSettingsPane);

                dynamicSettingsPane.addControls(controls, inputs);
                dynamicSettingsPane.setFields(fieldOptions);
            }
        }
    }

    private void clearTabs() {
        for (final TabData tab : dynamicSettingsMap.keySet()) {
            onRemoveTab.accept(tab);
        }
        dynamicSettingsMap.clear();
        // The visualisation's tabs are made again when it changes, so their controllers go now,
        // rather than with this
        dateTimeBoxControllers.forEach(this::release);
        dateTimeBoxControllers.clear();
    }

    @Override
    public void read(final ComponentConfig componentConfig) {
        super.read(componentConfig);

        final List<DashboardPane> list = getDashboardContext()
                .getComponents().getSortedComponentsByType(TableController.TYPE.getId());
        getView().setTableList(list);

        final VisComponentSettings settings = (VisComponentSettings) componentConfig.getSettings();
        getView().setTable(getDashboardContext().getComponents().get(settings.getTableId()));
        updateFieldNames(getView().getTable());

        dynamicSettings = JSONUtil.getObject(JSONUtil.parse(settings.getJson()));
        if (dynamicSettings == null) {
            dynamicSettings = new JSONObject();
        }

        visualisation.setSelectedEntityReference(settings.getVisualisation(), true);
        loadVisualisation(settings.getVisualisation(), dynamicSettings);
    }

    private void readDynamicSettings(final JSONObject dynamicSettings) {
        for (final DynamicSettingsPane pane : dynamicSettingsMap.values()) {
            pane.read(dynamicSettings);
        }
    }

    @Override
    public ComponentConfig write(final ComponentConfig componentConfig) {
        final ComponentConfig result = super.write(componentConfig);
        final VisComponentSettings oldSettings = (VisComponentSettings) result.getSettings();
        final VisComponentSettings newSettings = writeSettings(oldSettings);
        return result.copy().settings(newSettings).build();
    }

    private VisComponentSettings writeSettings(final VisComponentSettings settings) {
        return settings
                .copy()
                .tableId(getTableId())
                .visualisation(getSelectedVisualisation())
                .json(getJSON())
                .build();
    }

    private String getTableId() {
        final DashboardPane table = getView().getTable();
        if (table == null) {
            return null;
        }

        return table.getId();
    }

    private void writeDynamicSettings(final JSONObject dynamicSettings) {
        for (final DynamicSettingsPane pane : dynamicSettingsMap.values()) {
            pane.write(dynamicSettings);
        }
    }

    private String getJSON() {
        final JSONObject dynamicSettings = new JSONObject();
        writeDynamicSettings(dynamicSettings);
        return dynamicSettings.toString();
    }

    @Override
    public boolean isDirty(final ComponentConfig componentConfig) {
        if (super.isDirty(componentConfig)) {
            return true;
        }

        final VisComponentSettings oldSettings = (VisComponentSettings) componentConfig.getSettings();
        final VisComponentSettings newSettings = writeSettings(oldSettings);

        final boolean equal = Objects.equals(oldSettings.getTableId(), newSettings.getTableId()) &&
                              Objects.equals(oldSettings.getVisualisation(), newSettings.getVisualisation()) &&
                              Objects.equals(oldSettings.getJson(), newSettings.getJson());

        return !equal;
    }

    private DocRef getSelectedVisualisation() {
        return visualisation.getSelectedEntityReference();
    }

    /// @param onAddTab Adds a tab of the visualisation's settings to the settings dialog, given its
    ///                 name and content, and gives the tab.
    public void setOnAddTab(final BiFunction<String, Layer, TabData> onAddTab) {
        this.onAddTab = onAddTab;
    }

    /// @param onRemoveTab Takes a tab of the visualisation's settings off the settings dialog.
    public void setOnRemoveTab(final Consumer<TabData> onRemoveTab) {
        this.onRemoveTab = onRemoveTab;
    }

}
