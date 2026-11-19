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

/// A Visualisation pane's settings: its Basic tab, which adds a tab for each of the
/// visualisation's own settings.
public class VisSettingsController extends PaneSettingsController {

    /// Made by GIN, with its content and its tabs.
    ///
    /// @param eventBus      The application's event bus.
    /// @param view          Its component.
    /// @param basicSettings The basic settings.
    @Inject
    public VisSettingsController(final EventBus eventBus,
                                 final PaneSettings view,
                                 final BasicVisSettingsController basicSettings) {
        super(eventBus, view);
        own(basicSettings);

        basicSettings.setOnAddTab(this::addTab);
        basicSettings.setOnRemoveTab(this::removeTab);
        addTab("Basic", basicSettings);
    }
}
