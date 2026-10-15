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

package stroom.gwt.workbench.client;

import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.Value;

import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.IFrameElement;

/// Reads the document shown in a story's `<iframe>`, for plays that check what Stroom loads into
/// its frames (e.g. the Markdown preview's document or `ui/vis.html`).
public final class FrameDocuments {

    private static final String SOURCE_DOC_ATTRIBUTE = "srcdoc";

    private FrameDocuments() {
        // Static utility
    }

    /// The language of the document in a frame, so a play can check that the document declares
    /// one for screen readers (WCAG 3.1.1, Language of Page), e.g.
    /// `play.waitFor(() -> play.expect(FrameDocuments.language(frame)).toBe("en"))`.
    ///
    /// A frame given its document as `srcdoc` (e.g. the Markdown preview) is read from that, as it
    /// is sandboxed, which hides its document from the page; the browser's `DOMParser` reads the
    /// `srcdoc` as the frame would. Otherwise the frame's loaded document is read, so wait for the
    /// value, as a frame shows an empty document (with no language) until its own has loaded.
    ///
    /// @param frame The `<iframe>`, which must have a `srcdoc` or show a document of the page's
    ///              origin.
    /// @return The `lang` attribute of the frame document's `<html>` element, or null if the frame
    /// has no document yet or it has no `lang`.
    public static Value<String> language(final Query frame) {
        final Value<Element> element = frame.element();
        return Value.of(frame.describe() + " document's lang", () -> {
            final IFrameElement iFrameElement = IFrameElement.as(element.get());
            final Element documentElement;
            if (iFrameElement.hasAttribute(SOURCE_DOC_ATTRIBUTE)) {
                documentElement = parseHtml(iFrameElement.getAttribute(SOURCE_DOC_ATTRIBUTE));
            } else {
                final Document document = iFrameElement.getContentDocument();
                documentElement = document == null
                        ? null
                        : document.getDocumentElement();
            }
            if (documentElement == null || !documentElement.hasAttribute("lang")) {
                return null;
            }
            return documentElement.getAttribute("lang");
        });
    }

    /// Parses HTML as a frame would, without running its scripts.
    private static native Element parseHtml(String html) /*-{
        return new $wnd.DOMParser().parseFromString(html, "text/html").documentElement;
    }-*/;
}
