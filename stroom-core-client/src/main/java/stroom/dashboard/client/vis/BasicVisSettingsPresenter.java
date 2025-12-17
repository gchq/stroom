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

package stroom.dashboard.client.vis;

import stroom.dashboard.client.main.BasicSettingsTabPresenter;
import stroom.dashboard.client.main.BasicSettingsView;
import stroom.dashboard.client.main.Component;
import stroom.dashboard.client.main.SettingsPresenter;
import stroom.dashboard.client.table.TablePresenter;
import stroom.dashboard.shared.ComponentConfig;
import stroom.dashboard.shared.VisComponentSettings;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.explorer.client.presenter.DocSelectionBoxPresenter;
import stroom.preferences.client.UserPreferencesManager;
import stroom.query.api.Column;
import stroom.security.shared.DocumentPermission;
import stroom.util.client.JSONUtil;
import stroom.visualisation.shared.VisualisationDoc;
import stroom.visualisation.shared.VisualisationResource;
import stroom.widget.tab.client.presenter.TabData;

import com.google.gwt.core.client.GWT;
import com.google.gwt.json.client.JSONArray;
import com.google.gwt.json.client.JSONObject;
import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.View;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class BasicVisSettingsPresenter extends BasicSettingsTabPresenter<BasicVisSettingsPresenter.BasicVisSettingsView>
        implements BasicVisSettingsUiHandlers, Focus {

    private static final VisualisationResource VISUALISATION_RESOURCE = GWT.create(VisualisationResource.class);

    private final DocSelectionBoxPresenter visualisationPresenter;
    private final RestFactory restFactory;
    private final UserPreferencesManager userPreferencesManager;
    private final Map<TabData, DynamicSettingsPane> dynamicSettingsMap = new HashMap<>();
    private SettingsPresenter settingsPresenter;
    private DocRef currentVisualisation;
    private JSONObject dynamicSettings;
    private List<String> fieldNames;

    @Inject
    public BasicVisSettingsPresenter(final EventBus eventBus,
                                     final BasicVisSettingsView view,
                                     final DocSelectionBoxPresenter visualisationPresenter,
                                     final RestFactory restFactory,
                                     final UserPreferencesManager userPreferencesManager) {
        super(eventBus, view);
        this.visualisationPresenter = visualisationPresenter;
        this.restFactory = restFactory;
        this.userPreferencesManager = userPreferencesManager;
        view.setUiHandlers(this);

        visualisationPresenter.setIncludedTypes(VisualisationDoc.TYPE);
        visualisationPresenter.setRequiredPermissions(DocumentPermission.USE);

        view.setVisualisationView(visualisationPresenter.getView());
    }

    @Override
    public void focus() {
        getView().focus();
    }

    @Override
    protected void onBind() {
        registerHandler(visualisationPresenter.addDataSelectionHandler(event -> {
            if (!Objects.equals(currentVisualisation, visualisationPresenter.getSelectedEntityReference())) {
                writeDynamicSettings(dynamicSettings);
                loadVisualisation(visualisationPresenter.getSelectedEntityReference(), dynamicSettings);
            }
        }));
    }

    @Override
    public void onTableChange() {
        updateFieldNames(getView().getTable());
    }

    private void updateFieldNames(final Component component) {
        fieldNames = new ArrayList<>();
        if (component instanceof final TablePresenter tablePresenter) {
            final List<Column> columns = tablePresenter.getTableComponentSettings().getColumns();
            if (columns != null && !columns.isEmpty()) {
                for (final Column column : columns) {
                    if (!fieldNames.contains(column.getName())) {
                        fieldNames.add(column.getName());
                    }
                }
            }
        }
        fieldNames.sort(Comparator.naturalOrder());
        fieldNames.add(0, "");

        for (final DynamicSettingsPane dynamicSettings : dynamicSettingsMap.values()) {
            dynamicSettings.setFieldNames(fieldNames);
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
                    .taskMonitorFactory(this)
                    .exec();
        }
    }

    private void readSettings(final JSONObject possibleSettings, final JSONObject dynamicSettings) {
        clearTabs();

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
                final DynamicSettingsPane dynamicSettingsPane = new DynamicSettingsPane(userPreferencesManager.isUtc());
                final TabData t = settingsPresenter.addTab(name, dynamicSettingsPane);
                dynamicSettingsMap.put(t, dynamicSettingsPane);

                dynamicSettingsPane.addControls(controls);
                dynamicSettingsPane.setFieldNames(fieldNames);
            }
        }
    }

    private void clearTabs() {
        for (final TabData tab : dynamicSettingsMap.keySet()) {
            settingsPresenter.removeTab(tab);
        }
        dynamicSettingsMap.clear();
    }

    @Override
    public void read(final ComponentConfig componentConfig) {
        super.read(componentConfig);

        final List<Component> list = getDashboardContext()
                .getComponents().getSortedComponentsByType(TablePresenter.TYPE.getId());
        getView().setTableList(list);

        final VisComponentSettings settings = (VisComponentSettings) componentConfig.getSettings();
        getView().setTable(getDashboardContext().getComponents().get(settings.getTableId()));
        updateFieldNames(getView().getTable());

        dynamicSettings = JSONUtil.getObject(JSONUtil.parse(settings.getJson()));
        if (dynamicSettings == null) {
            dynamicSettings = new JSONObject();
        }

        visualisationPresenter.setSelectedEntityReference(settings.getVisualisation(), true);
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
        final Component table = getView().getTable();
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
        return visualisationPresenter.getSelectedEntityReference();
    }

    public void setSettingsPresenter(final SettingsPresenter settingsPresenter) {
        this.settingsPresenter = settingsPresenter;
    }


    // --------------------------------------------------------------------------------


    public interface BasicVisSettingsView
            extends BasicSettingsView, HasUiHandlers<BasicVisSettingsUiHandlers> {

        void setTableList(List<Component> tableList);

        Component getTable();

        void setTable(Component table);

        void setVisualisationView(View view);
    }
}
