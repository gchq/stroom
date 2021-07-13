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

package stroom.widget.menu.client.presenter;

import stroom.svg.client.Icon;

import com.google.gwt.cell.client.AbstractCell;
import com.google.gwt.cell.client.ValueUpdater;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.BrowserEvents;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.safecss.shared.SafeStyles;
import com.google.gwt.safecss.shared.SafeStylesUtils;
import com.google.gwt.safehtml.client.SafeHtmlTemplates;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.safehtml.shared.SafeUri;

public class MenuItemCell extends AbstractCell<Item> {

    private final MenuItemCellUiHandler handler;

    public MenuItemCell(final MenuItemCellUiHandler handler) {
        super(BrowserEvents.CLICK, BrowserEvents.MOUSEOVER, BrowserEvents.MOUSEOUT);
        this.handler = handler;
    }

    @Override
    public void onBrowserEvent(final Context context,
                               final Element parent,
                               final Item value,
                               final NativeEvent event,
                               final ValueUpdater<Item> valueUpdater) {
        super.onBrowserEvent(context, parent, value, event, valueUpdater);

        if (value != null) {
            final String eventType = event.getType();
            final Element element = getElement(parent);

            if (value instanceof CommandMenuItem) {
                if (BrowserEvents.MOUSEOVER.equals(eventType)) {
                    final CommandMenuItem menuItem = (CommandMenuItem) value;
                    if (menuItem.isEnabled()) {
                        handler.onMouseOver(menuItem, element);
                        if (handler.isHover(menuItem)) {
                            element.addClassName("cellTableHoveredRow");
                        }
                    } else {
                        element.removeClassName("cellTableHoveredRow");
                    }

                } else if (BrowserEvents.MOUSEOUT.equals(eventType)) {
                    final CommandMenuItem menuItem = (CommandMenuItem) value;
                    handler.onMouseOut(menuItem, element);
                    if (!handler.isHover(menuItem)) {
                        element.removeClassName("cellTableHoveredRow");
                    }

                } else if (BrowserEvents.CLICK.equals(eventType)
                        && ((event.getButton() & NativeEvent.BUTTON_LEFT) != 0)) {
                    final CommandMenuItem menuItem = (CommandMenuItem) value;
                    if (menuItem.isEnabled()) {
                        handler.onClick(menuItem, element);
                    }
                }

            } else if (value instanceof MenuItem) {
                final MenuItem menuItem = (MenuItem) value;

                if (BrowserEvents.MOUSEOVER.equals(eventType)) {
                    element.addClassName("cellTableHoveredRow");
                    handler.onMouseOver(menuItem, element);

                } else if (BrowserEvents.MOUSEOUT.equals(eventType)) {
                    element.removeClassName("cellTableHoveredRow");

                } else if (BrowserEvents.CLICK.equals(eventType)
                        && ((event.getButton() & NativeEvent.BUTTON_LEFT) != 0)) {
                    handler.onClick(menuItem, element);
                }
            }
        }
    }

    private Element getElement(final Element parent) {
        if (parent.getFirstChildElement() != null) {
            return parent.getFirstChildElement();
        }

        return parent;
    }

    @Override
    public void render(final Context context, final Item value, final SafeHtmlBuilder sb) {
        if (value != null) {
            if (value instanceof IconMenuItem) {
                new IconMenuItemAppearance(handler).render(this, context, (IconMenuItem) value, sb);
            } else if (value instanceof SimpleMenuItem) {
                new SimpleMenuItemAppearance(handler).render(this, context, (SimpleMenuItem) value, sb);
            } else if (value instanceof InfoMenuItem) {
                new InfoMenuItemAppearance(handler).render(this, context, (InfoMenuItem) value, sb);
            } else if (value instanceof MenuItem) {
                new MenuItemAppearance().render(this, context, (MenuItem) value, sb);
            } else if (value instanceof Separator) {
                new SeparatorAppearance().render(this, context, (Separator) value, sb);
            } else if (value instanceof GroupHeading) {
                new GroupHeadingAppearance().render(this, context, (GroupHeading) value, sb);
            }
        }
    }

    public interface Appearance<I extends Item> {

