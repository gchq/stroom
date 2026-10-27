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

package stroom.dashboard.client.flexlayout;

import stroom.dashboard.client.main.DashboardPane;

import java.util.List;

public interface FlexLayoutChangeHandler {

    void removeTab(MutableTabLayoutConfig tabLayoutConfig, MutableTabConfig tab);

    void removeTabPanel(MutableTabLayoutConfig tabLayoutConfig);

    void onChange();

    /// Called when placing new components (a duplicate, or an added component) is cancelled, so
    /// that they can be removed again: they were made before they were placed.
    ///
    /// @param components The components that were not placed.
    default void cancelNewComponents(final List<DashboardPane> components) {
    }
}
