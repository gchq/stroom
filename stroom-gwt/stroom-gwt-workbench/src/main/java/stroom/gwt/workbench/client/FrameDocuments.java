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
            final Element documentElement = documentElement(IFrameElement.as(element.get()));
            if (documentElement == null || !documentElement.hasAttribute("lang")) {
                return null;
            }
            return documentElement.getAttribute("lang");
        });
    }

    /// The links in the document in a frame that open in a new browser tab
    /// (`a[target="_blank"]`), each as its text content (which includes visually hidden text, as a
    /// screen reader reads it) and `rel`, e.g. `"count documentation (opens in new tab) | noopener
    /// noreferrer"`, so a play can check that each one says it opens a new tab.
    ///
    /// The frame's document is read as [#language(Query)] reads it.
    ///
    /// @param frame The `<iframe>`, which must have a `srcdoc` or show a document of the page's
    ///              origin.
    /// @return The links, each as `text | rel` and separated by new lines, or null if the frame has
    /// no document yet.
    public static Value<String> newTabLinks(final Query frame) {
        final Value<Element> element = frame.element();
        return Value.of(frame.describe() + " document's new tab links", () -> {
            final Element documentElement = documentElement(IFrameElement.as(element.get()));
            if (documentElement == null) {
                return null;
            }
            return describeNewTabLinks(documentElement);
        });
    }

    /// The number of elements in the document in a frame that match a CSS selector, so a play can
    /// check what the document holds, e.g. its theme's classes.
    ///
    /// The frame's document is read as [#language(Query)] reads it.
    ///
    /// @param frame    The `<iframe>`, which must have a `srcdoc` or show a document of the page's
    ///                 origin.
    /// @param selector The CSS selector.
    /// @return The number of matches, or null if the frame has no document yet.
    public static Value<Integer> count(final Query frame, final String selector) {
        final Value<Element> element = frame.element();
        return Value.of(frame.describe() + " document's " + selector, () -> {
            final Element documentElement = documentElement(IFrameElement.as(element.get()));
            if (documentElement == null) {
                return null;
            }
            return countMatches(documentElement, selector);
        });
    }

    // The frame's document's <html> element: its srcdoc parsed, as a sandboxed frame hides its
    // document from the page, or else its loaded document's, or null if it has none yet
    private static Element documentElement(final IFrameElement iFrameElement) {
        if (iFrameElement.hasAttribute(SOURCE_DOC_ATTRIBUTE)) {
            return parseHtml(iFrameElement.getAttribute(SOURCE_DOC_ATTRIBUTE));
        }
        final Document document = iFrameElement.getContentDocument();
        return document == null
                ? null
                : document.getDocumentElement();
    }

    private static native String describeNewTabLinks(Element documentElement) /*-{
        var links = documentElement.querySelectorAll('a[target="_blank"]');
        var descriptions = [];
        for (var i = 0; i < links.length; i++) {
            descriptions.push(links[i].textContent + " | " + links[i].getAttribute("rel"));
        }
        return descriptions.join("\n");
    }-*/;

    private static native int countMatches(Element documentElement, String selector) /*-{
        return documentElement.ownerDocument.querySelectorAll(selector).length;
    }-*/;

    /// Parses HTML as a frame would, without running its scripts.
    private static native Element parseHtml(String html) /*-{
        return new $wnd.DOMParser().parseFromString(html, "text/html").documentElement;
    }-*/;
}
