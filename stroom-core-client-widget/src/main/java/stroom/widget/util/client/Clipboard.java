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

import java.util.function.Consumer;

/// Reads and writes the system clipboard's text.
///
/// Writing works everywhere: with the async Clipboard API where the browser gives it (pages served
/// over HTTPS, or from localhost), else with `execCommand('copy')` on a hidden text area. Reading
/// is only possible with the async API, so only over HTTPS; the browser may ask the user first
/// (Chrome and Edge ask once for the site, Firefox and Safari show a Paste button by the pointer
/// unless the text was copied from the same site).
public final class Clipboard {

    private Clipboard() {
        // Static utility
    }

    /// @return True if the page may ask to read the clipboard, i.e. the browser gives it the async
    /// Clipboard API. It doesn't on plain HTTP, where only Ctrl+V can paste.
    public static native boolean canRead() /*-{
        var clipboard = $wnd.navigator.clipboard;
        return !!($wnd.isSecureContext && clipboard && clipboard.readText);
    }-*/;

    /// Reads the clipboard's text. Call it only after [#canRead] says it can.
    ///
    /// @param onText    Given the clipboard's text, once the browser (and perhaps the user) allows it.
    /// @param onRefused Run if the browser or the user refused, or the clipboard holds no text.
    public static native void readText(final Consumer<String> onText, final Runnable onRefused) /*-{
        var refused = $entry(function () {
            onRefused.@java.lang.Runnable::run()();
        });
        try {
            $wnd.navigator.clipboard.readText().then($entry(function (text) {
                onText.@java.util.function.Consumer::accept(Ljava/lang/Object;)(text);
            }), refused);
        } catch (e) {
            refused();
        }
    }-*/;

    /// Puts text on the clipboard, keeping the focus where it was.
    ///
    /// @param text The text.
    public static void writeText(final String text) {
        writeText(text, () -> {
        }, () -> {
        });
    }

    /// Puts text on the clipboard, keeping the focus where it was, saying whether it could.
    ///
    /// @param text     The text.
    /// @param onCopied Run once the text is on the clipboard.
    /// @param onFailed Run if the browser wouldn't put it there.
    public static native void writeText(final String text,
                                        final Runnable onCopied,
                                        final Runnable onFailed) /*-{
        var copied = $entry(function () {
            onCopied.@java.lang.Runnable::run()();
        });
        var withTextArea = $entry(function () {
            if (@stroom.widget.util.client.Clipboard::writeWithTextArea(Ljava/lang/String;)(text)) {
                onCopied.@java.lang.Runnable::run()();
            } else {
                onFailed.@java.lang.Runnable::run()();
            }
        });
        var clipboard = $wnd.navigator.clipboard;
        if ($wnd.isSecureContext && clipboard && clipboard.writeText) {
            clipboard.writeText(text).then(copied, withTextArea);
        } else {
            withTextArea();
        }
    }-*/;

    // Copies from an unseen text area, putting the focus back afterwards (copying selects the text
    // area's text, which takes the focus); false if the browser wouldn't copy
    private static native boolean writeWithTextArea(final String text) /*-{
        var focused = $doc.activeElement;
        var textArea = $doc.createElement('textarea');
        textArea.className = 'clipboard';
        textArea.setAttribute('aria-hidden', 'true');
        textArea.value = text;
        $doc.body.appendChild(textArea);
        textArea.focus();
        textArea.select();
        try {
            return $doc.execCommand('copy');
        } catch (e) {
            return false;
        } finally {
            $doc.body.removeChild(textArea);
            if (focused && focused !== $doc.body && focused.isConnected && focused.focus) {
                focused.focus({preventScroll: true});
            }
        }
    }-*/;
}
