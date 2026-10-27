/*
 * Copyright 2026 Crown Copyright
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

import com.google.gwt.user.client.ui.Focus;

/// The fields every pane's Basic settings tab has: the pane's id, which can't be changed, and its
/// name. A Basic settings tab's component implements it, for [BasicSettingsTabController].
public interface BasicSettingsFields extends Focus {

    /// @param id The pane's id.
    void setId(String id);

    /// @return The pane's name, as typed.
    String getName();

    /// @param name The pane's name.
    void setName(String name);
}
