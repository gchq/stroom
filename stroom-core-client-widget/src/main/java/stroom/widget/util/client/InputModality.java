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

package stroom.widget.util.client;

/// Whether the last input on the page was a key or a pointer (mouse, pen or touch), for widgets
/// that move the focus from code, whose `:focus-visible` the browser can't be relied on to set
/// (e.g. a menu showing a focus ring only while the keyboard is used).
public final class InputModality {

    private static boolean installed;

    private InputModality() {
        // Utility class
    }

    /// Whether the last input on the page was a key press. True until the first input of either kind.
    ///
    /// @return True if the keyboard is in use.
    public static boolean isKeyboard() {
        install();
        return isKeyboardNative();
    }

    /// Starts listening for input, if it hasn't already. Call it before the first input it should
    /// see, e.g. when a widget that asks is created ([#isKeyboard()] starts it too, but then misses
    /// the input that led to the call).
    public static void install() {
        if (!installed) {
            installed = true;
            installNative();
        }
    }

    private static native boolean isKeyboardNative() /*-{
        return $wnd.__stroomInputModalityKeyboard !== false;
    }-*/;

    // Listens in the capture phase, so that a widget stopping an event doesn't hide it
    private static native void installNative() /*-{
        var doc = $doc;
        doc.addEventListener('keydown', function () {
            $wnd.__stroomInputModalityKeyboard = true;
        }, true);
        doc.addEventListener('pointerdown', function () {
            $wnd.__stroomInputModalityKeyboard = false;
        }, true);
        doc.addEventListener('mousedown', function () {
            $wnd.__stroomInputModalityKeyboard = false;
        }, true);
    }-*/;
}
