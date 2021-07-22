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

import stroom.svg.client.Icon;
import stroom.svg.client.SvgImages;

import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Style.Display;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.ui.Widget;

public class CurveTab extends AbstractTab {

    private static final int MAX_TEXT_LENGTH = 50;

    private final Element element;
    private final Element label;
    private final Element close;
    private final boolean allowClose;

    public CurveTab(final Icon icon, final String text, final boolean allowClose) {
        this.allowClose = allowClose;

        element = DOM.createDiv();
        element.setClassName("curveTab");

        final Element background = DOM.createDiv();
        background.setClassName("curveTab-background");
        element.appendChild(background);

        if (icon != null) {
            final Widget iconWidget = icon.asWidget();
            iconWidget.getElement().addClassName("curveTab-icon");
            element.appendChild(iconWidget.getElement());
        }

        label = DOM.createDiv();
        label.setClassName("curveTab-text");

        if (text.length() > MAX_TEXT_LENGTH) {
            label.setInnerText(text.substring(0, MAX_TEXT_LENGTH) + "...");
        } else {
            label.setInnerText(text);
        }

        label.setTitle(text);
        element.appendChild(label);

        close = DOM.createDiv();
        close.setClassName("curveTab-close");
        close.setInnerHTML(SvgImages.MONO_CLOSE);
        element.appendChild(close);

        setElement(element);

        if (!allowClose) {
            close.getStyle().setDisplay(Display.NONE);
            label.getStyle().setPaddingRight(20, Unit.PX);
        }
    }

    @Override
    protected void setKeyboardSelected(final boolean selected) {
        if (selected) {
            element.addClassName("curveTab-keyboardSelected");
        } else {
            element.removeClassName("curveTab-keyboardSelected");
        }
    }

    @Override
    public void setSelected(final boolean selected) {
        if (selected) {
            element.addClassName("curveTab-selected");
        } else {
            element.removeClassName("curveTab-selected");
        }
    }

    @Override
    public void setCloseActive(final boolean active) {
        if (allowClose) {
            if (active) {
                close.addClassName("curveTab-closeActive");
            } else {
                close.removeClassName("curveTab-closeActive");
            }
        }
    }

    public String getText() {
        return label.getInnerText();
    }

    @Override
    public void setText(final String text) {
        label.setInnerText(text);
    }

    @Override
    protected void setHover(final boolean hover) {
        if (hover) {
            element.addClassName("curveTab-hover");
        } else {
            element.removeClassName("curveTab-hover");
        }
    }

    @Override
    protected Element getCloseElement() {
        return close;
    }
}
