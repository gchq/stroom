/*
 * Copyright 2024 Crown Copyright
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

package stroom.widget.datagrid.client;

import stroom.svg.shared.SvgImage;
import stroom.util.shared.NullSafe;
import stroom.widget.menu.client.IconMenuItem;
import stroom.widget.menu.client.Item;
import stroom.widget.menu.client.MenuRequest;
import stroom.widget.menu.client.ShowMenuEvent;
import stroom.widget.popup.client.PopupPosition;
import stroom.widget.util.client.Clipboard;
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

    public static final String ICON_NAME = "icon-wrap";
    public static final String COPY_CLASS_NAME = "doc-ref-link__copy";
    public static final String INSERT_CLASS_NAME = "doc-ref-link__insert";
    private static final String HOVER_ICON_CONTAINER_CLASS_NAME = "hoverIconContainer";
    // The icons shown only on a hover, or with the keyboard on the row (doc-ref-link.css)
    private static final String HOVER_ICON_CLASS_NAME = "doc-ref-link__icon doc-ref-link__icon--hover";
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
                    "doc-ref-link");
            hb.div(div -> {
                // The text, then its icons
                div.div(d -> d.append(safeHtml));

                NullSafe.consumeNonBlankString(value, str -> {
                    final SafeHtml copy = SvgImageUtil.toSafeHtml(
                            SvgImage.COPY,
                            ICON_NAME,
                            COPY_CLASS_NAME,
                            HOVER_ICON_CLASS_NAME);
                    // Cell values could be huge so truncate big ones
                    final String truncatedStr = truncate(str);
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

    /// The accessible name of the menu for a value, e.g. "Actions for value 'abc'", with big
    /// values cut short.
    ///
    /// @param value The value the menu acts on.
    /// @return The menu's name.
    public static String getValueMenuName(final String value) {
        return "Actions for value '" + truncate(NullSafe.trim(value)) + "'";
    }

    private static String truncate(final String str) {
        return str.length() > TRUNCATE_THRESHOLD
                ? (str.substring(0, TRUNCATE_THRESHOLD) + "...")
                : str;
    }

    /// Stops a mouse press on an icon that acts on the press (copy, insert, open) starting a text
    /// selection, which the slightest drag would stretch across the page (#5111). A key press is
    /// left alone.
    ///
    /// @param event The event the icon acts on.
    public static void keepSelection(final NativeEvent event) {
        if (BrowserEvents.MOUSEDOWN.equals(event.getType())) {
            event.preventDefault();
        }
    }

    public static void onClick(final NativeEvent e,
                               final HasHandlers hasHandlers) {
        onClick(e, hasHandlers, null);
    }

    /// Copies a value, as [#onClick(NativeEvent, HasHandlers)] does, for a widget with no event bus
    /// (e.g. a trace's overview): its menu (a secondary click's Copy) is asked for with a
    /// [MenuRequest] from the element clicked, which the page's relay shows.
    ///
    /// @param e The mouse down on a value rendered by [#render].
    public static void onClick(final NativeEvent e) {
        onClick(e, null, null);
    }

    public static void onClick(final NativeEvent e,
                               final HasHandlers hasHandlers,
                               final Consumer<String> insertHandler) {
        if (BrowserEvents.MOUSEDOWN.equals(e.getType())) {
            final Element element = e.getEventTarget().cast();
            final Element container = ElementUtil.findParent(
                    element, "doc-ref-link", 5);
            final String text = NullSafe.get(container, Element::getInnerText);
            if (NullSafe.isNonBlankString(text)) {
                if (MouseUtil.isPrimary(e)) {
                    final Element copy = ElementUtil.findParent(element, CopyTextUtil.COPY_CLASS_NAME, 5);
                    if (copy != null) {
                        keepSelection(e);
                        Clipboard.writeText(text.trim());
                    } else if (insertHandler != null) {
                        final Element insert = ElementUtil
                                .findParent(element, CopyTextUtil.INSERT_CLASS_NAME, 5);
                        if (insert != null) {
                            keepSelection(e);
                            insertHandler.accept(text.trim());
                        }
                    }
                } else {
                    final List<Item> menuItems = new ArrayList<>();
                    menuItems.add(new IconMenuItem.Builder()
                            .priority(1)
                            .icon(SvgImage.COPY)
                            .text("Copy")
                            .command(() -> Clipboard.writeText(text.trim()))
                            .build());
                    if (insertHandler != null) {
                        menuItems.add(new IconMenuItem.Builder()
                                .priority(2)
                                .icon(SvgImage.INSERT)
                                .text("Insert into Expression")
                                .command(() -> insertHandler.accept(text.trim()))
                                .build());
                    }
                    final PopupPosition popupPosition = new PopupPosition(e.getClientX(), e.getClientY());
                    if (hasHandlers != null) {
                        ShowMenuEvent
                                .builder()
                                .name(getValueMenuName(text))
                                .items(menuItems)
                                .popupPosition(popupPosition)
                                .fire(hasHandlers);
                    } else {
                        MenuRequest.fire(element, getValueMenuName(text), menuItems, popupPosition);
                    }
                }
            }
        }
    }
}
