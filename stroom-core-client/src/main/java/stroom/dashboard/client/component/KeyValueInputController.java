/*
 * Copyright 2022 Crown Copyright
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

import stroom.dashboard.client.main.PaneRegistry.ComponentType;
import stroom.dashboard.client.main.PaneRegistry.ComponentUse;
import stroom.dashboard.shared.ComponentConfig;
import stroom.dashboard.shared.ComponentSettings;
import stroom.dashboard.shared.KeyValueInputComponentSettings;
import stroom.query.api.Param;
import stroom.query.api.ParamUtil;
import stroom.query.client.result.table.HasParams;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.List;

/// A dashboard's Key/Value Input pane ([KeyValueInput]): the parameters typed are query parameters
/// of the dashboard.
public class KeyValueInputController extends DashboardPaneController implements HasParams {

    /// The tab type of the pane.
    public static final String TAB_TYPE = "key-value-input-component";
    /// The pane's type, as the dashboard's Add menu offers it.
    public static final ComponentType TYPE = new ComponentType(1,
            "key-value-input",
            "Key/Value Input",
            ComponentUse.INPUT);

    private final KeyValueInput view;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus                   The application's event bus.
    /// @param view                       The content.
    /// @param settingsControllerProvider Makes the pane's settings dialog.
    @Inject
    public KeyValueInputController(final EventBus eventBus,
                                   final KeyValueInput view,
                                   final Provider<KeyValueInputSettingsController> settingsControllerProvider) {
        super(eventBus, settingsControllerProvider);
        this.view = view;
        initWidget(view);
        view.setOnChange(this::onValueChanged);
    }

    private void onValueChanged(final String value) {
        setSettings(getKeyValueInputSettings().copy().text(value).build());
        onChange();
    }

    @Override
    public List<Param> getParams() {
        return ParamUtil.parse(view.getValue());
    }

    @Override
    public void read(final ComponentConfig componentConfig) {
        super.read(componentConfig);
        final ComponentSettings settings = componentConfig.getSettings();
        if (!(settings instanceof KeyValueInputComponentSettings)) {
            setSettings(createSettings());
        }
        view.setValue(getKeyValueInputSettings().getText());
        // Named for the component, so a rename renames it too
        view.setAccessibleName(getComponentConfig().getName());
    }

    private KeyValueInputComponentSettings getKeyValueInputSettings() {
        return (KeyValueInputComponentSettings) getSettings();
    }

    private KeyValueInputComponentSettings createSettings() {
        return KeyValueInputComponentSettings.builder().build();
    }

    @Override
    public void link() {
    }

    @Override
    protected void changeSettings() {
        // Refreshes the pane's tab, so a rename shows, and marks the dashboard changed
        super.changeSettings();
        view.setAccessibleName(getComponentConfig().getName());
    }

    @Override
    public ComponentType getComponentType() {
        return TYPE;
    }

    /// @param value The parameters to show, e.g. a legacy dashboard's.
    public void setValue(final String value) {
        view.setValue(value);
    }

    @Override
    public String getType() {
        return TAB_TYPE;
    }
}
