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

package stroom.util.client.cell;

import stroom.security.client.UserTabPlugin;
import stroom.svg.client.IconColour;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.NullSafe;
import stroom.util.shared.UserRef;
import stroom.util.shared.UserRef.DisplayType;
import stroom.util.shared.string.CaseType;
import stroom.widget.datagrid.client.CopyTextUtil;
import stroom.widget.datagrid.client.EventCell;
import stroom.widget.util.client.Clipboard;
import stroom.widget.util.client.ElementUtil;
import stroom.widget.util.client.MouseUtil;
import stroom.widget.util.client.RowActions;
import stroom.widget.util.client.SvgImageUtil;
import stroom.widget.util.client.Templates;

import com.google.gwt.cell.client.AbstractCell;
import com.google.gwt.cell.client.ValueUpdater;
import com.google.gwt.dom.client.BrowserEvents;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.view.client.CellPreviewEvent;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/// A cell showing a user or group by its name (or another of its details), with its icon and hover
/// buttons that copy its name and open it. Who can open which user, and opening, are its owner's:
/// [Builder#canOpen] and [Builder#onOpen] (see `RefCellsController`).
///
/// @param <T_ROW> The row's type.
public class UserRefCell<T_ROW> extends AbstractCell<T_ROW>
        implements EventCell {

    private static final String ICON_CLASS_NAME = "icon-wrap";
    private static final String COPY_CLASS_NAME = "user-ref-link__copy";
    private static final String OPEN_CLASS_NAME = "user-ref-link__open";
    // The icons shown only on a hover, or with the keyboard on the row (user-ref-link.css)
    private static final String HOVER_ICON_CLASS_NAME = "user-ref-link__icon user-ref-link__icon--hover";
    private static final String HOVER_ICON_CONTAINER_CLASS_NAME = "hoverIconContainer";

    private final boolean showIcon;
    private final Function<T_ROW, SafeHtml> cellTextFunction;
    private final Function<T_ROW, UserRef> docRefFunction;
    private final Function<T_ROW, String> cssClassFunction;
    private final DisplayType displayType;
    private final Predicate<UserRef> canOpen;
    private final Consumer<UserRef> onOpen;

    private UserRefCell(final boolean showIcon,
                        final Function<T_ROW, SafeHtml> cellTextFunction,
                        final Function<T_ROW, UserRef> docRefFunction,
                        final Function<T_ROW, String> cssClassFunction,
                        final DisplayType displayType,
                        final Predicate<UserRef> canOpen,
                        final Consumer<UserRef> onOpen) {
        super(BrowserEvents.MOUSEDOWN, BrowserEvents.KEYDOWN);
        this.showIcon = showIcon;
        this.cellTextFunction = cellTextFunction;
        this.docRefFunction = docRefFunction;
        this.cssClassFunction = cssClassFunction;
        this.displayType = displayType;
        this.canOpen = canOpen;
        this.onOpen = onOpen;
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
        final UserRef userRef = docRefFunction.apply(value);
        if (userRef != null) {
            if (BrowserEvents.MOUSEDOWN.equals(event.getType()) && MouseUtil.isPrimary(event)) {
                onEnterKeyDown(context, parent, value, event, valueUpdater);
            } else if (RowActions.isKeyPress(event)) {
                // Enter or Space on the open button; the button's own click would come too
                event.preventDefault();
                onEnterKeyDown(context, parent, value, event, valueUpdater);
            }
        }
    }

    @Override
    protected void onEnterKeyDown(final Context context,
                                  final Element parent,
                                  final T_ROW value,
                                  final NativeEvent event,
                                  final ValueUpdater<T_ROW> valueUpdater) {
        final Element element = event.getEventTarget().cast();
        final UserRef userRef = docRefFunction.apply(value);
        if (userRef != null) {
            if (ElementUtil.hasClassName(element, COPY_CLASS_NAME, 5)) {
                CopyTextUtil.keepSelection(event);
                final String text = cellTextFunction.apply(value).asString();
                if (text != null) {
                    Clipboard.writeText(text);
                }
            } else if (isOpenable(userRef) && ElementUtil.hasClassName(element, OPEN_CLASS_NAME, 5)) {
                CopyTextUtil.keepSelection(event);
                onOpen.accept(userRef);
            }
        }
    }

    @Override
    public void render(final Context context,
                       final T_ROW value,
                       final SafeHtmlBuilder sb) {
        final UserRef userRef = docRefFunction.apply(value);
        if (userRef == null) {
            sb.append(SafeHtmlUtils.EMPTY_SAFE_HTML);
        } else {
            final String cellPlainText = NullSafe.getOrElse(
                    userRef,
                    ref -> ref.toDisplayString(displayType),
                    "");
            if (NullSafe.isBlankString(cellPlainText)) {
                sb.append(SafeHtmlUtils.EMPTY_SAFE_HTML);
            } else {
                final SafeHtml cellHtmlText = SafeHtmlUtils.fromString(cellPlainText);
                String cssClasses = "userRefLinkText";
                if (cssClassFunction != null) {
                    final String additionalClasses = cssClassFunction.apply(value);
                    if (additionalClasses != null) {
                        cssClasses += " " + additionalClasses;
                    }
                }
                final SafeHtml textDiv = Templates.div(cssClasses, cellHtmlText);

                final String containerClasses = String.join(
                        " ",
                        HOVER_ICON_CONTAINER_CLASS_NAME,
                        "user-ref-link");
                sb.appendHtmlConstant("<div class=\"" + containerClasses + "\">");

                if (showIcon) {
                    final String title = userRef.getType(CaseType.SENTENCE) + " " + cellPlainText;
                    final SvgImage icon = userRef.isGroup()
                            ? UserTabPlugin.GROUP_ICON
                            : UserTabPlugin.USER_ICON;
                    final SafeHtml iconDiv = SvgImageUtil.toSafeHtml(
                            title,
                            icon,
                            ICON_CLASS_NAME,
                            IconColour.BLUE.getClassName(),
                            "user-ref-link__icon");
                    sb.append(iconDiv);
                }
                sb.append(textDiv);

                final SafeHtml copySvg = SvgImageUtil.toSafeHtml(
                        SvgImage.COPY, ICON_CLASS_NAME, COPY_CLASS_NAME, HOVER_ICON_CLASS_NAME);
                sb.append(Templates.divWithTitle(
                        "Copy " + displayType.getTypeName() + " '" + cellPlainText + "' to clipboard",
                        copySvg));

                // Not all users should be able to open the user
                if (isOpenable(userRef)) {
                    // A button the keyboard reaches from its row (a row action)
                    final SafeHtml openSvg = SvgImageUtil.toSafeHtml(
                            SvgImage.OPEN, ICON_CLASS_NAME, HOVER_ICON_CLASS_NAME, RowActions.ICON_CLASS_NAME);
                    sb.append(RowActions.button(
                            OPEN_CLASS_NAME,
                            "Open " + userRef.getType(CaseType.LOWER) + " " + cellPlainText,
                            openSvg));
                }

                sb.appendHtmlConstant("</div>");
            }
        }
    }

    // Not every user may open every user (see Builder#canOpen), and one with no UUID can't be opened
    private boolean isOpenable(final UserRef userRef) {
        return onOpen != null
               && userRef.getUuid() != null
               && (canOpen == null || canOpen.test(userRef));
    }

    public static class Builder<T> {

        private boolean showIcon = false;
        private DisplayType displayType = DisplayType.AUTO;
        private Function<T, SafeHtml> cellTextFunction;
        private Function<T, UserRef> userRefFunction;
        private Function<T, String> cssClassFunction;
        private Predicate<UserRef> canOpen;
        private Consumer<UserRef> onOpen;

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

        public Builder<T> userRefFunction(final Function<T, UserRef> userRefFunction) {
            this.userRefFunction = userRefFunction;
            return this;
        }

        public Builder<T> cssClassFunction(final Function<T, String> cssClassFunction) {
            this.cssClassFunction = cssClassFunction;
            return this;
        }

        /// Sets which users a row's open button is shown for, e.g. all for those who manage users,
        /// else only the current user (see `RefCellsController`).
        ///
        /// @param canOpen Whether a user may be opened, or null (the default) for every user.
        /// @return This builder.
        public Builder<T> canOpen(final Predicate<UserRef> canOpen) {
            this.canOpen = canOpen;
            return this;
        }

        /// Sets what opens a row's user, from its open button, e.g. firing the app's open user event
        /// (see `RefCellsController`).
        ///
        /// @param onOpen What opens a user, or null (the default) for a cell that can't open one: it
        ///               shows no open button.
        /// @return This builder.
        public Builder<T> onOpen(final Consumer<UserRef> onOpen) {
            this.onOpen = onOpen;
            return this;
        }

        public UserRefCell<T> build() {
            if (userRefFunction == null) {
                userRefFunction = v -> null;
            }
            if (cellTextFunction == null) {
                cellTextFunction = v -> {
                    final UserRef userRef = userRefFunction.apply(v);
                    if (userRef == null) {
                        return SafeHtmlUtils.EMPTY_SAFE_HTML;
                    } else {
                        return SafeHtmlUtils.fromString(userRef.toDisplayString(displayType));
                    }
                };
            }
            if (cssClassFunction == null) {
                cssClassFunction = v -> null;
            }


            return new UserRefCell<>(
                    showIcon,
                    cellTextFunction,
                    userRefFunction,
                    cssClassFunction,
                    displayType,
                    canOpen,
                    onOpen);
        }
    }
}
