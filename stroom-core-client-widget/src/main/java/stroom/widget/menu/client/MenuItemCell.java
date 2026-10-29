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

package stroom.widget.menu.client;

import stroom.svg.client.IconColour;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.NullSafe;
import stroom.widget.util.client.NewTabLink;
import stroom.widget.util.client.SafeHtmlUtil;
import stroom.widget.util.client.SvgImageUtil;
import stroom.widget.util.client.Templates;

import com.google.gwt.cell.client.AbstractCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.client.SafeHtmlTemplates;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;

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
            }
        }
    }


    // Tells screen readers that an item opens a new browser tab, with hidden text after the item's
    // text. It is kept out of the text's own element, which other code reads as the item's
    // visible text (e.g. to name a sub menu)
    private static void appendNewTabHint(final MenuItem menuItem, final SafeHtmlBuilder sb) {
        if (menuItem.isOpensNewTab()) {
            sb.append(NewTabLink.hiddenHint());
        }
    }


    // --------------------------------------------------------------------------------


    public interface Appearance<I extends Item> {

        void render(MenuItemCell cell, Context context, I value, SafeHtmlBuilder sb);
    }


    // --------------------------------------------------------------------------------


    public static class SeparatorAppearance implements Appearance<Separator> {

        public SeparatorAppearance() {
        }

        @Override
        public void render(final MenuItemCell cell, final Context context, final Separator value,
                           final SafeHtmlBuilder sb) {
            sb.append(Templates.div("menu__separator"));
        }
    }


    // --------------------------------------------------------------------------------


    public static class MenuItemAppearance implements Appearance<MenuItem> {

        @Override
        public void render(final MenuItemCell cell, final Context context, final MenuItem value,
                           final SafeHtmlBuilder sb) {
            if (value.getText() != null) {
                sb.append(value.getText());
            }
        }
    }


    // --------------------------------------------------------------------------------


    public static class IconMenuItemAppearance implements Appearance<IconMenuItem> {

        private static final Template TEMPLATE = GWT.create(Template.class);

        @Override
        public void render(final MenuItemCell cell,
                           final Context context,
                           final IconMenuItem value,
                           final SafeHtmlBuilder sb) {
            if (value.getText() != null) {
                final SafeHtmlBuilder inner = new SafeHtmlBuilder();
                final SvgImage enabledIcon = value.getEnabledIcon();
                final SvgImage disabledIcon = value.getDisabledIcon();

                IconColour iconColour = IconColour.BLUE;
                if (value.getIconColour() != null) {
                    iconColour = value.getIconColour();
                }
                final String iconClassName = "menu__icon" + " " + iconColour.getClassName();

                if (value.isEnabled()) {
                    if (enabledIcon != null) {
                        inner.append(SvgImageUtil.toSafeHtml(enabledIcon, iconClassName));
                    } else {
                        inner.append(TEMPLATE.inner(iconClassName, SafeHtmlUtils.EMPTY_SAFE_HTML));
                    }
                } else {
                    if (disabledIcon != null) {
                        inner.append(SvgImageUtil.toSafeHtml(disabledIcon, iconClassName));
                    } else if (enabledIcon != null) {
                        inner.append(SvgImageUtil.toSafeHtml(enabledIcon, iconClassName));
                    } else {
                        inner.append(TEMPLATE.inner(iconClassName, SafeHtmlUtils.EMPTY_SAFE_HTML));
                    }
                }


                inner.append(
                        TEMPLATE.inner("menu__text", value.getText()));
                appendNewTabHint(value, inner);

                final String shortcut = value.getShortcut();
                if (shortcut != null) {
                    inner.append(TEMPLATE.inner("menu__shortcut",
                            SafeHtmlUtils.fromString(shortcut)));
                }

                // If this is a parent menu item, render an arrow to the right-hand side
                if ((value instanceof IconParentMenuItem || value instanceof KeyedParentMenuItem)
                    && value.isEnabled()) {
                    inner.append(SvgImageUtil.toSafeHtml(SvgImage.ARROW_RIGHT, "menu__arrow"));
                }

                String className = "menu__item";
                className += value.isHighlight()
                        ? " menu__item--highlight"
                        : "";
                final SafeHtml tooltip = NullSafe.getOrElse(value, MenuItem::getTooltip, value.getText());
                // As text: the template escapes the title itself
                sb.append(TEMPLATE.outer(className, SafeHtmlUtil.toPlainText(tooltip), inner.toSafeHtml()));
            }
        }


        // --------------------------------------------------------------------------------


        public interface Template extends SafeHtmlTemplates {

            @Template("<div class=\"{0}\" title=\"{1}\">{2}</div>")
            SafeHtml outer(String className, String tooltip, SafeHtml inner);

            @Template("<div class=\"{0}\">{1}</div>")
            SafeHtml inner(String className, SafeHtml icon);

            @Template("<div class=\"{0}\">{1}</div>")
            SafeHtml text(String className, SafeHtml text);
        }
    }


    // --------------------------------------------------------------------------------


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
                                "menu__text",
                                value.getText()));
                appendNewTabHint(value, inner);

                final String shortcut = value.getShortcut();
                if (shortcut != null) {
                    inner.append(TEMPLATE.inner("menu__shortcut",
                            SafeHtmlUtils.fromString(shortcut)));
                }

                // If this is a parent menu item, render an arrow to the right-hand side
                if ((value instanceof SimpleParentMenuItem) && value.isEnabled()) {
                    inner.append(SvgImageUtil.toSafeHtml(SvgImage.ARROW_RIGHT, "menu__arrow"));
                }

                // A disabled item looks so from its aria-disabled (Menu)
                sb.append(TEMPLATE.outer("menu__item", inner.toSafeHtml()));
            }
        }


        // --------------------------------------------------------------------------------


        public interface Template extends SafeHtmlTemplates {

            @Template("<div class=\"{0}\">{1}</div>")
            SafeHtml outer(String className, SafeHtml inner);

            @Template("<div class=\"{0}\">{1}</div>")
            SafeHtml inner(String className, SafeHtml icon);

            @Template("<div class=\"{0}\">{1}</div>")
            SafeHtml text(String className, SafeHtml text);
        }
    }


    // --------------------------------------------------------------------------------


    public static class InfoMenuItemAppearance implements Appearance<InfoMenuItem> {

        private static final Template TEMPLATE = GWT.create(Template.class);

        @Override
        public void render(final MenuItemCell cell,
                           final Context context,
                           final InfoMenuItem value,
                           final SafeHtmlBuilder sb) {
            if (value.getText() != null) {
                final SafeHtmlBuilder inner = new SafeHtmlBuilder();

                inner.append(TEMPLATE.inner(
                        "menu__text",
                        value.getText()));

                sb.append(TEMPLATE.outer(
                        "menu__item menu__item--info",
                        inner.toSafeHtml()));
            }
        }


        // --------------------------------------------------------------------------------


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
