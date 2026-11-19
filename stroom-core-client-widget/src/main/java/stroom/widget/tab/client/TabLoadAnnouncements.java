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

package stroom.widget.tab.client;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/// Decides when a tab bar tells a screen reader that a tab is loading, and that it has loaded.
///
/// Only the selected tab's loading is announced, the one the user is waiting on, and only once it
/// has gone on for a while (the bar's delay), so that quick loads, and tabs loading out of sight,
/// say nothing. Its finishing is announced only when its loading was. The bar runs the delay: it
/// starts it when [#startWaiting] says so, and asks [#delayElapsed] when it is up.
///
/// @param <T> What identifies a tab.
final class TabLoadAnnouncements<T> {

    // The tab whose delay is running, if any
    private T waiting;
    // The tabs whose loading was announced and whose finishing hasn't been yet
    private final Set<T> announced = new HashSet<>();

    /// A tab has started loading, or been selected: whether to start the delay for it, which it
    /// then waits on in place of any other tab.
    ///
    /// @param tab      The tab.
    /// @param busy     Whether it is loading.
    /// @param selected Whether it is the selected tab.
    /// @return Whether to start the delay.
    boolean startWaiting(final T tab, final boolean busy, final boolean selected) {
        if (busy && selected && !announced.contains(tab)) {
            waiting = tab;
            return true;
        }
        return false;
    }

    /// @return The tab whose delay is running, or null if none.
    T getWaiting() {
        return waiting;
    }

    /// The delay is up: whether to announce the waiting tab's loading, as it is still loading and
    /// still selected.
    ///
    /// @param tab      The tab the delay was for.
    /// @param busy     Whether it is still loading.
    /// @param selected Whether it is still the selected tab.
    /// @return Whether to announce that it is loading.
    boolean delayElapsed(final T tab, final boolean busy, final boolean selected) {
        if (tab == null || !Objects.equals(tab, waiting)) {
            return false;
        }
        waiting = null;
        if (busy && selected) {
            announced.add(tab);
            return true;
        }
        return false;
    }

    /// A tab has finished loading: whether to announce that it has, as its loading was.
    ///
    /// @param tab The tab.
    /// @return Whether to announce that it has loaded.
    boolean finished(final T tab) {
        if (Objects.equals(tab, waiting)) {
            waiting = null;
        }
        return announced.remove(tab);
    }

    /// A tab has been closed: nothing more is said about it.
    ///
    /// @param tab The tab.
    void removed(final T tab) {
        if (Objects.equals(tab, waiting)) {
            waiting = null;
        }
        announced.remove(tab);
    }
}
