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


import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

/// A Table pane's settings: Basic, Conditional Formatting and Selection Filter.
public class TableSettingsController extends ComponentSettingsDialogController {

    /// Made by GIN, with its content and its tabs.
    ///
    /// @param eventBus        The application's event bus.
    /// @param view            Its component.
    /// @param basicSettings   The basic settings.
    /// @param rules           The rules.
    /// @param selectionFilter The selection filter.
    @Inject
    public TableSettingsController(final EventBus eventBus,
                                   final ComponentSettingsDialog view,
                                   final BasicTableSettingsController basicSettings,
                                   final RulesController rules,
                                   final SelectionHandlersController selectionFilter) {
        super(eventBus, view);
        own(basicSettings);
        own(rules);
        own(selectionFilter);

        selectionFilter.setUseForFilter(true);

        addTab("Basic", basicSettings);
        addTab("Conditional Formatting", rules);
        addTab("Selection Filter", selectionFilter);
    }
}
