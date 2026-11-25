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

package stroom.widget.spinner.client;

/// Decides when [LoadStatus] tells a screen reader that something is loading, and that it has loaded.
///
/// Loading is announced only once it has gone on for a while, so that quick loads say nothing, and its
/// finishing only when its loading was. The status runs the delay: it starts it when [#start()] says so,
/// and asks [#delayElapsed()] when it is up.
final class LoadAnnouncements {

    private boolean loading;
    private boolean announced;

    /// It has started loading.
    ///
    /// @return Whether to start the delay: not if it was loading already.
    boolean start() {
        if (loading) {
            return false;
        }
        loading = true;
        return true;
    }

    /// @return Whether it is loading and that hasn't been announced, so the delay is to run, e.g.
    /// again once it is back on the page.
    boolean isWaiting() {
        return loading && !announced;
    }

    /// The delay is up.
    ///
    /// @return Whether to announce that it is loading: not if it has finished.
    boolean delayElapsed() {
        if (!isWaiting()) {
            return false;
        }
        announced = true;
        return true;
    }

    /// It has finished loading, or failed to.
    ///
    /// @return Whether its loading was announced, so its finishing is to be.
    boolean finish() {
        final boolean wasAnnounced = announced;
        loading = false;
        announced = false;
        return wasAnnounced;
    }
}
