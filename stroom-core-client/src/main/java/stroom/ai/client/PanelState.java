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

package stroom.ai.client;

/// Remembers, in the browser's local storage, whether the Ask Stroom AI chat was open, so it opens again after a
/// page refresh, and the size the user gave the docked chat panel. Local storage may be missing or blocked (e.g.
/// in a private window), in which case nothing is remembered.
public final class PanelState {

    private static final String OPEN_KEY = "stroom.askStroomAi.open";
    private static final String DOCK_SIZE_KEY = "stroom.askStroomAi.dockSize";

    private PanelState() {
        // Static only.
    }

    /// @return True if the chat was open when the page was last left.
    public static native boolean wasOpen() /*-{
        try {
            return $wnd.localStorage.getItem("stroom.askStroomAi.open") === "true";
        } catch (e) {
            return false;
        }
    }-*/;

    /// Records whether the chat is open.
    static void setOpen(final boolean open) {
        setItem(OPEN_KEY, open);
    }

    /// @return The width (docked left or right) or height (docked at the top or bottom) the user last gave the
    /// docked chat panel, in pixels, or 0 if none is remembered.
    static int dockSize() {
        return getIntItem(DOCK_SIZE_KEY);
    }

    /// Records the width or height the user gave the docked chat panel.
    ///
    /// @param size The size in pixels.
    static void setDockSize(final int size) {
        setIntItem(DOCK_SIZE_KEY, size);
    }

    private static native int getIntItem(String key) /*-{
        try {
            var value = parseInt($wnd.localStorage.getItem(key), 10);
            return isNaN(value) || value < 0
                ? 0
                : value;
        } catch (e) {
            return 0;
        }
    }-*/;

    private static native void setIntItem(String key, int value) /*-{
        try {
            $wnd.localStorage.setItem(key, String(value));
        } catch (e) {
            // Nothing is remembered.
        }
    }-*/;

    private static native void setItem(String key, boolean open) /*-{
        try {
            if (open) {
                $wnd.localStorage.setItem(key, "true");
            } else {
                $wnd.localStorage.removeItem(key);
            }
        } catch (e) {
            // Nothing is remembered.
        }
    }-*/;
}
