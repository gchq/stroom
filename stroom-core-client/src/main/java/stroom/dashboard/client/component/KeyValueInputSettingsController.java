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

import stroom.dashboard.client.component.BasicKeyValueInputSettingsController;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

/// A Key/Value Input pane's settings: its Basic tab.
public class KeyValueInputSettingsController extends ComponentSettingsDialogController {

    /// Made by GIN, with its content and its tabs.
    @Inject
    public KeyValueInputSettingsController(final EventBus eventBus,
                                           final ComponentSettingsDialog view,
                                           final BasicKeyValueInputSettingsController basicSettings) {
        super(eventBus, view);
        own(basicSettings);

        addTab("Basic", basicSettings);
    }
}
