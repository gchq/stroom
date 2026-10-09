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

import com.google.gwt.user.client.Window;

/// The leak check's probe (see the test runner's `--leak-check`).
///
/// With `probe=1` in the page's address, a story that opens its screen with
/// [ScreenHarness#afterStartUp] exposes `window.__leakProbe`, so that a script can close and open
/// the screen many times and measure what each close leaves behind:
///
/// * `close(dispose)` closes the screen as closing a Stroom tab does: its popups are hidden, its
///   widgets removed, and, if `dispose` is true, the presenters made while opening it are disposed
///   (as `DocumentPlugin` does) and the clean ups its opening registered are run.
/// * `open()` opens the screen again, as the story did (usually with new presenters).
/// * `shown()` is the number of widgets the opening has shown, and of popups shown.
/// * `pending()` is the number of REST requests not yet answered.
final class LeakProbe {

    private final ScreenHarness harness;
    private final Runnable reopen;

    private LeakProbe(final ScreenHarness harness, final Runnable reopen) {
        this.harness = harness;
        this.reopen = reopen;
    }

    /// @return Whether the page's address asks for the probe.
    static boolean isRequested() {
        return "1".equals(Window.Location.getParameter("probe"));
    }

    /// Exposes the probe on the page.
    ///
    /// @param harness The story's harness.
    /// @param reopen  Opens the story's screen again.
    static void install(final ScreenHarness harness, final Runnable reopen) {
        export(new LeakProbe(harness, reopen));
    }

    private void close(final boolean dispose) {
        harness.probeClose(dispose);
    }

    private void open() {
        reopen.run();
    }

    private int shown() {
        return harness.probeShownCount();
    }

    private int pending() {
        return harness.probePendingCount();
    }

    private static native void export(LeakProbe probe) /*-{
        $wnd.__leakProbe = {
            close: function (dispose) {
                probe.@stroom.gwt.workbench.client.app.screen.LeakProbe::close(Z)(!!dispose);
            },
            open: function () {
                probe.@stroom.gwt.workbench.client.app.screen.LeakProbe::open()();
            },
            shown: function () {
                return probe.@stroom.gwt.workbench.client.app.screen.LeakProbe::shown()();
            },
            pending: function () {
                return probe.@stroom.gwt.workbench.client.app.screen.LeakProbe::pending()();
            }
        };
    }-*/;
}
