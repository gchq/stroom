/*
 * Copyright 2025 Crown Copyright
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

package stroom.planb.client.view;

import stroom.document.client.event.ChangeUiHandlers;
import stroom.widget.component.client.Component;

/// The base of Plan B's settings widgets (general, retention, snapshot, key and value schemas and
/// the rest): a dumb component that says when the user changes a setting.
public abstract class AbstractSettingsWidget extends Component {

    private Runnable onChange = () -> {
    };

    /// @param onChange Run when the user changes a setting.
    public void setOnChange(final Runnable onChange) {
        this.onChange = onChange;
    }

    /// The old way to hear changes, kept while the widgets' view interfaces (and the floor map's
    /// views) still ask for it; they move to [#setOnChange(Runnable)] with Plan B's wave.
    ///
    /// @param uiHandlers Told when the user changes a setting.
    public void setUiHandlers(final ChangeUiHandlers uiHandlers) {
        setOnChange(uiHandlers::onChange);
    }

    /// Says that the user changed a setting.
    protected final void changed() {
        onChange.run();
    }
}
