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

package stroom.data.client.component;

import stroom.data.client.CopyTextUtil;
import stroom.docstore.shared.DocumentType;
import stroom.feed.shared.FeedDoc;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.NullSafe;
import stroom.widget.datagrid.client.EventCell;
import stroom.widget.datagrid.client.HasContextMenus;
import stroom.widget.menu.client.IconMenuItem;
import stroom.widget.menu.client.IconParentMenuItem;
import stroom.widget.menu.client.Item;
import stroom.widget.menu.client.MenuItem;
import stroom.widget.util.client.Clipboard;
import stroom.widget.util.client.ElementUtil;
import stroom.widget.util.client.MouseUtil;
import stroom.widget.util.client.RowActions;
import stroom.widget.util.client.SafeHtmlUtil;
import stroom.widget.util.client.SvgImageUtil;

import com.google.gwt.cell.client.AbstractCell;
import com.google.gwt.cell.client.ValueUpdater;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.BrowserEvents;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.safehtml.client.SafeHtmlTemplates;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.view.client.CellPreviewEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/// A cell showing a feed by its name, with the feed's icon and hover buttons that copy its name and
/// open it, and the grid's context menu items to open it and copy its details. Opening and copying
/// its link are its owner's: [Builder#onOpen] and [Builder#onCopyUrl] (see `RefCellsController`).
///
/// @param <T_ROW> The row's type.
public class FeedRefCell<T_ROW> extends AbstractCell<T_ROW>
        implements EventCell, HasContextMenus<T_ROW> {

    private static final String ICON_CLASS_NAME = "icon-wrap";
    private static final String COPY_CLASS_NAME = "doc-ref-link__copy";
    private static final String OPEN_CLASS_NAME = "doc-ref-link__open";
    private static final String HOVER_ICON_CONTAINER_CLASS_NAME = "hoverIconContainer";
    // The icons shown only on a hover, or with the keyboard on the row (doc-ref-link.css)
    private static final String HOVER_ICON_CLASS_NAME = "doc-ref-link__icon doc-ref-link__icon--hover";

    private final boolean showIcon;
    private final Function<T_ROW, SafeHtml> cellTextFunction;
    private final Function<T_ROW, String> nameFunction;
    private final Function<T_ROW, String> cssClassFunction;
    private final Consumer<String> onOpen;
    private final Consumer<String> onCopyUrl;

    private static volatile Template template;

    /**
     * @param showIcon         Set to true to show the type icon next to the text
     * @param cellTextFunction Function to provide the cell 'text' in HTML form.
     * @param nameFunction     Function to provide a doc ref if the value can give us one.
     * @param cssClassFunction Function to provide additional css class names.
     * @param onOpen           What opens a feed by its name, or null if it can't be opened.
     * @param onCopyUrl        What copies a feed's link by its name, or null for no link.
     */
    private FeedRefCell(final boolean showIcon,
                        final Function<T_ROW, SafeHtml> cellTextFunction,
                        final Function<T_ROW, String> nameFunction,
                        final Function<T_ROW, String> cssClassFunction,
                        final Consumer<String> onOpen,
                        final Consumer<String> onCopyUrl) {
        super(BrowserEvents.MOUSEDOWN, BrowserEvents.KEYDOWN);
        this.showIcon = showIcon;
        this.cellTextFunction = cellTextFunction;
        this.nameFunction = nameFunction;
        this.cssClassFunction = cssClassFunction;
        this.onOpen = onOpen;
        this.onCopyUrl = onCopyUrl;

        if (template == null) {
            template = GWT.create(Template.class);
        }
    }

    @Override
    public boolean isConsumed(final CellPreviewEvent<?> event) {
        final NativeEvent nativeEvent = event.getNativeEvent();
        if (BrowserEvents.MOUSEDOWN.equals(nativeEvent.getType()) && MouseUtil.isPrimary(nativeEvent)) {
            final Element element = nativeEvent.getEventTarget().cast();
            return ElementUtil.hasClassName(element, COPY_CLASS_NAME, 5) ||
                   ElementUtil.hasClassName(element, OPEN_CLASS_NAME, 5);
        }
        return false;
    }

    @Override
    public void onBrowserEvent(final Context context,
                               final Element parent,
                               final T_ROW value,
                               final NativeEvent event,
                               final ValueUpdater<T_ROW> valueUpdater) {
        // The right-click menu is the grid's context menu, which adds this cell's items (see
        // getContextMenuItems). The cell showing its own menu as well opened two menus
        if (RowActions.isKeyPress(event)) {
            if (NullSafe.get(value, nameFunction) != null) {
                // Enter or Space on the open button; the button's own click would come too
                event.preventDefault();
                onEnterKeyDown(context, parent, value, event, valueUpdater);
            }
        } else if (BrowserEvents.MOUSEDOWN.equals(event.getType()) && MouseUtil.isPrimary(event)) {
            onEnterKeyDown(context, parent, value, event, valueUpdater);
        }
    }

    /// The items this cell adds to the grid's context menu for a feed: 'Open Feed' and 'Copy Feed
    /// Details'.
    ///
    /// @param context The cell's context.
    /// @param value   The row.
    /// @return The items, or null for a row with no feed.
    @Override
    public List<Item> getContextMenuItems(final Context context, final T_ROW value) {
        final String name = NullSafe.get(value, nameFunction);
        if (name == null) {
            return null;
        }
        final String type = FeedDoc.DOCUMENT_TYPE.getDisplayType();
        final List<Item> menuItems = new ArrayList<>();
        int priority = 1;
        if (onOpen != null) {
            menuItems.add(new IconMenuItem.Builder()
                    .priority(priority++)
                    .icon(SvgImage.OPEN)
                    .text("Open " + type)
                    .command(() -> onOpen.accept(name))
                    .build());
        }
        menuItems.add(createCopyAsMenuItem(name, type, priority));
        return menuItems;
    }

    @Override
    protected void onEnterKeyDown(final Context context,
                                  final Element parent,
                                  final T_ROW value,
                                  final NativeEvent event,
                                  final ValueUpdater<T_ROW> valueUpdater) {
        final Element element = event.getEventTarget().cast();
        final String name = NullSafe.get(value, nameFunction);
        if (name != null) {
            if (ElementUtil.hasClassName(element, COPY_CLASS_NAME, 5)) {
                CopyTextUtil.keepSelection(event);
                final String text = cellTextFunction.apply(value).asString();
                if (text != null) {
                    Clipboard.writeText(text);
                }
            } else if (onOpen != null && ElementUtil.hasClassName(element, OPEN_CLASS_NAME, 5)) {
                CopyTextUtil.keepSelection(event);
                onOpen.accept(name);
            }
        } else {
            final String text = cellTextFunction.apply(value).asString();
            if (ElementUtil.hasClassName(element, CopyTextUtil.COPY_CLASS_NAME, 5)) {
                CopyTextUtil.keepSelection(event);
                if (text != null) {
                    Clipboard.writeText(text);
                }
            }
        }
    }

    @Override
    public void render(final Context context, final T_ROW value, final SafeHtmlBuilder sb) {
        if (value == null) {
            sb.append(SafeHtmlUtils.EMPTY_SAFE_HTML);
        } else {
            final String name = NullSafe.get(value, nameFunction);
            final SafeHtml cellHtmlText = cellTextFunction.apply(value);
            final String cssClasses = NullSafe.string(cssClassFunction.apply(value));
            final SafeHtml textDiv = template.div(cssClasses, cellHtmlText);

            final String containerClasses = String.join(
                    " ",
                    HOVER_ICON_CONTAINER_CLASS_NAME,
                    "doc-ref-link");

            sb.appendHtmlConstant("<div class=\"" + containerClasses + "\">");
            if (name != null && showIcon) {
                final DocumentType documentType = FeedDoc.DOCUMENT_TYPE;
                if (documentType != null) {
                    final SvgImage svgImage = documentType.getIcon();
                    final SafeHtml iconDiv = SvgImageUtil.toSafeHtml(
                            documentType.getDisplayType(),
                            svgImage,
                            ICON_CLASS_NAME,
                            "doc-ref-link__icon");
                    sb.append(iconDiv);
                }
            }

            sb.append(textDiv);

            // Add copy and open links.
            if (name != null) {
                // This DocRefCell gets used for pipeline props which sometimes are a docRef
                // and other times just a simple string
                final SafeHtml copy = SvgImageUtil.toSafeHtml(
                        SvgImage.COPY,
                        ICON_CLASS_NAME,
                        COPY_CLASS_NAME,
                        HOVER_ICON_CLASS_NAME);
                sb.append(template.divWithToolTip(
                        "Copy name '" + name + "' to clipboard",
                        copy));

                if (onOpen != null) {
                    // A button the keyboard reaches from its row (a row action)
                    final SafeHtml open = SvgImageUtil.toSafeHtml(
                            SvgImage.OPEN,
                            ICON_CLASS_NAME,
                            HOVER_ICON_CLASS_NAME,
                            RowActions.ICON_CLASS_NAME);
                    sb.append(RowActions.button(
                            OPEN_CLASS_NAME,
                            "Open " + FeedDoc.TYPE + " " + name,
                            open));
                }
            }

            sb.appendHtmlConstant("</div>");
        }
    }

    private MenuItem createCopyAsMenuItem(final String name,
                                          final String type,
                                          final int priority) {
        final List<Item> children = createCopyAsChildMenuItems(name);
        return new IconParentMenuItem.Builder()
                .priority(priority)
                .icon(SvgImage.COPY)
                .text("Copy " + type + " Details")
                .children(children)
                .enabled(true)
                .build();
    }

    private List<Item> createCopyAsChildMenuItems(final String name) {
        // If a user has VIEW on a doc they will also see (but not have VIEW) all ancestor
        // docs, so we need to only allow 'copy name' for these 'see but not view' cases.
        // Thus, totalCount may be bigger than readableCount
        final List<Item> childMenuItems = new ArrayList<>();
        int priority = 1;
        if (NullSafe.isNonBlankString(name)) {
            childMenuItems.add(new IconMenuItem.Builder()
                    .priority(priority++)
                    .icon(SvgImage.COPY)
                    .text("Name")
                    .enabled(true)
                    .command(() -> Clipboard.writeText(name))
                    .build());
        }
        if (onCopyUrl != null) {
            childMenuItems.add(createCopyLinkMenuItem(name, priority++));
        }
        return childMenuItems;
    }

    private MenuItem createCopyLinkMenuItem(final String name, final int priority) {
        return new IconMenuItem.Builder()
                .priority(priority)
                .icon(SvgImage.SHARE)
                .text("Link")
                .command(() -> onCopyUrl.accept(name))
                .build();
    }


    // --------------------------------------------------------------------------------


    /**
     * Use {@link SafeHtmlUtil#getTemplate()} instead
     */
    @Deprecated
    interface Template extends SafeHtmlTemplates {

        @Template("<div class=\"{0}\">{1}</div>")
        SafeHtml div(String cssClass, SafeHtml content);

        @Template("<div title=\"{0}\">{1}</div>")
        SafeHtml divWithToolTip(String title, SafeHtml content);
    }


    // --------------------------------------------------------------------------------


    public static class Builder<T> {

        private boolean showIcon = false;
        private Function<T, SafeHtml> cellTextFunction;
        private Function<T, String> nameFunction;
        private Function<T, String> cssClassFunction;
        private Consumer<String> onOpen;
        private Consumer<String> onCopyUrl;

        /// Sets what opens a row's feed, from its open button or its 'Open' menu item, e.g. firing
        /// the app's open feed event (see `RefCellsController`).
        ///
        /// @param onOpen What opens a feed, given its name, or null (the default) for a cell that
        ///               can't open one: it shows no open button or menu item.
        /// @return This builder.
        public Builder<T> onOpen(final Consumer<String> onOpen) {
            this.onOpen = onOpen;
            return this;
        }

        /// Sets what copies a link to a row's feed, from its 'Copy Feed Details > Link' menu item,
        /// e.g. firing the app's copy feed URL event (see `RefCellsController`).
        ///
        /// @param onCopyUrl What copies a feed's link, given its name, or null (the default) for no
        ///                  'Link' item.
        /// @return This builder.
        public Builder<T> onCopyUrl(final Consumer<String> onCopyUrl) {
            this.onCopyUrl = onCopyUrl;
            return this;
        }

        public Builder<T> showIcon(final boolean showIcon) {
            this.showIcon = showIcon;
            return this;
        }

        public Builder<T> cellTextFunction(final Function<T, SafeHtml> cellTextFunction) {
            this.cellTextFunction = cellTextFunction;
            return this;
        }

        public Builder<T> nameFunction(final Function<T, String> nameFunction) {
            this.nameFunction = nameFunction;
            return this;
        }

        public Builder<T> cssClassFunction(final Function<T, String> cssClassFunction) {
            this.cssClassFunction = cssClassFunction;
            return this;
        }

        public FeedRefCell<T> build() {
            if (nameFunction == null) {
                nameFunction = v -> null;
            }
            if (cellTextFunction == null) {
                cellTextFunction = v -> {
                    final String name = nameFunction.apply(v);
                    if (name == null) {
                        return SafeHtmlUtils.EMPTY_SAFE_HTML;
                    } else {
                        return SafeHtmlUtils.fromString(name);
                    }
                };
            }
            if (cssClassFunction == null) {
                cssClassFunction = v -> null;
            }

            return new FeedRefCell<>(
                    showIcon,
                    cellTextFunction,
                    nameFunction,
                    cssClassFunction,
                    onOpen,
                    onCopyUrl);
        }
    }
}
