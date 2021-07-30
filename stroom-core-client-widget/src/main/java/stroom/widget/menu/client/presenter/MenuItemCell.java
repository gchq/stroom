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
import stroom.widget.util.client.KeyBinding;

import com.google.gwt.cell.client.AbstractCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.client.SafeHtmlTemplates;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.safehtml.shared.SafeUri;

public class MenuItemCell extends AbstractCell<Item> {

    @Override
    public void render(final Context context, final Item value, final SafeHtmlBuilder sb) {
        if (value != null) {
            if (value instanceof IconMenuItem) {
                new IconMenuItemAppearance().render(this, context, (IconMenuItem) value, sb);
            } else if (value instanceof SimpleMenuItem) {
                new SimpleMenuItemAppearance().render(this, context, (SimpleMenuItem) value, sb);
            } else if (value instanceof InfoMenuItem) {
                new InfoMenuItemAppearance().render(this, context, (InfoMenuItem) value, sb);
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

        @Override
        public void render(final MenuItemCell cell,
                           final Context context,
                           final IconMenuItem value,
                           final SafeHtmlBuilder sb) {
            if (value.getText() != null) {
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

                if (value.getAction() != null) {
                    final String shortcut = KeyBinding.getShortcut(value.getAction());
                    if (shortcut != null) {
                        inner.append(TEMPLATE.inner("menuItem-shortcut",
                                SafeHtmlUtils.fromTrustedString(shortcut)));
                    }
                }

                String className = "menuItem-outer";
                className += value.isHighlight()
                        ? " menuItem-highlight"
                        : "";
                className += value.isEnabled()
                        ? ""
                        : " menuItem-disabled";
                sb.append(TEMPLATE.outer(className, inner.toSafeHtml()));
            }
        }

        public interface Template extends SafeHtmlTemplates {

            @Template("<div class=\"{0}\" tabindex=\"-1\">{1}</div>")
            SafeHtml outer(String className, SafeHtml inner);

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

        @Override
        public void render(final MenuItemCell cell,
                           final Context context,
                           final SimpleMenuItem value,
                           final SafeHtmlBuilder sb) {
            if (value.getText() != null) {
                final SafeHtmlBuilder inner = new SafeHtmlBuilder();

                inner.append(
                        TEMPLATE.inner(
                                "menuItem-simpleText",
                                SafeHtmlUtils.fromTrustedString(value.getText())));

                if (value.getAction() != null) {
                    final String shortcut = KeyBinding.getShortcut(value.getAction());
                    if (shortcut != null) {
                        inner.append(TEMPLATE.inner("menuItem-shortcut",
                                SafeHtmlUtils.fromTrustedString(shortcut)));
                    }
                }

                String className = "menuItem-outer";
                className += value.isEnabled()
                        ? ""
                        : " menuItem-disabled";
                sb.append(TEMPLATE.outer(className, inner.toSafeHtml()));
            }
        }

        public interface Template extends SafeHtmlTemplates {

            @Template("<div class=\"{0}\" tabindex=\"-1\">{1}</div>")
            SafeHtml outer(String className, SafeHtml inner);

            @Template("<div class=\"{0}\">{1}</div>")
            SafeHtml inner(String className, SafeHtml icon);

            @Template("<div class=\"{0}\">{1}</div>")
            SafeHtml text(String className, SafeHtml text);
        }
    }

    public static class InfoMenuItemAppearance implements Appearance<InfoMenuItem> {

        private static final Template TEMPLATE = GWT.create(Template.class);

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

            @Template("<div class=\"{0}\" tabindex=\"-1\">{1}</div>")
            SafeHtml outer(String className, SafeHtml inner);

            @Template("<div class=\"{0}\">{1}</div>")
            SafeHtml inner(String className, SafeHtml icon);

            @Template("<div class=\"{0}\">{1}</div>")
            SafeHtml text(String className, SafeHtml text);
        }
    }
}
