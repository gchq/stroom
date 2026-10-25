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

package stroom.entity.client.presenter;

import com.google.gwt.dom.client.Element;

/// Opens or closes every section of rendered markdown in a frame (see MarkdownConverter, whose headings each make a
/// collapsible section): the Documentation toolbar's Expand all and Collapse all.
final class MarkdownSections {

    private MarkdownSections() {
        // Static only.
    }

    /// @param frameElement The frame holding the rendered markdown, or an element containing it.
    /// @param open         True to open every section, false to close them all.
    static native void setAllOpen(Element frameElement, boolean open) /*-{
        var frame = frameElement.tagName === 'IFRAME'
            ? frameElement
            : frameElement.querySelector('iframe');
        var doc = null;
        try {
            doc = frame ? frame.contentDocument : null;
        } catch (e) {
            // Another origin.
        }
        if (!doc) {
            return;
        }
        Array.prototype.forEach.call(doc.querySelectorAll('details.markdown-section'), function (section) {
            section.open = open;
        });
    }-*/;
}
