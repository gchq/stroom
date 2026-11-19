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

package stroom.widget.form.client;

/// Content that doesn't show all its fields at once, e.g. tabs (only the tab showing is on the page),
/// so that walking its widgets ([InvalidInputs#find]) misses some. It looks for a field holding input
/// that can't be kept itself, and shows it, selecting its tab. A dialog's OK asks a dialog that is
/// one ([stroom.widget.popup.client.event.ShowPopupEvent]).
public interface RefusesInvalidInput {

    /// Refuses to go on while a field holds input that can't be kept: shows the field (e.g. selects
    /// its tab), says why under it and focuses it.
    ///
    /// @return Whether it refused.
    boolean refuseInvalidInput();
}
