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
import stroom.svg.client.SvgIcon;
import stroom.widget.tab.client.presenter.ImageIcon;

import com.google.gwt.core.shared.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Style.Display;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.resources.client.ImageResource;
import com.google.gwt.resources.client.ImageResource.ImageOptions;
import com.google.gwt.resources.client.ImageResource.RepeatStyle;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.ui.Image;

public class CurveTab extends AbstractTab {

    private static final int MAX_TEXT_LENGTH = 50;

    private final Element element;
    private final Element background;
    private final Element leftBackground;
    private final Element midBackground;
    private final Element rightBackground;
    //    private final Element icon;
    private final Element label;
    private final Element close;
    private final boolean allowClose;

    public CurveTab(final Icon icon, final String text, final boolean allowClose) {
        this.allowClose = allowClose;

        element = DOM.createDiv();
        element.setClassName("curveTab");

        background = DOM.createDiv();
        background.setClassName("curveTab-background");
        element.appendChild(background);

        leftBackground = DOM.createDiv();
        leftBackground.setClassName("curveTab-leftBackground");
        background.appendChild(leftBackground);

        midBackground = DOM.createDiv();
        midBackground.setClassName("curveTab-midBackground");
        background.appendChild(midBackground);

        rightBackground = DOM.createDiv();
        rightBackground.setClassName("curveTab-rightBackground");
        background.appendChild(rightBackground);

        if (icon != null) {
            if (icon instanceof ImageIcon) {
                final ImageIcon imageIcon = (ImageIcon) icon;
                final Image image = imageIcon.getImage();
                if (image != null) {
                    image.getElement().addClassName("curveTab-icon");
                    element.appendChild(image.getElement());
                }
//            } else if (icon instanceof SvgIcon) {
//                final SvgIcon glyphIcon = (SvgIcon) icon;
//                final SafeHtml safeHtml = SafeHtmlUtils.fromTrustedString("<div class=\""
//                        + resources.style().icon()
//                        + "\"><div class=\""
//                        + resources.style().face()
//                        + "\" style=\"color:"
//                        + glyphIcon.getColourSet()
//                        + "\"><i class=\""
//                        + glyphIcon.getGlyph()
//                        + "\"></i></div></div>");
//                final HTML html = new HTML(safeHtml);
//                final Element elem = html.getElement();
//                element.appendChild(elem);
            } else if (icon instanceof SvgIcon) {
                final SvgIcon svgIcon = (SvgIcon) icon;

                final Image image = new Image(svgIcon.getUrl());
                image.addStyleName("curveTab-icon");


//                final SafeHtml safeHtml = SafeHtmlUtils.fromTrustedString("<div class=\""
//                        + resources.style().icon()
//                        + "\"></div>");
//                final HTML html = new HTML(safeHtml);
//
//                if (svgIcon.getUrl() != null) {
//                    ResourceCache.get(svgIcon.getUrl(), data -> {
//                        html.setHTML("<div class=\"" +
//                                resources.style().icon() +
//                                "\">" +
//                                data +
//                                "</div>");
//                        final Element svg = getElement().getElementsByTagName("svg").getItem(0).cast();
//                        svg.setAttribute("width", "18");
//                        svg.setAttribute("height", "18");
//                    });
//                }

                final Element elem = image.getElement();
                element.appendChild(elem);
            }
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
        element.appendChild(close);

        setElement(element);

        if (!allowClose) {
            close.getStyle().setDisplay(Display.NONE);
            label.getStyle().setPaddingRight(20, Unit.PX);
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