        void render(MenuItemCell cell, Context context, I value, SafeHtmlBuilder sb);
    }

    public static class SeparatorAppearance implements Appearance<Separator> {

        private static final Template TEMPLATE = GWT.create(Template.class);

        public SeparatorAppearance() {
        }

        @Override
        public void render(final MenuItemCell cell, final Context context, final Separator value,
                           final SafeHtmlBuilder sb) {
            sb.append(TEMPLATE.separator("menuItem-separator"));
        }

        public interface Template extends SafeHtmlTemplates {

            @Template("<div class=\"{0}\"></div>")
            SafeHtml separator(String className);
        }
    }

    public static class GroupHeadingAppearance implements Appearance<GroupHeading> {

        private static final Template TEMPLATE = GWT.create(Template.class);

        public GroupHeadingAppearance() {
        }

        @Override
        public void render(final MenuItemCell cell, final Context context, final GroupHeading value,
                           final SafeHtmlBuilder sb) {
            sb.append(TEMPLATE.groupHeading("menuItem-groupHeading",
                    SafeHtmlUtils.fromTrustedString(value.getGroupName())));
        }

        public interface Template extends SafeHtmlTemplates {

            @Template("<div class=\"{0}\">{1}</div>")
            SafeHtml groupHeading(String className, SafeHtml groupName);
        }
    }

    public static class MenuItemAppearance implements Appearance<MenuItem> {

        @Override
        public void render(final MenuItemCell cell, final Context context, final MenuItem value,
                           final SafeHtmlBuilder sb) {
            if (value.getText() != null) {
                sb.append(SafeHtmlUtils.fromTrustedString(value.getText()));
            }
        }
    }

    public static class IconMenuItemAppearance implements Appearance<IconMenuItem> {

        private static final Template TEMPLATE = GWT.create(Template.class);
        private static final SafeStyles NORMAL = SafeStylesUtils.fromTrustedString("cursor:pointer;");
        private static final SafeStyles DISABLED = SafeStylesUtils.fromTrustedString("cursor:default;");
        private final MenuItemCellUiHandler uiHandler;

        public IconMenuItemAppearance(final MenuItemCellUiHandler uiHandler) {
            this.uiHandler = uiHandler;
        }

        @Override
        public void render(final MenuItemCell cell,
                           final Context context,
                           final IconMenuItem value,
                           final SafeHtmlBuilder sb) {
            if (value.getText() != null) {
                SafeStyles styles = NORMAL;

                if (!value.isEnabled()) {
                    styles = DISABLED;
                }

                final SafeHtmlBuilder inner = new SafeHtmlBuilder();
                final Icon enabledIcon = value.getEnabledIcon();
                final Icon disabledIcon = value.getDisabledIcon();

                if (value.isEnabled()) {
                    if (enabledIcon != null) {
                        inner.append(TEMPLATE.inner("menuItem-icon",
                                SafeHtmlUtils.fromTrustedString(enabledIcon.asWidget().getElement().getString())));
                    } else {
                        inner.append(TEMPLATE.inner("menuItem-icon", SafeHtmlUtils.EMPTY_SAFE_HTML));
                    }
                } else {
                    if (disabledIcon != null) {
                        inner.append(TEMPLATE.inner("menuItem-icon",
                                SafeHtmlUtils.fromTrustedString(disabledIcon.asWidget().getElement().getString())));
                    } else {
                        inner.append(TEMPLATE.inner("menuItem-icon", SafeHtmlUtils.EMPTY_SAFE_HTML));
                    }
                }

                inner.append(
                        TEMPLATE.inner("menuItem-text", SafeHtmlUtils.fromTrustedString(value.getText())));

                if (value.getShortcut() != null) {
                    inner.append(TEMPLATE.inner("menuItem-shortcut",
                            SafeHtmlUtils.fromTrustedString(value.getShortcut())));
                }

                final String disabledClass = value.isEnabled()
                        ? ""
                        : " menuItem-disabled";
                if (uiHandler.isHighlighted(value)) {
                    sb.append(TEMPLATE.outer("menuItem-highlight" + disabledClass, styles, inner.toSafeHtml()));
                } else {
                    sb.append(TEMPLATE.outer("menuItem-outer" + disabledClass, styles, inner.toSafeHtml()));
                }
            }
        }

