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

package stroom.widget.tab.client.view;

import stroom.util.shared.NullSafe;

import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.DOM;

public class LinkTab extends AbstractTab {

    private final Element element;
    private final Element background;
    private final Element label;
    private final Element hotspot;

    public LinkTab(final String text, final String tooltip) {
        element = DOM.createDiv();
        element.setClassName("linkTab");
        NullSafe.consumeNonBlankString(tooltip, element::setTitle);

        background = DOM.createDiv();
        background.setClassName("linkTab-background");
        background.setInnerText(text);
        element.appendChild(background);

        label = DOM.createDiv();
        label.setClassName("linkTab-label");
        label.setInnerText(text);
        element.appendChild(label);

        hotspot = DOM.createDiv();
        hotspot.setClassName("linkTab-hotspot");
        element.appendChild(hotspot);

        setElement(element);
    }

    public Element getHotspot() {
        return hotspot;
    }

    @Override
    protected void setKeyboardSelected(final boolean selected) {
        if (selected) {
            element.addClassName("linkTab-keyboardSelected");
        } else {
            element.removeClassName("linkTab-keyboardSelected");
        }
    }

    @Override
    public void setSelected(final boolean selected) {
        if (selected) {
            element.addClassName("linkTab-selected");
        } else {
            element.removeClassName("linkTab-selected");
        }
    }

    public void setHighlight(final boolean highlight) {
        if (highlight) {
            hotspot.addClassName("linkTab-hotspotVisible");
        } else {
            hotspot.removeClassName("linkTab-hotspotVisible");
        }
    }

    public String getText() {
        return label.getInnerText();
    }

    @Override
    public void setText(final String text) {
        background.setInnerText(text);
        label.setInnerText(text);
    }
}
