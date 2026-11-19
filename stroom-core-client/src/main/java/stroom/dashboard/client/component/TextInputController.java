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
import stroom.dashboard.shared.TextInputComponentSettings;
import stroom.dashboard.shared.TextInputFormat;
import stroom.dashboard.shared.TextInputValidator;
import stroom.query.api.Param;
import stroom.query.client.result.table.HasParams;
import stroom.widget.datepicker.client.component.DateTimeBoxController;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.List;

/// A dashboard's Text Input pane ([TextInput]): its text, with the key from its settings, is a
/// query parameter of the dashboard. Its settings can say what the text must be (a number, a date
/// and time, which gets a calendar, or a match of a pattern); text that isn't is marked invalid,
/// kept, and not passed to queries.
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
    /// @param settingsControllerProvider     Makes the pane's settings dialog.
    /// @param dateTimeBoxControllerProvider Makes what opens the date-time box's calendar.
    @Inject
    public TextInputController(final EventBus eventBus,
                               final TextInput view,
                               final Provider<TextInputSettingsController> settingsControllerProvider,
                               final Provider<DateTimeBoxController> dateTimeBoxControllerProvider) {
        super(eventBus, settingsControllerProvider);
        this.view = view;
        initWidget(view);
        make(dateTimeBoxControllerProvider).attach(view.getDateTimeBox());
        view.setOnChange(this::onValueChanged);
        view.setMarkInvalid(this::getInvalidReason);
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
        // Text that isn't what the settings say it must be is not passed on to queries
        if (key != null && key.trim().length() > 0
            && value != null && value.trim().length() > 0
            && getInvalidReason(value) == null) {
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
        view.setDateTime(settings.getFormat() == TextInputFormat.DATE_TIME);
        view.setValue(settings.getValue());
    }

    // What is wrong with the text, as the settings' format says, or null if nothing is
    private String getInvalidReason(final String value) {
        // Nothing is wrong before the settings are read
        if (getComponentConfig() == null
            || !(getSettings() instanceof final TextInputComponentSettings settings)) {
            return null;
        }
        return TextInputValidator.getInvalidReason(settings.getFormat(), settings.getPattern(), value);
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
