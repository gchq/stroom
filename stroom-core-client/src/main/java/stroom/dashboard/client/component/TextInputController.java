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

import stroom.dashboard.client.main.HasParams;
import stroom.dashboard.client.main.PaneRegistry.ComponentType;
import stroom.dashboard.client.main.PaneRegistry.ComponentUse;
import stroom.dashboard.shared.ComponentConfig;
import stroom.dashboard.shared.ComponentSettings;
import stroom.dashboard.shared.TextInputComponentSettings;
import stroom.query.api.Param;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.List;

/// A dashboard's Text Input pane ([TextInput]): its text, with the key from its settings, is a
/// query parameter of the dashboard.
public class TextInputController extends DashboardPaneController implements HasParams {

    /// The tab type of the pane.
    public static final String TAB_TYPE = "TextInput";
    /// The pane's type, as the dashboard's Add menu offers it.
    public static final ComponentType TYPE =
            new ComponentType(1,
                    "text-input",
                    "Text Input",
                    ComponentUse.INPUT);

    private final TextInput view;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus                   The application's event bus.
    /// @param view                       The content.
    /// @param settingsControllerProvider Makes the pane's settings dialog.
    @Inject
    public TextInputController(final EventBus eventBus,
                               final TextInput view,
                               final Provider<TextInputSettingsController> settingsControllerProvider) {
        super(eventBus, settingsControllerProvider);
        this.view = view;
        initWidget(view);
        view.setOnChange(this::onValueChanged);
    }

    private void onValueChanged(final String value) {
        setSettings(getTextInputSettings().copy().value(value).build());
        onChange();
    }

    @Override
    public List<Param> getParams() {
        final List<Param> list = new ArrayList<>();
        final String key = getTextInputSettings().getKey();
        final String value = view.getValue();
        if (key != null && key.trim().length() > 0 && value != null && value.trim().length() > 0) {
            final Param param = new Param(key.trim(), value.trim());
            list.add(param);
        }
        return list;
    }

    @Override
    public void read(final ComponentConfig componentConfig) {
        super.read(componentConfig);

        final ComponentSettings settings = componentConfig.getSettings();
        if (!(settings instanceof TextInputComponentSettings)) {
            setSettings(createSettings());
        }

        update(getTextInputSettings());
    }

    private void update(final TextInputComponentSettings settings) {
        // Named for the component (e.g. "Feed"), so a rename renames it too
        view.setAccessibleName(getComponentConfig().getName());
        view.setValue(settings.getValue());
    }

    private TextInputComponentSettings getTextInputSettings() {
        return (TextInputComponentSettings) getSettings();
    }

    private TextInputComponentSettings createSettings() {
        return TextInputComponentSettings.builder().build();
    }

    @Override
    public void link() {
    }

    @Override
    protected void changeSettings() {
        super.changeSettings();
        update(getTextInputSettings());
    }

    @Override
    public ComponentType getComponentType() {
        return TYPE;
    }

    @Override
    public String getType() {
        return TAB_TYPE;
    }
}
