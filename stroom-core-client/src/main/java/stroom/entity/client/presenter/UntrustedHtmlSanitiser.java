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

import stroom.entity.client.presenter.HtmlSanitisePolicy.ElementAction;

import com.google.gwt.core.client.JsArrayString;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Node;

/// Walks a DOM tree that was parsed in an inert document and removes anything that [HtmlSanitisePolicy] does not
/// allow. The tree must not belong to the live page document, otherwise images and event handlers could act
/// before they are removed.
final class UntrustedHtmlSanitiser {

    private UntrustedHtmlSanitiser() {
        // Static only.
    }

    /// Sanitises the children of the container in place.
    ///
    /// @param container The element holding the untrusted html, from an inert document.
    static void sanitise(final Element container) {
        sanitiseChildren(container);
    }

    private static void sanitiseChildren(final Node parent) {
        Node child = parent.getFirstChild();
        while (child != null) {
            final Node next = child.getNextSibling();
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                sanitiseElement(parent, Element.as(child));
            } else if (child.getNodeType() != Node.TEXT_NODE) {
                // Comments, processing instructions etc.
                parent.removeChild(child);
            }
            child = next;
        }
    }

    private static void sanitiseElement(final Node parent, final Element element) {
        final String tag = element.getTagName().toLowerCase();
        final ElementAction action = HtmlSanitisePolicy.getElementAction(tag);
        switch (action) {
            case DROP:
                parent.removeChild(element);
                break;
            case UNWRAP:
                sanitiseChildren(element);
                while (element.getFirstChild() != null) {
                    parent.insertBefore(element.getFirstChild(), element);
                }
                parent.removeChild(element);
                break;
            case IMAGE:
                parent.replaceChild(createImageReplacement(element), element);
                break;
            case KEEP:
                if ("input".equals(tag)
                    && !HtmlSanitisePolicy.isAllowedInput(tag, element.getAttribute("type"))) {
                    parent.removeChild(element);
                } else {
                    filterAttributes(tag, element);
                    if ("a".equals(tag) && element.hasAttribute("href")) {
                        setLinkTarget(element);
                    } else if ("input".equals(tag)) {
                        element.setAttribute("disabled", "disabled");
                    }
                    sanitiseChildren(element);
                }
                break;
            default:
                parent.removeChild(element);
        }
    }

    private static void filterAttributes(final String tag, final Element element) {
        final JsArrayString names = getAttributeNames(element);
        for (int i = 0; i < names.length(); i++) {
            final String name = names.get(i);
            final String value = element.getAttribute(name);
            final String filtered = HtmlSanitisePolicy.filterAttribute(tag, name, value);
            if (filtered == null) {
                element.removeAttribute(name);
            } else if (!filtered.equals(value)) {
                element.setAttribute(name, filtered);
            }
        }
    }

    private static Element createImageReplacement(final Element image) {
        final String src = image.getAttribute("src");
        final String alt = image.getAttribute("alt");
        final Element replacement;
        if (HtmlSanitisePolicy.isInlineImage(src)) {
            replacement = image.getOwnerDocument().createElement("img");
            replacement.setAttribute("src", src.trim());
            if (alt != null && !alt.isEmpty()) {
                replacement.setAttribute("alt", alt);
            }
        } else {
            final String url = HtmlSanitisePolicy.imageReplacementUrl(src);
            if (url != null) {
                replacement = image.getOwnerDocument().createElement("a");
                replacement.setAttribute("href", url);
                replacement.setAttribute("title", url);
                setLinkTarget(replacement);
            } else {
                replacement = image.getOwnerDocument().createElement("span");
            }
            replacement.setInnerText(HtmlSanitisePolicy.imageReplacementText(alt));
        }
        return replacement;
    }

    private static void setLinkTarget(final Element link) {
        link.setAttribute("target", "_blank");
        link.setAttribute("rel", "noopener noreferrer nofollow");
    }

    private static native JsArrayString getAttributeNames(final Element element) /*-{
        var names = [];
        for (var i = 0; i < element.attributes.length; i++) {
            names.push(element.attributes[i].name);
        }
        return names;
    }-*/;
}
