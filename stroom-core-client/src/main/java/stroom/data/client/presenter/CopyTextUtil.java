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

import stroom.svg.shared.SvgImage;
import stroom.util.client.ClipboardUtil;
import stroom.util.shared.NullSafe;
import stroom.widget.menu.client.presenter.IconMenuItem;
import stroom.widget.menu.client.presenter.Item;
import stroom.widget.menu.client.presenter.ShowMenuEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.util.client.ElementUtil;
import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.HtmlBuilder.Attribute;
import stroom.widget.util.client.MouseUtil;
import stroom.widget.util.client.SvgImageUtil;

import com.google.gwt.dom.client.BrowserEvents;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class CopyTextUtil {

    public static final String ICON_NAME = "svgIcon";
    public static final String COPY_CLASS_NAME = "docRefLinkCopy";
    public static final String INSERT_CLASS_NAME = "docRefLinkInsert";
    private static final String HOVER_ICON_CONTAINER_CLASS_NAME = "hoverIconContainer";
    private static final String HOVER_ICON_CLASS_NAME = "hoverIcon";
    private static final int TRUNCATE_THRESHOLD = 30;

    public static SafeHtml render(final String value,
                                  final boolean allowInsert) {
        final HtmlBuilder sb = new HtmlBuilder();
        render(value, sb, allowInsert);
        return sb.toSafeHtml();
    }

    public static void render(final String value,
                              final HtmlBuilder sb,
                              final boolean allowInsert) {
        if (value == null) {
            sb.append(SafeHtmlUtils.EMPTY_SAFE_HTML);
        } else {
            render(value, SafeHtmlUtils.fromString(value), sb, allowInsert);
        }
    }

    public static void render(final String value,
                              final SafeHtml safeHtml,
                              final HtmlBuilder hb,
                              final boolean allowInsert) {
        if (value == null) {
            hb.append(SafeHtmlUtils.EMPTY_SAFE_HTML);
        } else {
            final String containerClasses = String.join(
                    " ",
                    HOVER_ICON_CONTAINER_CLASS_NAME,
                    "docRefLinkContainer");
            hb.div(div -> {
                div.div(d -> d.append(safeHtml),
                        Attribute.className("docRefLinkText"));

                NullSafe.consumeNonBlankString(value, str -> {
                    final SafeHtml copy = SvgImageUtil.toSafeHtml(
                            SvgImage.COPY,
                            ICON_NAME,
                            COPY_CLASS_NAME,
                            HOVER_ICON_CLASS_NAME);
                    // Cell values could be huge so truncate big ones
                    final String truncatedStr = str.length() > TRUNCATE_THRESHOLD
                            ? (str.substring(0, TRUNCATE_THRESHOLD) + "...")
                            : str;
                    div.div(d -> d.append(copy),
                            Attribute.title("Copy value '" + truncatedStr + "' to clipboard"));

                    if (allowInsert) {
                        final SafeHtml insert = SvgImageUtil.toSafeHtml(
                                SvgImage.INSERT,
                                ICON_NAME,
                                INSERT_CLASS_NAME,
                                HOVER_ICON_CLASS_NAME);
                        div.div(d -> d.append(insert),
                                Attribute.title("Insert value '" + truncatedStr + "'"));
                    }
                });
            }, Attribute.className(containerClasses));
        }
    }

    public static void onClick(final NativeEvent e,
                               final HasHandlers hasHandlers) {
        onClick(e, hasHandlers, null);
    }

    public static void onClick(final NativeEvent e,
                               final HasHandlers hasHandlers,
                               final Consumer<String> insertHandler) {
        if (BrowserEvents.MOUSEDOWN.equals(e.getType())) {
            final Element element = e.getEventTarget().cast();
            final Element container = ElementUtil.findParent(
                    element, "docRefLinkContainer", 5);
            final String text = NullSafe.get(container, Element::getInnerText);
            if (NullSafe.isNonBlankString(text)) {
                if (MouseUtil.isPrimary(e)) {
                    final Element copy = ElementUtil.findParent(element, CopyTextUtil.COPY_CLASS_NAME, 5);
                    if (copy != null) {
                        ClipboardUtil.copy(text.trim());
                    } else if (insertHandler != null) {
                        final Element insert = ElementUtil
                                .findParent(element, CopyTextUtil.INSERT_CLASS_NAME, 5);
                        if (insert != null) {
                            insertHandler.accept(text.trim());
                        }
                    }
                } else {
                    final List<Item> menuItems = new ArrayList<>();
                    menuItems.add(new IconMenuItem.Builder()
                            .priority(1)
                            .icon(SvgImage.COPY)
                            .text("Copy")
                            .command(() -> ClipboardUtil.copy(text.trim()))
                            .build());
                    if (insertHandler != null) {
                        menuItems.add(new IconMenuItem.Builder()
                                .priority(2)
                                .icon(SvgImage.INSERT)
                                .text("Insert")
                                .command(() -> insertHandler.accept(text.trim()))
                                .build());
                    }
                    ShowMenuEvent
                            .builder()
                            .items(menuItems)
                            .popupPosition(new PopupPosition(e.getClientX(), e.getClientY()))
                            .fire(hasHandlers);
                }
            }
        }
    }
}
