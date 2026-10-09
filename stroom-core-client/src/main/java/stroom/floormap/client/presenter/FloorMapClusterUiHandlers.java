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

package stroom.floormap.client.presenter;

import com.gwtplatform.mvp.client.UiHandlers;

/// What the cluster dialog's view tells its presenter: one of the three controls
/// — the search box, the Area dropdown or the Group dropdown — has changed.
///
/// Top-level rather than nested in [FloorMapClusterPresenter] because
/// the presenter implements it, and a class cannot name its own nested type in
/// its `implements` clause. Mirrors the platform's own
/// `QuickFilterUiHandlers`.
public interface FloorMapClusterUiHandlers extends UiHandlers {

    /// The search text or one of the dropdowns changed. The presenter reads the
    /// current state back off the view rather than being handed it, so a change to
    /// one control cannot be applied without the other two.
    void onFilterChange();
}
