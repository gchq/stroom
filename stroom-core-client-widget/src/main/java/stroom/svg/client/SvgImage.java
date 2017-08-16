/*
 * Copyright 2016 Crown Copyright
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

package stroom.svg.client;

import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NodeList;
import com.google.gwt.user.client.ui.Widget;
import stroom.widget.util.client.ResourceCache;

public class SvgImage extends Widget {
    private boolean detached;
    private int width;
    private int height;

    public SvgImage() {
        setElement(Document.get().createSpanElement());
    }

    public SvgImage(final String url, final int width, final int height, final boolean detached) {
        this();
        this.detached = detached;

        if (detached) {
            super.getElement().setId(Document.get().createUniqueId());
        }

        setUrl(url);
        setWidth(width);
        setHeight(height);
    }

    public SvgImage(final String url, final int width, final int height) {
        this(url, width, height, false);
    }

    public void setUrl(final String url) {
        ResourceCache.get(url, data -> {
            if (data != null) {
                final Element element = getElement();
                element.setInnerHTML(data);
                final Element svg = getSvg(element);
                if (svg != null) {
                    if (width > 0) {
                        svg.setAttribute("width", String.valueOf(width));
                    }
                    if (height > 0) {
                        svg.setAttribute("height", String.valueOf(height));
                    }
                }
            }
        });
    }

    @Override
    public com.google.gwt.user.client.Element getElement() {
        final Element element = super.getElement();
        final String id = element.getId();
        if (id != null) {
            final Element elem = Document.get().getElementById(id);
            if (elem != null) {
                return (com.google.gwt.user.client.Element) elem;
            }
        }

        return (com.google.gwt.user.client.Element) element;
    }

    public void setWidth(final int width) {
        if (width >= 0) {
            this.width = width;

            final Element element = getElement();
            element.setAttribute("width", width + "px");

            final Element svg = getSvg(element);
            if (svg != null) {
                svg.setAttribute("width", String.valueOf(width));
            }
        }
    }

    public void setHeight(final int height) {
        if (height >= 0) {
            this.height = height;

            final Element element = getElement();
            element.setAttribute("height", height + "px");

            final Element svg = getSvg(element);
            if (svg != null) {
                svg.setAttribute("height", String.valueOf(height));
            }
        }
    }

    private Element getSvg(final Element parent) {
        final NodeList<Element> nodes = parent.getElementsByTagName("svg");
        if (nodes != null && nodes.getLength() > 0) {
            return nodes.getItem(0);
        }
        return null;
    }
}