        public interface Template extends SafeHtmlTemplates {

            @Template("<div class=\"{0}\" style=\"{1}\">{2}</div>")
            SafeHtml outer(String className, SafeStyles styles, SafeHtml inner);

            @Template("<div class=\"{0}\">{1}</div>")
            SafeHtml inner(String className, SafeHtml icon);

            @Template("<img class=\"{0}\" src=\"{1}\">")
            SafeHtml icon(String className, SafeUri url);

            @Template("<div class=\"{0}\">{1}</div>")
            SafeHtml text(String className, SafeHtml text);
        }
    }

    public static class SimpleMenuItemAppearance implements Appearance<SimpleMenuItem> {

        private static final Template TEMPLATE = GWT.create(Template.class);
        private static final SafeStyles NORMAL = SafeStylesUtils.fromTrustedString("cursor:pointer;");
        private static final SafeStyles DISABLED = SafeStylesUtils.fromTrustedString("cursor:default;");
        private final MenuItemCellUiHandler uiHandler;

        public SimpleMenuItemAppearance(final MenuItemCellUiHandler uiHandler) {
            this.uiHandler = uiHandler;
        }

        @Override
        public void render(final MenuItemCell cell,
                           final Context context,
                           final SimpleMenuItem value,
                           final SafeHtmlBuilder sb) {
            if (value.getText() != null) {
                SafeStyles styles = NORMAL;

                if (!value.isEnabled()) {
                    styles = DISABLED;
                }

                final SafeHtmlBuilder inner = new SafeHtmlBuilder();

                inner.append(
                        TEMPLATE.inner(
                                "menuItem-simpleText",
                                SafeHtmlUtils.fromTrustedString(value.getText())));

                if (value.getShortcut() != null) {
                    inner.append(TEMPLATE.inner("menuItem-shortcut",
                            SafeHtmlUtils.fromTrustedString(value.getShortcut())));
                }

                final String disabledClass = value.isEnabled()
                        ? ""
                        : " menuItem-disabled";
                if (uiHandler.isHighlighted(value)) {
                    sb.append(TEMPLATE.outer("menuItem-highlight" + disabledClass, styles,
                            inner.toSafeHtml()));
                } else {
                    sb.append(TEMPLATE.outer("menuItem-outer" + disabledClass, styles, inner.toSafeHtml()));
                }
            }
        }

        public interface Template extends SafeHtmlTemplates {

            @Template("<div class=\"{0}\" style=\"{1}\">{2}</div>")
            SafeHtml outer(String className, SafeStyles styles, SafeHtml inner);

            @Template("<div class=\"{0}\">{1}</div>")
            SafeHtml inner(String className, SafeHtml icon);

            @Template("<div class=\"{0}\">{1}</div>")
            SafeHtml text(String className, SafeHtml text);
        }
    }

    public static class InfoMenuItemAppearance implements Appearance<InfoMenuItem> {

        private static final Template TEMPLATE = GWT.create(Template.class);

        public InfoMenuItemAppearance(final MenuItemCellUiHandler uiHandler) {
        }

        @Override
        public void render(final MenuItemCell cell,
                           final Context context,
                           final InfoMenuItem value,
                           final SafeHtmlBuilder sb) {
            if (value.getSafeHtml() != null) {
                final SafeHtmlBuilder inner = new SafeHtmlBuilder();

                inner.append(TEMPLATE.inner(
                        "menuItem-simpleText",
                        value.getSafeHtml()));

                sb.append(TEMPLATE.outer(
                        "menuItem-outer infoMenuItem",
                        inner.toSafeHtml()));
            }
        }

        public interface Template extends SafeHtmlTemplates {

            @Template("<div class=\"{0}\">{1}</div>")
            SafeHtml outer(String className, SafeHtml inner);

            @Template("<div class=\"{0}\">{1}</div>")
            SafeHtml inner(String className, SafeHtml icon);

            @Template("<div class=\"{0}\">{1}</div>")
            SafeHtml text(String className, SafeHtml text);
        }
    }
}
