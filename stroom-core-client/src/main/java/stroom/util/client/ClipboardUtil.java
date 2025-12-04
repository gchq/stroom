/*
 * Copyright 2016-2025 Crown Copyright
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
        final BodyElement body = Document.get().getBody();
        final TextArea textArea = new TextArea();
        final Element textAreaElement = textArea.getElement();

        body.appendChild(textAreaElement);
        textArea.setStyleName("clipboardCopy");
        textArea.setText(text);

        final boolean result = copy();
        body.removeChild(textAreaElement);

        return result;
    }

    private static native boolean copy() /*-{
        var textArea = $doc.getElementsByClassName('clipboardCopy')[0];
        textArea.focus();
        textArea.select();
        return $doc.execCommand('copy');
    }-*/;
}
