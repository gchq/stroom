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

import stroom.data.grid.client.EventCell;
import stroom.docstore.shared.DocumentType;
import stroom.feed.client.CopyFeedUrlEvent;
import stroom.feed.client.OpenFeedEvent;
import stroom.feed.shared.FeedDoc;
import stroom.svg.shared.SvgImage;
import stroom.util.client.ClipboardUtil;
import stroom.util.shared.NullSafe;
import stroom.widget.menu.client.presenter.IconMenuItem;
import stroom.widget.menu.client.presenter.IconParentMenuItem;
import stroom.widget.menu.client.presenter.Item;
import stroom.widget.menu.client.presenter.MenuItem;
import stroom.widget.menu.client.presenter.ShowMenuEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.util.client.ElementUtil;
import stroom.widget.util.client.MouseUtil;
import stroom.widget.util.client.SafeHtmlUtil;
import stroom.widget.util.client.SvgImageUtil;

import com.google.gwt.cell.client.AbstractCell;
import com.google.gwt.cell.client.ValueUpdater;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.BrowserEvents;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.google.gwt.safehtml.client.SafeHtmlTemplates;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.view.client.CellPreviewEvent;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class FeedRefCell<T_ROW> extends AbstractCell<T_ROW>
        implements HasHandlers, EventCell {

    private static final String ICON_CLASS_NAME = "svgIcon";
    private static final String COPY_CLASS_NAME = "docRefLinkCopy";
    private static final String OPEN_CLASS_NAME = "docRefLinkOpen";
    private static final String HOVER_ICON_CONTAINER_CLASS_NAME = "hoverIconContainer";
    private static final String HOVER_ICON_CLASS_NAME = "hoverIcon";

    private final EventBus eventBus;
    private final boolean showIcon;
    private final Function<T_ROW, SafeHtml> cellTextFunction;
    private final Function<T_ROW, String> nameFunction;
    private final Function<T_ROW, String> cssClassFunction;

    private static volatile Template template;

    /**
     * @param showIcon         Set to true to show the type icon next to the text
     * @param cellTextFunction Function to provide the cell 'text' in HTML form.
     * @param nameFunction     Function to provide a doc ref if the value can give us one.
     * @param cssClassFunction Function to provide additional css class names.
     */
    private FeedRefCell(final EventBus eventBus,
                        final boolean showIcon,
                        final Function<T_ROW, SafeHtml> cellTextFunction,
                        final Function<T_ROW, String> nameFunction,
                        final Function<T_ROW, String> cssClassFunction) {
        super(BrowserEvents.MOUSEDOWN);
        this.eventBus = eventBus;
        this.showIcon = showIcon;
        this.cellTextFunction = cellTextFunction;
        this.nameFunction = nameFunction;
        this.cssClassFunction = cssClassFunction;

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
        super.onBrowserEvent(context, parent, value, event, valueUpdater);
        final String name = NullSafe.get(value, nameFunction);
        if (name != null) {
            if (BrowserEvents.MOUSEDOWN.equals(event.getType())) {
                if (MouseUtil.isPrimary(event)) {
                    onEnterKeyDown(context, parent, value, event, valueUpdater);
                } else {
                    final String type;
                    final DocumentType documentType = FeedDoc.DOCUMENT_TYPE;
                    type = documentType.getDisplayType();

                    final List<Item> menuItems = new ArrayList<>();
                    int priority = 1;
                    menuItems.add(new IconMenuItem.Builder()
                            .priority(priority++)
                            .icon(SvgImage.OPEN)
                            .text("Open " + type)
                            .command(() -> OpenFeedEvent.fire(this, name, true))
                            .build());
                    menuItems.add(createCopyAsMenuItem(name, priority++));

                    ShowMenuEvent
                            .builder()
                            .items(menuItems)
                            .popupPosition(new PopupPosition(event.getClientX(), event.getClientY()))
                            .fire(this);
                }
            }
        } else {
            final String text = cellTextFunction.apply(value).asString();
            if (BrowserEvents.MOUSEDOWN.equals(event.getType())) {
                if (MouseUtil.isPrimary(event)) {
                    final Element element = event.getEventTarget().cast();
                    if (ElementUtil.hasClassName(element, CopyTextUtil.COPY_CLASS_NAME, 5)) {
                        if (text != null) {
                            ClipboardUtil.copy(text);
                        }
                    }
                } else if (NullSafe.isNonBlankString(text)) {
                    final List<Item> menuItems = new ArrayList<>();
                    menuItems.add(new IconMenuItem.Builder()
                            .priority(1)
                            .icon(SvgImage.COPY)
                            .text("Copy")
                            .command(() -> ClipboardUtil.copy(text))
                            .build());
                    ShowMenuEvent
                            .builder()
                            .items(menuItems)
                            .popupPosition(new PopupPosition(event.getClientX(), event.getClientY()))
                            .fire(this);
                }
            }
        }
    }

    @Override
    public void fireEvent(final GwtEvent<?> gwtEvent) {
        eventBus.fireEvent(gwtEvent);
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
                final String text = cellTextFunction.apply(value).asString();
                if (text != null) {
                    ClipboardUtil.copy(text);
                }
            } else if (ElementUtil.hasClassName(element, OPEN_CLASS_NAME, 5)) {
                OpenFeedEvent.fire(this, name, true);
            }
        } else {
            final String text = cellTextFunction.apply(value).asString();
            if (ElementUtil.hasClassName(element, CopyTextUtil.COPY_CLASS_NAME, 5)) {
                if (text != null) {
                    ClipboardUtil.copy(text);
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
            String cssClasses = "docRefLinkText";
            final String additionalClasses = cssClassFunction.apply(value);
            if (additionalClasses != null) {
                cssClasses += " " + additionalClasses;
            }
            final SafeHtml textDiv = template.div(cssClasses, cellHtmlText);

            final String containerClasses = String.join(
                    " ",
                    HOVER_ICON_CONTAINER_CLASS_NAME,
                    "docRefLinkContainer");

            sb.appendHtmlConstant("<div class=\"" + containerClasses + "\">");
            if (name != null && showIcon) {
                final DocumentType documentType = FeedDoc.DOCUMENT_TYPE;
                if (documentType != null) {
                    final SvgImage svgImage = documentType.getIcon();
                    final SafeHtml iconDiv = SvgImageUtil.toSafeHtml(
                            documentType.getDisplayType(),
                            svgImage,
                            ICON_CLASS_NAME,
                            "docRefLinkIcon");
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

                final SafeHtml open = SvgImageUtil.toSafeHtml(
                        SvgImage.OPEN,
                        ICON_CLASS_NAME,
                        OPEN_CLASS_NAME,
                        HOVER_ICON_CLASS_NAME);
                sb.append(template.divWithToolTip(
                        "Open " + FeedDoc.TYPE + " " + name + " in new tab",
                        open));
            }

            sb.appendHtmlConstant("</div>");
        }
    }

    private MenuItem createCopyAsMenuItem(final String name,
                                          final int priority) {
        final List<Item> children = createCopyAsChildMenuItems(name);
        return new IconParentMenuItem.Builder()
                .priority(priority)
                .icon(SvgImage.COPY)
                .text("Copy As")
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
                    .text("Copy Name to Clipboard")
                    .enabled(true)
                    .command(() -> ClipboardUtil.copy(name))
                    .build());
        }
        childMenuItems.add(createCopyLinkMenuItem(name, priority++));
        return childMenuItems;
    }

    private MenuItem createCopyLinkMenuItem(final String name, final int priority) {
        return new IconMenuItem.Builder()
                .priority(priority)
                .icon(SvgImage.SHARE)
                .text("Copy Link to Clipboard")
                .command(() -> CopyFeedUrlEvent.fire(this, name))
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

        private EventBus eventBus;
        private boolean showIcon = false;
        private Function<T, SafeHtml> cellTextFunction;
        private Function<T, String> nameFunction;
        private Function<T, String> cssClassFunction;

        public Builder<T> eventBus(final EventBus eventBus) {
            this.eventBus = eventBus;
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
                    eventBus,
                    showIcon,
                    cellTextFunction,
                    nameFunction,
                    cssClassFunction);
        }
    }
}
