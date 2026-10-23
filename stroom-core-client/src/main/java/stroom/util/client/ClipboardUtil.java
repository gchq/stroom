/*
 * Copyright 2023 Crown Copyright
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

package stroom.util.client;

import com.google.gwt.dom.client.BodyElement;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.ui.TextArea;

public final class ClipboardUtil {

    /**
     * Copies the provided text to the clipboard via an invisible textarea
     * @return Whether the copy was successful
     */
    public static boolean copy(final String text) {
        final Element focused = getFocusedElement();
        final BodyElement body = Document.get().getBody();
        final TextArea textArea = new TextArea();
        final Element textAreaElement = textArea.getElement();

        body.appendChild(textAreaElement);
        textArea.setStyleName("clipboardCopy");
        textArea.setText(text);

        final boolean result = copy();
        body.removeChild(textAreaElement);
        // Copying selects the text area's text, which takes the focus; without this, removing the
        // text area left the focus on the page itself
        refocus(focused);

        return result;
    }

    private static native Element getFocusedElement() /*-{
        return $doc.activeElement;
    }-*/;

    private static native void refocus(final Element element) /*-{
        if (element && element !== $doc.body && element.isConnected && element.focus) {
            element.focus({preventScroll: true});
        }
    }-*/;

    private static native boolean copy() /*-{
        var textArea = $doc.getElementsByClassName('clipboardCopy')[0];
        textArea.focus();
        textArea.select();
        return $doc.execCommand('copy');
    }-*/;
}
