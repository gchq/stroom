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
 */

package stroom.dashboard.client.vis;

import com.google.gwt.json.client.JSONArray;
import com.google.gwt.json.client.JSONObject;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.View;
import stroom.dashboard.client.main.BasicSettingsTabPresenter;
import stroom.dashboard.client.main.Component;
import stroom.dashboard.client.main.SettingsPresenter;
import stroom.dashboard.client.table.TablePresenter;
import stroom.dashboard.shared.ComponentConfig;
import stroom.dashboard.shared.FetchVisualisationAction;
import stroom.dashboard.shared.Field;
import stroom.dashboard.shared.VisComponentSettings;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.explorer.client.presenter.EntityDropDownPresenter;
import stroom.query.api.v1.DocRef;
import stroom.security.shared.DocumentPermissionNames;
import stroom.util.client.JSONUtil;
import stroom.util.shared.EqualsBuilder;
import stroom.util.shared.EqualsUtil;
import stroom.visualisation.shared.Visualisation;
import stroom.widget.tab.client.presenter.TabData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BasicVisSettingsPresenter extends BasicSettingsTabPresenter<BasicVisSettingsPresenter.BasicVisSettingsView>
        implements BasicVisSettingsUiHandlers {
    private final EntityDropDownPresenter visualisationPresenter;
    private final ClientDispatchAsync dispatcher;
    private final Map<TabData, DynamicSettingsPane> dynamicSettingsMap = new HashMap<>();
    private SettingsPresenter settingsPresenter;
    private DocRef currentVisualisation;
    private JSONObject dynamicSettings;
    private List<String> fieldNames;

    @Inject
    public BasicVisSettingsPresenter(final EventBus eventBus, final BasicVisSettingsView view,
                                     final EntityDropDownPresenter visualisationPresenter, final ClientDispatchAsync dispatcher) {
        super(eventBus, view);
        this.visualisationPresenter = visualisationPresenter;
        this.dispatcher = dispatcher;
        view.setUiHandlers(this);

        visualisationPresenter.setIncludedTypes(Visualisation.ENTITY_TYPE);
        visualisationPresenter.setRequiredPermissions(DocumentPermissionNames.USE);

        view.setVisualisationView(visualisationPresenter.getView());
    }

    @Override
    protected void onBind() {
        registerHandler(visualisationPresenter.addDataSelectionHandler(event -> {
            if (!EqualsUtil.isEquals(currentVisualisation, visualisationPresenter.getSelectedEntityReference())) {
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
        fieldNames.add("");

        if (component != null && component instanceof TablePresenter) {
            final TablePresenter tablePresenter = (TablePresenter) component;
            final List<Field> fields = tablePresenter.getSettings().getFields();
            if (fields != null && fields.size() > 0) {
                for (final Field field : fields) {
                    if (field.isVisible()) {
                        fieldNames.add(field.getName());
                    }
                }
            }
        }

        for (final DynamicSettingsPane dynamicSettings : dynamicSettingsMap.values()) {
            dynamicSettings.setFieldNames(fieldNames);
        }
    }

    private void loadVisualisation(final DocRef docRef, final JSONObject dynamicSettings) {
        currentVisualisation = docRef;
        if (docRef != null) {
            final FetchVisualisationAction action = new FetchVisualisationAction(docRef);
            dispatcher.exec(action).onSuccess(result -> {
                String jsonString = "";
                if (result != null && result.getSettings() != null) {
                    jsonString = result.getSettings();
                }
                final JSONObject settings = JSONUtil.getObject(JSONUtil.parse(jsonString));
                readSettings(settings, dynamicSettings);
            });
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
                final DynamicSettingsPane dynamicSettingsPane = new DynamicSettingsPane();
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
    public void read(final ComponentConfig componentData) {
        super.read(componentData);

        final List<Component> list = getComponents().getComponentsByType(TablePresenter.TYPE.getId());
        getView().setTableList(list);

        final VisComponentSettings settings = (VisComponentSettings) componentData.getSettings();
        getView().setTable(getComponents().get(settings.getTableId()));
        updateFieldNames(getView().getTable());

        dynamicSettings = JSONUtil.getObject(JSONUtil.parse(settings.getJSON()));
        if (dynamicSettings == null) {
            dynamicSettings = new JSONObject();
        }

        visualisationPresenter.setSelectedEntityReference(settings.getVisualisation());
        loadVisualisation(settings.getVisualisation(), dynamicSettings);
    }

    private void readDynamicSettings(final JSONObject dynamicSettings) {
        for (final DynamicSettingsPane pane : dynamicSettingsMap.values()) {
            pane.read(dynamicSettings);
        }
    }

    @Override
    public void write(final ComponentConfig componentData) {
        super.write(componentData);

        final VisComponentSettings settings = (VisComponentSettings) componentData.getSettings();

        settings.setTableId(getTableId());
        settings.setVisualisation(getSelectedVisualisation());
        settings.setJSON(getJSON());
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
    public boolean isDirty(final ComponentConfig componentData) {
        if (super.isDirty(componentData)) {
            return true;
        }

        final VisComponentSettings settings = (VisComponentSettings) componentData.getSettings();

        final EqualsBuilder builder = new EqualsBuilder();
        builder.append(settings.getTableId(), getTableId());
        builder.append(settings.getVisualisation(), getSelectedVisualisation());
        builder.append(settings.getJSON(), getJSON());

        return !builder.isEquals();
    }

    private DocRef getSelectedVisualisation() {
        return visualisationPresenter.getSelectedEntityReference();
    }

    public void setSettingsPresenter(final SettingsPresenter settingsPresenter) {
        this.settingsPresenter = settingsPresenter;
    }

    public interface BasicVisSettingsView
            extends BasicSettingsTabPresenter.SettingsView, HasUiHandlers<BasicVisSettingsUiHandlers> {
        void setTableList(List<Component> tableList);

        Component getTable();

        void setTable(Component table);

        void setVisualisationView(View view);
    }
}
