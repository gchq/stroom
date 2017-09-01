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
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.user.client.DOM;

public class LinkTab extends AbstractTab {
    private static Resources resources;
    private final Element element;
    private final Element background;
    private final Element label;
    private final Element hotspot;
    public LinkTab(final String text) {
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

        hotspot = DOM.createDiv();
        hotspot.setClassName(resources.style().hotspot());
        element.appendChild(hotspot);

        setElement(element);
    }

    public Element getHotspot() {
        return hotspot;
    }

    @Override
    public void setSelected(final boolean selected) {
        if (selected) {
            element.addClassName(resources.style().selected());
        } else {
            element.removeClassName(resources.style().selected());
        }
    }

    public void setHighlight(final boolean highlight) {
        if (highlight) {
            hotspot.addClassName(resources.style().hotspotVisible());
        } else {
            hotspot.removeClassName(resources.style().hotspotVisible());
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

    public interface Style extends CssResource {
        String linkTab();

        String selected();

        String background();

        String label();

        String hotspot();

        String hotspotVisible();
    }

    public interface Resources extends ClientBundle {
        @Source("LinkTab.css")
        Style style();
    }
}
