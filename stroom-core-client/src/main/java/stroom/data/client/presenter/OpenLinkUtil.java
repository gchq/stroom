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

package stroom.data.client.presenter;

import stroom.feed.client.OpenFeedEvent;
import stroom.pipeline.shared.SourceLocation;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.NullSafe;
import stroom.widget.util.client.ElementUtil;
import stroom.widget.util.client.SvgImageUtil;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.google.gwt.safehtml.client.SafeHtmlTemplates;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.user.client.ui.Widget;

public class OpenLinkUtil {

    private static final String HOVER_ICON_CONTAINER_CLASS_NAME = "hoverIconContainer";
    private static final String HOVER_ICON_CLASS_NAME = "hoverIcon";
    private static final String ICON_CLASS_NAME = "svgIcon";
    private static final String LINK_CONTAINER_CLASS_NAME = "docRefLinkContainer";
    private static final String LINK_OPEN_CLASS_NAME = "docRefLinkOpen";
    private static final String LINK_TEXT_CLASS_NAME = "docRefLinkText";
    private static final String LINK_ID_ATTRIBUTE = "link-id";
    private static final String LINK_TYPE_ATTRIBUTE = "link-type";

    private static final Template TEMPLATE;

    static {
        TEMPLATE = GWT.create(Template.class);
    }

    public static SafeHtml render(final String value, final LinkType linkType) {
        return render(value, linkType,
                TEMPLATE.div(LINK_TEXT_CLASS_NAME, SafeHtmlUtils.fromString(value)));
    }

    public static SafeHtml render(final String value, final LinkType linkType, final SafeHtml innerHtml) {
        final SafeHtmlBuilder sb = new SafeHtmlBuilder();

        final String containerClasses = String.join(" ",
                HOVER_ICON_CONTAINER_CLASS_NAME,
                LINK_CONTAINER_CLASS_NAME);
        sb.appendHtmlConstant("<div class=\"" + containerClasses + "\" "
                              + LINK_TYPE_ATTRIBUTE + "=\"" + linkType.toString().toLowerCase() + "\" "
                              + LINK_ID_ATTRIBUTE + "=\"" + value + "\">");

        sb.append(innerHtml);

        final SafeHtml openIcon = SvgImageUtil.toSafeHtml(
                SvgImage.OPEN,
                ICON_CLASS_NAME,
                LINK_OPEN_CLASS_NAME,
                HOVER_ICON_CLASS_NAME);
        sb.append(TEMPLATE.divWithToolTip("Open " + linkType.displayName, openIcon));

        sb.appendHtmlConstant("</div>");

        return sb.toSafeHtml();
    }

    public static void addClickHandler(final HasHandlers handlers, final Widget widget) {
        widget.addDomHandler(event -> {
            final Element element = Element.as(event.getNativeEvent().getEventTarget());
            if (ElementUtil.hasClassName(element, LINK_OPEN_CLASS_NAME, 5)) {
                final Element parent = ElementUtil.findParent(element, LINK_CONTAINER_CLASS_NAME, 5);
                if (parent != null) {
                    final String linkId = parent.getAttribute(LINK_ID_ATTRIBUTE);
                    final String linkType = parent.getAttribute(LINK_TYPE_ATTRIBUTE);

                    if (NullSafe.isNonBlankString(linkId) && NullSafe.isNonBlankString(linkType)) {
                        LinkType type = null;
                        try {
                            type = LinkType.valueOf(linkType.toUpperCase());
                        } catch (final IllegalArgumentException e) {
                            // Unknown type.
                        }

                        if (type != null) {
                            switch (type) {
                                case STREAM:
                                    final SourceLocation location = SourceLocation
                                            .builder(Long.parseLong(linkId))
                                            .build();
                                    ShowDataEvent.fire(handlers, location, DataViewType.INFO, DisplayMode.STROOM_TAB);
                                    break;

                                case FEED:
                                    OpenFeedEvent.fire(handlers, linkId, true);
                                    break;
                            }
                        }
                    }
                }
            }
        }, ClickEvent.getType());
    }

    interface Template extends SafeHtmlTemplates {

        @Template("<div class=\"{0}\">{1}</div>")
        SafeHtml div(String cssClass, SafeHtml content);

        @Template("<div title=\"{0}\">{1}</div>")
        SafeHtml divWithToolTip(String title, SafeHtml content);
    }

    public enum LinkType {
        FEED("Feed"),
        STREAM("Stream");

        private final String displayName;

        LinkType(final String displayName) {
            this.displayName = displayName;
        }
    }
}
