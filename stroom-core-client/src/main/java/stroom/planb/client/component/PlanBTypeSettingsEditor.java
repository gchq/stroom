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

package stroom.planb.client.component;

import stroom.planb.shared.AbstractPlanBSettings;
import stroom.widget.component.client.Component;

/// The settings of one type of Plan B store (e.g. a state store's), in groups of Plan B's settings
/// widgets (general, retention, key schema and the rest) that its template declares. It reads and
/// writes the type's settings, and says when the user changes one, but not while it is read only
/// ([#setReadOnly], which its owner calls, as its document is).
///
/// Every type has general and retention settings, which its owner carries over when the type
/// changes ([#getGeneralSettings()], [#getRetentionSettings()]); some have more.
public abstract class PlanBTypeSettingsEditor extends Component {

    private Runnable onChange = () -> {
    };

    /// Shows settings.
    ///
    /// @param settings The settings, or settings of another type (or null) for this type's defaults.
    public abstract void read(AbstractPlanBSettings settings);

    /// @return The settings shown, as the user has changed them.
    public abstract AbstractPlanBSettings write();

    /// @return The general settings (e.g. the maximum store size), which every type has.
    public abstract GeneralSettingsWidget getGeneralSettings();

    /// @return The data retention settings, which every type has.
    public abstract RetentionSettingsWidget getRetentionSettings();

    /// @return The condense settings, or null for a type that has none.
    public CondenseSettingsWidget getCondenseSettings() {
        return null;
    }

    /// @return The shared file store settings, or null for a type that has none.
    public SharedFileStoreSettingsWidget getSharedFileStoreSettings() {
        return null;
    }

    /// @return Whether the general settings include the write options (synchronise merge and
    /// overwrite), which a store served over HTTP has.
    public boolean hasWriteOptions() {
        return true;
    }

    /// Sets what is told when the user changes a setting.
    ///
    /// @param onChange Run each time, or null for nothing.
    public void setOnChange(final Runnable onChange) {
        this.onChange = onChange != null
                ? onChange
                : () -> {
                };
    }

    /// Says that the user changed a setting, unless the settings are read only. Each type gives it
    /// to its widgets as their change callback.
    protected final void changed() {
        if (!isReadOnly()) {
            onChange.run();
        }
    }
}
