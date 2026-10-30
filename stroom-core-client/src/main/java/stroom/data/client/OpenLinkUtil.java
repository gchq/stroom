/*
 * Copyright 2025 Crown Copyright
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

package stroom.data.client;

import stroom.data.client.event.ShowDataEvent;
import stroom.feed.client.event.OpenFeedEvent;
import stroom.pipeline.shared.SourceLocation;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.NullSafe;
import stroom.widget.util.client.ElementUtil;
import stroom.widget.util.client.SvgImageUtil;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.event.shared.HasHandlers;
import com.google.gwt.safehtml.client.SafeHtmlTemplates;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.user.client.ui.Widget;

public class OpenLinkUtil {

    private static final String HOVER_ICON_CONTAINER_CLASS_NAME = "hoverIconContainer";
    // The icon shown only on a hover, or with the keyboard on the row (doc-ref-link.css)
    private static final String HOVER_ICON_CLASS_NAME = "doc-ref-link__icon doc-ref-link__icon--hover";
    private static final String ICON_CLASS_NAME = "icon-wrap";
    private static final String LINK_CONTAINER_CLASS_NAME = "doc-ref-link";
    private static final String LINK_OPEN_CLASS_NAME = "doc-ref-link__open";
    private static final String LINK_ID_ATTRIBUTE = "link-id";
    private static final String LINK_TYPE_ATTRIBUTE = "link-type";

    private static final Template TEMPLATE;

    static {
        TEMPLATE = GWT.create(Template.class);
    }

    public static SafeHtml render(final String value, final LinkType linkType) {
        return render(value, linkType,
                TEMPLATE.div(SafeHtmlUtils.fromString(value)));
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

    /// Opens the stream or feed of a link [#render] wrote, when its open icon is clicked anywhere in
    /// `widget`. Add it once for the widget (e.g. in its owner's constructor), not each time its
    /// content changes, or one click opens the link as many times as it was added.
    ///
    /// @param handlers Where the open events are fired from.
    /// @param widget   The widget the links are shown in.
    /// @return The click handler's registration, for its owner to own.
    public static HandlerRegistration addClickHandler(final HasHandlers handlers, final Widget widget) {
        return widget.addDomHandler(event -> {
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

        @Template("<div>{0}</div>")
        SafeHtml div(SafeHtml content);

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
