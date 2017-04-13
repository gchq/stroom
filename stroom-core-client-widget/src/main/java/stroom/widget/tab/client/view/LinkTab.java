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

package stroom.widget.tab.client.view;

import com.google.gwt.core.shared.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Style.Display;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.resources.client.ImageResource;
import com.google.gwt.user.client.DOM;

public class LinkTab extends AbstractTab {
    public interface Style extends CssResource {
        String linkTab();

        String selected();

        String background();

        String label();

        String hotspot();

        String hotspotVisible();

        String close();

        String closeActive();
    }

    public interface Resources extends ClientBundle {
        @Source("close.png")
        ImageResource close();

        @Source("closeActive.png")
        ImageResource closeActive();

        @Source("LinkTab.css")
        Style style();
    }

    private static Resources resources;

    private final Element element;
    private final Element background;
    private final Element label;
    private final Element close;
    private final Element hotspot;
    private final boolean allowClose;

    public LinkTab(final String text) {
        this(text, false);
    }

    public LinkTab(final String text, final boolean allowClose) {
        this.allowClose = allowClose;

        if (resources == null) {
            resources = GWT.create(Resources.class);
            resources.style().ensureInjected();
        }

        element = DOM.createDiv();
        element.setClassName(resources.style().linkTab());

        background = DOM.createDiv();
        background.setClassName(resources.style().background());
        background.setInnerText(text);
        element.appendChild(background);

        label = DOM.createDiv();
        label.setClassName(resources.style().label());
        label.setInnerText(text);
        element.appendChild(label);

        close = DOM.createDiv();
        close.setClassName(resources.style().close());
        element.appendChild(close);

        hotspot = DOM.createDiv();
        hotspot.setClassName(resources.style().hotspot());
        element.appendChild(hotspot);

        setElement(element);

//        if (allowClose) {
//            close.getStyle().setDisplay(Display.NONE);
            background.getStyle().setPaddingRight(15, Unit.PX);
//            label.getStyle().setPaddingRight(15, Unit.PX);
//        }
    }

    public Element getHotspot() {
        return hotspot;
    }

    @Override
    public void setSelected(final boolean selected) {
        if (selected) {
            element.addClassName(resources.style().selected());
            if (allowClose) {
                close.getStyle().setDisplay(Display.INLINE_BLOCK);
            }
        } else {
            element.removeClassName(resources.style().selected());
            if (allowClose) {
                close.getStyle().setDisplay(Display.NONE);
            }
        }
    }

    public void setHighlight(final boolean highlight) {
        if (highlight) {
            hotspot.addClassName(resources.style().hotspotVisible());
        } else {
            hotspot.removeClassName(resources.style().hotspotVisible());
        }
    }

    @Override
    public void setCloseActive(final boolean active) {
        if (allowClose) {
            if (active) {
                close.addClassName(resources.style().closeActive());
            } else {
                close.removeClassName(resources.style().closeActive());
            }
        }
    }

    @Override
    public void setText(final String text) {
        background.setInnerText(text);
        label.setInnerText(text);
    }

    public String getText() {
        return label.getInnerText();
    }

    @Override
    public Element getCloseElement() {
        return close;
    }
}
