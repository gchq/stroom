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


package stroom.gwt.workbench.client.app.screen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/// Keeps track of the popups that Stroom's `PopupManager` has open, from the `ShowPopupEvent`s
/// and `HidePopupEvent`s on the harness's event bus, so that the harness can hide them through
/// Stroom's own path (a `HidePopupEvent`) when the story renders again.
///
/// It mirrors `PopupManager`: showing a popup that is already open hides it.
///
/// @param <T> The type of the popups' presenters.
final class PopupTracker<T> {

    private final List<T> open = new ArrayList<>();

    /// @param popup The presenter of a popup being shown.
    void onShow(final T popup) {
        Objects.requireNonNull(popup);
        if (open.contains(popup)) {
            // PopupManager toggles a popup that is shown twice
            open.remove(popup);
        } else {
            open.add(popup);
        }
    }

    /// @param popup The presenter of a popup being hidden.
    void onHide(final T popup) {
        open.remove(popup);
    }

    /// @return The open popups, most recently shown first, i.e. in the order to hide them.
    List<T> getOpenPopups() {
        final List<T> list = new ArrayList<>(open);
        Collections.reverse(list);
        return list;
    }
}
