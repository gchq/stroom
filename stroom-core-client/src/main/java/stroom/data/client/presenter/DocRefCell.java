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

import stroom.core.client.UrlParameters;
import stroom.data.grid.client.EventCell;
import stroom.data.grid.client.HasContextMenus;
import stroom.docref.DocRef;
import stroom.docref.DocRef.DisplayType;
import stroom.docstore.shared.DocumentType;
import stroom.docstore.shared.DocumentTypeRegistry;
import stroom.document.client.event.OpenDocumentEvent;
import stroom.svg.shared.SvgImage;
import stroom.util.client.ClipboardUtil;
import stroom.util.shared.NullSafe;
import stroom.widget.menu.client.presenter.IconMenuItem;
import stroom.widget.menu.client.presenter.IconParentMenuItem;
import stroom.widget.menu.client.presenter.Item;
import stroom.widget.menu.client.presenter.MenuItem;
import stroom.widget.util.client.ElementUtil;
import stroom.widget.util.client.MouseUtil;
import stroom.widget.util.client.SvgImageUtil;
import stroom.widget.util.client.Templates;

import com.google.gwt.cell.client.AbstractCell;
import com.google.gwt.cell.client.ValueUpdater;
import com.google.gwt.dom.client.BrowserEvents;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.user.client.Window;
import com.google.gwt.view.client.CellPreviewEvent;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class DocRefCell<T_ROW> extends AbstractCell<T_ROW>
        implements HasHandlers, EventCell, HasContextMenus<T_ROW> {

    private static final String ICON_CLASS_NAME = "svgIcon";
    private static final String COPY_CLASS_NAME = "docRefLinkCopy";
    private static final String OPEN_CLASS_NAME = "docRefLinkOpen";
    private static final String HOVER_ICON_CONTAINER_CLASS_NAME = "hoverIconContainer";
    private static final String HOVER_ICON_CLASS_NAME = "hoverIcon";

    private final EventBus eventBus;
    private final boolean showIcon;
    private final Function<T_ROW, SafeHtml> cellTextFunction;
    private final Function<T_ROW, DocRef> docRefFunction;
    private final Function<T_ROW, String> cssClassFunction;

    /**
     * @param showIcon         Set to true to show the type icon next to the text
     * @param cellTextFunction Function to provide the cell 'text' in HTML form.
     * @param docRefFunction   Function to provide a doc ref if the value can give us one.
     * @param cssClassFunction Function to provide additional css class names.
     */
    private DocRefCell(final EventBus eventBus,
                       final boolean showIcon,
                       final Function<T_ROW, SafeHtml> cellTextFunction,
                       final Function<T_ROW, DocRef> docRefFunction,
                       final Function<T_ROW, String> cssClassFunction) {
        super(BrowserEvents.MOUSEDOWN);
        this.eventBus = eventBus;
        this.showIcon = showIcon;
        this.cellTextFunction = cellTextFunction;
        this.docRefFunction = docRefFunction;
        this.cssClassFunction = cssClassFunction;
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
        if (BrowserEvents.MOUSEDOWN.equals(event.getType())) {
            if (MouseUtil.isPrimary(event)) {
                onEnterKeyDown(context, parent, value, event, valueUpdater);
            }
        }
    }

    @Override
    public List<Item> getContextMenuItems(final Context context, final T_ROW value) {
        final List<Item> menuItems = new ArrayList<>();
        final DocRef docRef = NullSafe.get(value, docRefFunction);

        if (docRef != null) {
            final String type;
            final DocumentType documentType = DocumentTypeRegistry.get(docRef.getType());
            type = NullSafe.getOrElse(documentType, DocumentType::getDisplayType, docRef.getType());

            int priority = 1;
            menuItems.add(new IconMenuItem.Builder()
                    .priority(priority++)
                    .icon(SvgImage.OPEN)
                    .text("Open " + type)
                    .command(() -> OpenDocumentEvent.fire(this, docRef, true))
                    .build());
            menuItems.add(createCopyAsMenuItem(docRef, priority++));

        }
        return menuItems.isEmpty()
                ? null
                : menuItems;
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
        final DocRef docRef = NullSafe.get(value, docRefFunction);
        if (docRef != null) {
            if (ElementUtil.hasClassName(element, COPY_CLASS_NAME, 5)) {
                final String text = cellTextFunction.apply(value).asString();
                if (text != null) {
                    ClipboardUtil.copy(text);
                }
            } else if (ElementUtil.hasClassName(element, OPEN_CLASS_NAME, 5)) {
                OpenDocumentEvent.fire(this, docRef, true);
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
            final DocRef docRef = NullSafe.get(value, docRefFunction);
            final SafeHtml cellHtmlText = cellTextFunction.apply(value);
            String cssClasses = "docRefLinkText";
            final String additionalClasses = cssClassFunction.apply(value);
            if (additionalClasses != null) {
                cssClasses += " " + additionalClasses;
            }
            final SafeHtml textDiv = Templates.div(cssClasses, cellHtmlText);

            final String containerClasses = String.join(
                    " ",
                    HOVER_ICON_CONTAINER_CLASS_NAME,
                    "docRefLinkContainer");

            sb.appendHtmlConstant("<div class=\"" + containerClasses + "\">");
            if (docRef != null && showIcon) {
                final DocumentType documentType = DocumentTypeRegistry.get(docRef.getType());
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
            if (docRef != null) {
                // This DocRefCell gets used for pipeline props which sometimes are a docRef
                // and other times just a simple string
                final SafeHtml copy = SvgImageUtil.toSafeHtml(
                        SvgImage.COPY,
                        ICON_CLASS_NAME,
                        COPY_CLASS_NAME,
                        HOVER_ICON_CLASS_NAME);
                sb.append(Templates.divWithTitle(
                        "Copy name '" + docRef.getName() + "' to clipboard",
                        copy));

                if (docRef.getUuid() != null) {
                    final SafeHtml open = SvgImageUtil.toSafeHtml(
                            SvgImage.OPEN,
                            ICON_CLASS_NAME,
                            OPEN_CLASS_NAME,
                            HOVER_ICON_CLASS_NAME);
                    sb.append(Templates.divWithTitle(
                            "Open " + docRef.getType() + " " + docRef.getName() + " in new tab",
                            open));
                }
            }

            sb.appendHtmlConstant("</div>");
        }
    }

    private MenuItem createCopyAsMenuItem(final DocRef docRef,
                                          final int priority) {
        final List<Item> children = createCopyAsChildMenuItems(docRef);
        return new IconParentMenuItem.Builder()
                .priority(priority)
                .icon(SvgImage.COPY)
                .text("Copy As")
                .children(children)
                .enabled(true)
                .build();
    }

    private List<Item> createCopyAsChildMenuItems(final DocRef docRef) {
        // If a user has VIEW on a doc they will also see (but not have VIEW) all ancestor
        // docs, so we need to only allow 'copy name' for these 'see but not view' cases.
        // Thus, totalCount may be bigger than readableCount
        final List<Item> childMenuItems = new ArrayList<>();
        int priority = 1;
        if (NullSafe.isNonBlankString(docRef.getName())) {
            childMenuItems.add(new IconMenuItem.Builder()
                    .priority(priority++)
                    .icon(SvgImage.COPY)
                    .text("Copy Name to Clipboard")
                    .enabled(true)
                    .command(() -> ClipboardUtil.copy(docRef.getName()))
                    .build());
        }
        if (NullSafe.isNonBlankString(docRef.getUuid())) {
            childMenuItems.add(new IconMenuItem.Builder()
                    .priority(priority++)
                    .icon(SvgImage.COPY)
                    .text("Copy UUID to Clipboard")
                    .enabled(true)
                    .command(() -> ClipboardUtil.copy(docRef.getUuid()))
                    .build());
        }
        childMenuItems.add(createCopyLinkMenuItem(docRef, priority++));
        return childMenuItems;
    }

    private MenuItem createCopyLinkMenuItem(final DocRef docRef, final int priority) {
        // Generate a URL that can be used to open a new Stroom window with the target document loaded
        final String docUrl = Window.Location.createUrlBuilder()
                .setPath("/")
                .setParameter(UrlParameters.ACTION, UrlParameters.OPEN_DOC_ACTION)
                .setParameter(UrlParameters.DOC_TYPE_QUERY_PARAM, docRef.getType())
                .setParameter(UrlParameters.DOC_UUID_QUERY_PARAM, docRef.getUuid())
                .buildString();

        return new IconMenuItem.Builder()
                .priority(priority)
                .icon(SvgImage.SHARE)
                .text("Copy Link to Clipboard")
                .command(() -> ClipboardUtil.copy(docUrl))
                .build();
    }

    // --------------------------------------------------------------------------------


    public static class Builder<T> {

        private EventBus eventBus;
        private boolean showIcon = false;
        private DocRef.DisplayType displayType = DisplayType.NAME;
        private Function<T, SafeHtml> cellTextFunction;
        private Function<T, DocRef> docRefFunction;
        private Function<T, String> cssClassFunction;

        public Builder<T> eventBus(final EventBus eventBus) {
            this.eventBus = eventBus;
            return this;
        }

        public Builder<T> showIcon(final boolean showIcon) {
            this.showIcon = showIcon;
            return this;
        }

        public Builder<T> displayType(final DisplayType displayType) {
            this.displayType = displayType;
            return this;
        }

        public Builder<T> cellTextFunction(final Function<T, SafeHtml> cellTextFunction) {
            this.cellTextFunction = cellTextFunction;
            return this;
        }

        public Builder<T> docRefFunction(final Function<T, DocRef> docRefFunction) {
            this.docRefFunction = docRefFunction;
            return this;
        }

        public Builder<T> cssClassFunction(final Function<T, String> cssClassFunction) {
            this.cssClassFunction = cssClassFunction;
            return this;
        }

        public DocRefCell<T> build() {
            if (docRefFunction == null) {
                docRefFunction = v -> null;
            }
            if (cellTextFunction == null) {
                cellTextFunction = v -> {
                    final DocRef docRef = docRefFunction.apply(v);
                    if (docRef == null) {
                        return SafeHtmlUtils.EMPTY_SAFE_HTML;
                    } else {
                        final String displayValue = docRef.getDisplayValue(NullSafe.requireNonNullElse(
                                displayType,
                                DisplayType.AUTO));
                        return NullSafe.isNonBlankString(displayValue)
                                ? SafeHtmlUtils.fromString(displayValue)
                                : SafeHtmlUtils.EMPTY_SAFE_HTML;
                    }
                };
            }
            if (cssClassFunction == null) {
                cssClassFunction = v -> null;
            }

//
//                public static String getTextFromDocRef(final DocRef docRef) {
//                    return getTextFromDocRef(docRef, DisplayType.AUTO);
//                }
//
//                public static String getTextFromDocRef(final DocRef docRef, final DisplayType displayType) {
//                    if (docRef == null) {
//                        return null;
//                    } else {
//                        return docRef.getDisplayValue(NullSafe.requireNonNullElse(displayType, DisplayType.AUTO));
//                    }
//                }
//
//            }
//
//
//
//        } else if (docRef != null) {
//            cellHtmlText = SafeHtmlUtils.fromString(getTextFromDocRef(docRef, displayType));
//        } else {
//            cellHtmlText = SafeHtmlUtils.EMPTY_SAFE_HTML;
//        }


            return new DocRefCell<>(
                    eventBus,
                    showIcon,
                    cellTextFunction,
                    docRefFunction,
                    cssClassFunction);
        }
    }
}
