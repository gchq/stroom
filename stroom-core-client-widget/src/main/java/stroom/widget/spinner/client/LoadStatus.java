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

import stroom.widget.component.client.AriaPanel;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.user.client.Timer;

import java.util.Objects;
import java.util.function.Supplier;

/// Tells screen readers that something is loading, once it has for a while, and how that ended, as a
/// [SmallBusySpinner] only shows it. It is hidden from sight, and goes beside the spinner, not inside it,
/// as the spinner is hidden once it stops:
///
/// ```
/// <spinner:SmallBusySpinner ui:field="spinner"/>
/// <spinner:LoadStatus ui:field="loadStatus" text="Loading values"/>
/// ```
public class LoadStatus extends AriaPanel {

    // How long something loads before screen readers are told, as a tab does (AbstractTabBar)
    private static final int DELAY_MILLIS = 1000;

    private final LoadAnnouncements announcements = new LoadAnnouncements();
    private final Timer delay = new Timer() {
        @Override
        public void run() {
            if (announcements.delayElapsed()) {
                announce(() -> text);
            }
        }
    };
    private String text = "Loading";

    /// Makes a status that says nothing.
    public LoadStatus() {
        setStyleName("sr-only");
        // There from the start, as one added with its text already in it isn't reliably read
        setRole("status");
        setAriaLive("polite");
    }

    /// @param text What is said while it loads, e.g. `Loading values`.
    public void setText(final String text) {
        this.text = Objects.requireNonNull(text, "text");
    }

    /// Follows a spinner: what it shows loading is what this tells of.
    ///
    /// @param spinner The spinner, which nothing else follows.
    /// @param result  Gives what is said once it stops, when what was loaded, or why it couldn't be, is
    ///                shown, e.g. `Values loaded` or the error; or blank for nothing.
    public void follow(final SmallBusySpinner spinner, final Supplier<String> result) {
        spinner.setOnBusyChange(busy -> {
            if (busy) {
                start();
            } else {
                finish(result);
            }
        });
    }

    /// Says that it has started loading, which is told if it takes a while.
    public void start() {
        if (announcements.start()) {
            delay.schedule(DELAY_MILLIS);
        }
    }

    /// Says that it has finished loading, or couldn't, which is told if that it was loading was.
    ///
    /// @param result Gives what is said, or blank for nothing.
    public void finish(final Supplier<String> result) {
        delay.cancel();
        if (announcements.finish()) {
            announce(result);
        }
    }

    @Override
    protected void onLoad() {
        super.onLoad();
        if (announcements.isWaiting()) {
            delay.schedule(DELAY_MILLIS);
        }
    }

    @Override
    protected void onUnload() {
        delay.cancel();
        super.onUnload();
    }

    // Emptied first, so that the same text is said again, and set once what it tells of is shown
    private void announce(final Supplier<String> message) {
        getElement().setInnerText("");
        Scheduler.get().scheduleDeferred(() -> getElement().setInnerText(message.get()));
    }
}
