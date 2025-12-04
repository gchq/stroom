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
import stroom.security.client.UserTabPlugin;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.client.event.OpenUserEvent;
import stroom.security.shared.AppPermission;
import stroom.svg.client.IconColour;
import stroom.svg.client.Preset;
import stroom.svg.shared.SvgImage;
import stroom.util.client.ClipboardUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.UserRef;
import stroom.util.shared.UserRef.DisplayType;
import stroom.util.shared.string.CaseType;
import stroom.widget.util.client.ElementUtil;
import stroom.widget.util.client.MouseUtil;
import stroom.widget.util.client.SvgImageUtil;
import stroom.widget.util.client.Templates;

import com.google.gwt.cell.client.AbstractCell;
import com.google.gwt.cell.client.ValueUpdater;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.view.client.CellPreviewEvent;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.Function;

import static com.google.gwt.dom.client.BrowserEvents.MOUSEDOWN;

public class UserRefCell<T_ROW> extends AbstractCell<T_ROW>
        implements HasHandlers, EventCell {

    private static final String ICON_CLASS_NAME = "svgIcon";
    private static final String COPY_CLASS_NAME = "userRefLinkCopy";
    private static final String OPEN_CLASS_NAME = "userRefLinkOpen";
    private static final String HOVER_ICON_CLASS_NAME = "hoverIcon";
    private static final String HOVER_ICON_CONTAINER_CLASS_NAME = "hoverIconContainer";

    private final EventBus eventBus;
    private final ClientSecurityContext securityContext;
    private final boolean showIcon;
    private final Function<T_ROW, SafeHtml> cellTextFunction;
    private final Function<T_ROW, UserRef> docRefFunction;
    private final Function<T_ROW, String> cssClassFunction;
    private final DisplayType displayType;

    private UserRefCell(final EventBus eventBus,
                        final ClientSecurityContext securityContext,
                        final boolean showIcon,
                        final Function<T_ROW, SafeHtml> cellTextFunction,
                        final Function<T_ROW, UserRef> docRefFunction,
                        final Function<T_ROW, String> cssClassFunction,
                        final DisplayType displayType) {
        super(MOUSEDOWN);
        this.eventBus = eventBus;
        this.securityContext = securityContext;
        this.showIcon = showIcon;
        this.cellTextFunction = cellTextFunction;
        this.docRefFunction = docRefFunction;
        this.cssClassFunction = cssClassFunction;
        this.displayType = displayType;
    }

    @Override
    public boolean isConsumed(final CellPreviewEvent<?> event) {
        final NativeEvent nativeEvent = event.getNativeEvent();
        if (MOUSEDOWN.equals(nativeEvent.getType()) && MouseUtil.isPrimary(nativeEvent)) {
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
        final UserRef userRef = docRefFunction.apply(value);
        if (userRef != null) {
            if (MOUSEDOWN.equals(event.getType()) && MouseUtil.isPrimary(event)) {
                onEnterKeyDown(context, parent, value, event, valueUpdater);
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
        final UserRef userRef = docRefFunction.apply(value);
        if (userRef != null) {
            if (ElementUtil.hasClassName(element, COPY_CLASS_NAME, 5)) {
                final String text = cellTextFunction.apply(value).asString();
                if (text != null) {
                    ClipboardUtil.copy(text);
                }
            } else if (ElementUtil.hasClassName(element, OPEN_CLASS_NAME, 5)) {
                OpenUserEvent.fire(this, userRef);
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
                        "userRefLinkContainer");
                sb.appendHtmlConstant("<div class=\"" + containerClasses + "\">");

                if (showIcon) {
                    final String title = userRef.getType(CaseType.SENTENCE) + " " + cellPlainText;
                    final Preset icon = userRef.isGroup()
                            ? UserTabPlugin.GROUP_ICON
                            : UserTabPlugin.USER_ICON;
                    final SafeHtml iconDiv = SvgImageUtil.toSafeHtml(
                            title,
                            icon.getSvgImage(),
                            ICON_CLASS_NAME,
                            IconColour.BLUE.getClassName(),
                            "userRefLinkIcon");
                    sb.append(iconDiv);
                }
                sb.append(textDiv);

                final SafeHtml copySvg = SvgImageUtil.toSafeHtml(
                        SvgImage.COPY, ICON_CLASS_NAME, COPY_CLASS_NAME, HOVER_ICON_CLASS_NAME);
                sb.append(Templates.divWithTitle(
                        "Copy " + displayType.getTypeName() + " '" + cellPlainText + "' to clipboard",
                        copySvg));

                // Not all users should be able to open the user
                if (userRef.getUuid() != null && hasPermissionToOpen(userRef)) {
                    final SafeHtml openSvg = SvgImageUtil.toSafeHtml(
                            SvgImage.OPEN, ICON_CLASS_NAME, OPEN_CLASS_NAME, HOVER_ICON_CLASS_NAME);
                    sb.append(Templates.divWithTitle(
                            "Open " + userRef.getType(CaseType.LOWER) + " " + cellPlainText + " in new tab",
                            openSvg));
                }

                sb.appendHtmlConstant("</div>");
            }
        }
    }

    private boolean hasPermissionToOpen(final UserRef userRef) {
        return securityContext.hasAppPermission(AppPermission.MANAGE_USERS_PERMISSION)
               || securityContext.isCurrentUser(userRef);
    }

    public static class Builder<T> {

        private EventBus eventBus;
        private boolean showIcon = false;
        private DisplayType displayType = DisplayType.AUTO;
        private Function<T, SafeHtml> cellTextFunction;
        private Function<T, UserRef> userRefFunction;
        private Function<T, String> cssClassFunction;
        private ClientSecurityContext securityContext;

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

        public Builder<T> userRefFunction(final Function<T, UserRef> userRefFunction) {
            this.userRefFunction = userRefFunction;
            return this;
        }

        public Builder<T> cssClassFunction(final Function<T, String> cssClassFunction) {
            this.cssClassFunction = cssClassFunction;
            return this;
        }

        public Builder<T> securityContext(final ClientSecurityContext securityContext) {
            this.securityContext = securityContext;
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


            return new UserRefCell<>(
                    eventBus,
                    securityContext,
                    showIcon,
                    cellTextFunction,
                    userRefFunction,
                    cssClassFunction,
                    displayType);
        }
    }
}
