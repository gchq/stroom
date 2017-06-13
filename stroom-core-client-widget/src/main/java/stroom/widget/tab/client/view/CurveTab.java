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
import com.google.gwt.resources.client.ImageResource.ImageOptions;
import com.google.gwt.resources.client.ImageResource.RepeatStyle;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.Image;
import stroom.widget.button.client.GlyphIcon;
import stroom.widget.button.client.SVGImage;
import stroom.widget.tab.client.presenter.Icon;
import stroom.widget.tab.client.presenter.ImageIcon;
import stroom.widget.tab.client.presenter.SVGIcon;

public class CurveTab extends AbstractTab {
    public interface Style extends CssResource {
        String curveTab();

        String hover();

        String selected();

        String background();

        String leftBackground();

        String midBackground();

        String rightBackground();

        String icon();

        String face();

        String text();

        String close();

        String closeActive();
    }

    public interface Resources extends ClientBundle {
        @Source("content.png")
        @ImageOptions(repeatStyle = RepeatStyle.Horizontal)
        ImageResource content();

        @Source("left.png")
        ImageResource left();

        @Source("middle.png")
        @ImageOptions(repeatStyle = RepeatStyle.Horizontal)
        ImageResource middle();

        @Source("right.png")
        ImageResource right();

        @Source("close.png")
        ImageResource close();

        @Source("closeActive.png")
        ImageResource closeActive();

        @Source("CurveTab.css")
        Style style();
    }

    private static Resources resources;

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

        if (resources == null) {
            resources = GWT.create(Resources.class);
            resources.style().ensureInjected();
        }

        element = DOM.createDiv();
        element.setClassName(resources.style().curveTab());

        background = DOM.createDiv();
        background.setClassName(resources.style().background());
        element.appendChild(background);

        leftBackground = DOM.createDiv();
        leftBackground.setClassName(resources.style().leftBackground());
        background.appendChild(leftBackground);

        midBackground = DOM.createDiv();
        midBackground.setClassName(resources.style().midBackground());
        background.appendChild(midBackground);

        rightBackground = DOM.createDiv();
        rightBackground.setClassName(resources.style().rightBackground());
        background.appendChild(rightBackground);

        if (icon != null) {
            if (icon instanceof ImageIcon) {
                final ImageIcon imageIcon = (ImageIcon) icon;
                final Image image = imageIcon.getImage();
                if (image != null) {
                    image.getElement().addClassName(resources.style().icon());
                    element.appendChild(image.getElement());
                }
            } else if (icon instanceof GlyphIcon) {
                final GlyphIcon glyphIcon = (GlyphIcon) icon;
                final SafeHtml safeHtml = SafeHtmlUtils.fromTrustedString("<div class=\""
                        + resources.style().icon()
                        + "\"><div class=\""
                        + resources.style().face()
                        + "\" style=\"color:"
                        + glyphIcon.getColourSet()
                        + "\"><i class=\""
                        + glyphIcon.getGlyph()
                        + "\"></i></div></div>");
                final HTML html = new HTML(safeHtml);
                final Element elem = html.getElement();
                element.appendChild(elem);
            } else if (icon instanceof SVGIcon) {
                final SVGIcon svgIcon = (SVGIcon) icon;

                final SVGImage svgImage = new SVGImage(svgIcon.getUrl(), 18, 18);
                svgImage.setStyleName(resources.style().icon());


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

                final Element elem = svgImage.getElement();
                element.appendChild(elem);
            }
        }

        label = DOM.createDiv();
        label.setClassName(resources.style().text());
        label.setInnerText(text);
        element.appendChild(label);

        close = DOM.createDiv();
        close.setClassName(resources.style().close());
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
            element.addClassName(resources.style().selected());
        } else {
            element.removeClassName(resources.style().selected());
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
        label.setInnerText(text);
    }

    public String getText() {
        return label.getInnerText();
    }

    @Override
    protected void setHover(final boolean hover) {
        if (hover) {
            element.addClassName(resources.style().hover());
        } else {
            element.removeClassName(resources.style().hover());
        }
    }

    @Override
    protected Element getCloseElement() {
        return close;
    }
}
