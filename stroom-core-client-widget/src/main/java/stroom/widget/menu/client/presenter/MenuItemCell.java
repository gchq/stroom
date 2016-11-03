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

import com.google.gwt.cell.client.AbstractCell;
import com.google.gwt.cell.client.ValueUpdater;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.BrowserEvents;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.resources.client.CssResource.ImportedWithPrefix;
import com.google.gwt.safecss.shared.SafeStyles;
import com.google.gwt.safecss.shared.SafeStylesUtils;
import com.google.gwt.safehtml.client.SafeHtmlTemplates;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.user.client.ui.Image;
import stroom.data.table.client.CellTableViewImpl.MenuResources;
import stroom.widget.button.client.GlyphIcon;
import stroom.widget.tab.client.presenter.Icon;
import stroom.widget.tab.client.presenter.ImageIcon;

public class MenuItemCell extends AbstractCell<Item> {
    private final MenuPresenter menuPresenter;

    @ImportedWithPrefix("stroom-menu")
    public interface Style extends CssResource {
        String DEFAULT_CSS = "MenuItem.css";

        String separator();

        String outer();

        String highlight();

        String icon();

        String face();

        String disabled();

        String text();

        String shortcut();
    }

    public interface Resources extends ClientBundle {
        @Source(Style.DEFAULT_CSS)
        Style style();
    }

    public interface Appearance<I extends Item> {
        void render(MenuItemCell cell, Context context, I value, SafeHtmlBuilder sb);
    }

    public static class SeparatorAppearance implements Appearance<Separator> {
        public interface Template extends SafeHtmlTemplates {
            @Template("<div class=\"{0}\"></div>")
            SafeHtml separator(String className);
        }

        private static final Template TEMPLATE = GWT.create(Template.class);
        private static final Resources RESOURCES = GWT.create(Resources.class);

        public SeparatorAppearance() {
            // Make sure the CSS is injected.
            RESOURCES.style().ensureInjected();
        }

        @Override
        public void render(final MenuItemCell cell, final Context context, final Separator value,
                           final SafeHtmlBuilder sb) {
            sb.append(TEMPLATE.separator(RESOURCES.style().separator()));
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
        public interface Template extends SafeHtmlTemplates {
            @Template("<div class=\"{0}\" style=\"{1}\">{2}</div>")
            SafeHtml outer(String className, SafeStyles styles, SafeHtml inner);

            @Template("<div class=\"{0}\">{1}</div>")
            SafeHtml inner(String className, SafeHtml icon);



            @Template("<div class=\"{0}\" title=\"Filter\"><div class=\"{1}\" style=\"{2}\"><i class=\"{3}\"></i></div></div>")
            SafeHtml icon(String iconClassName, String faceClassName, SafeStyles colour, String icon);

            @Template("<div class=\"{0}\">{1}</div>")
            SafeHtml text(String className, SafeHtml text);
        }

        private static final Template TEMPLATE = GWT.create(Template.class);
        private static final SafeStyles NORMAL = SafeStylesUtils.fromTrustedString("cursor:pointer;");
        private static final SafeStyles DISABLED = SafeStylesUtils.fromTrustedString("cursor:default;color:grey;");
        private static final Resources RESOURCES = GWT.create(Resources.class);

        private final MenuPresenter menuPresenter;

        public IconMenuItemAppearance(final MenuPresenter menuPresenter) {
            this.menuPresenter = menuPresenter;

            // Make sure the CSS is injected.
            RESOURCES.style().ensureInjected();
        }

        @Override
        public void render(final MenuItemCell cell, final Context context, final IconMenuItem value,
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
                    if (enabledIcon != null && enabledIcon instanceof ImageIcon) {
                        final ImageIcon imageIcon = (ImageIcon) enabledIcon;
                        final Image image = imageIcon.getImage();
                        if (image != null) {
                            inner.append(TEMPLATE.inner(RESOURCES.style().icon(),
                                    SafeHtmlUtils.fromTrustedString(image.getElement().getString())));
                        }
                    } else if (enabledIcon != null && enabledIcon instanceof GlyphIcon) {
                        final GlyphIcon glyphIcon = (GlyphIcon) enabledIcon;
                        inner.append(TEMPLATE.icon(RESOURCES.style().icon(), RESOURCES.style().face(),
                                SafeStylesUtils.forTrustedColor(glyphIcon.getColourSet().getEnabled()), glyphIcon.getGlyph()));
                    } else {
                        inner.append(TEMPLATE.inner(RESOURCES.style().icon(), SafeHtmlUtils.EMPTY_SAFE_HTML));
                    }
                } else {
                    if (disabledIcon != null && disabledIcon instanceof ImageIcon) {
                        final ImageIcon imageIcon = (ImageIcon) disabledIcon;
                        final Image image = imageIcon.getImage();
                        if (image != null) {
                            inner.append(TEMPLATE.inner(RESOURCES.style().icon(),
                                    SafeHtmlUtils.fromTrustedString(image.getElement().getString())));
                        }
                    } else if (enabledIcon != null && enabledIcon instanceof GlyphIcon) {
                        final GlyphIcon glyphIcon = (GlyphIcon) enabledIcon;
                        inner.append(TEMPLATE.icon(RESOURCES.style().icon(), RESOURCES.style().face() + " " + RESOURCES.style().disabled(),
                                SafeStylesUtils.forTrustedColor(glyphIcon.getColourSet().getEnabled()), glyphIcon.getGlyph()));
                    } else {
                        inner.append(TEMPLATE.inner(RESOURCES.style().icon(), SafeHtmlUtils.EMPTY_SAFE_HTML));
                    }
                }

                inner.append(
                        TEMPLATE.inner(RESOURCES.style().text(), SafeHtmlUtils.fromTrustedString(value.getText())));

                if (value.getShortcut() != null) {
                    inner.append(TEMPLATE.inner(RESOURCES.style().shortcut(),
                            SafeHtmlUtils.fromTrustedString(value.getShortcut())));
                }

                if (menuPresenter.isHighlighted(value)) {
                    sb.append(TEMPLATE.outer(RESOURCES.style().highlight(), styles, inner.toSafeHtml()));
                } else {
                    sb.append(TEMPLATE.outer(RESOURCES.style().outer(), styles, inner.toSafeHtml()));
                }
            }
        }
    }

    private static final MenuResources MENU_RESOURCES = GWT.create(MenuResources.class);

    public MenuItemCell(final MenuPresenter menuPresenter) {
        super(BrowserEvents.CLICK, BrowserEvents.MOUSEOVER, BrowserEvents.MOUSEOUT);
        this.menuPresenter = menuPresenter;
    }

    @Override
    public void onBrowserEvent(final Context context, final Element parent, final Item value, final NativeEvent event,
                               final ValueUpdater<Item> valueUpdater) {
        super.onBrowserEvent(context, parent, value, event, valueUpdater);

        if (value != null) {
            final String eventType = event.getType();
            final Element element = getElement(parent);

            if (value instanceof CommandMenuItem) {
                if (BrowserEvents.MOUSEOVER.equals(eventType)) {
                    final CommandMenuItem menuItem = (CommandMenuItem) value;
                    if (menuItem.isEnabled()) {
                        menuPresenter.onMouseOver(menuItem, element);
                        if (menuPresenter.isHover(menuItem)) {
                            element.addClassName(MENU_RESOURCES.cellTableStyle().cellTableHoveredRow());
                        }
                    } else {
                        element.removeClassName(MENU_RESOURCES.cellTableStyle().cellTableHoveredRow());
                    }

                } else if (BrowserEvents.MOUSEOUT.equals(eventType)) {
                    final CommandMenuItem menuItem = (CommandMenuItem) value;
                    menuPresenter.onMouseOut(menuItem, element);
                    if (!menuPresenter.isHover(menuItem)) {
                        element.removeClassName(MENU_RESOURCES.cellTableStyle().cellTableHoveredRow());
                    }

                } else if (BrowserEvents.CLICK.equals(eventType) && ((event.getButton() & NativeEvent.BUTTON_LEFT) != 0)) {
                    final CommandMenuItem menuItem = (CommandMenuItem) value;
                    if (menuItem.isEnabled()) {
                        menuPresenter.onClick(menuItem, element);
                    }
                }

            } else if (value instanceof MenuItem) {
                final MenuItem menuItem = (MenuItem) value;

                if (BrowserEvents.MOUSEOVER.equals(eventType)) {
                    element.addClassName(MENU_RESOURCES.cellTableStyle().cellTableHoveredRow());
                    menuPresenter.onMouseOver(menuItem, element);

                } else if (BrowserEvents.MOUSEOUT.equals(eventType)) {
                    element.removeClassName(MENU_RESOURCES.cellTableStyle().cellTableHoveredRow());

                } else if (BrowserEvents.CLICK.equals(eventType) && ((event.getButton() & NativeEvent.BUTTON_LEFT) != 0)) {
                    menuPresenter.onClick(menuItem, element);
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
                new IconMenuItemAppearance(menuPresenter).render(this, context, (IconMenuItem) value, sb);
            } else if (value instanceof MenuItem) {
                new MenuItemAppearance().render(this, context, (MenuItem) value, sb);
            } else if (value instanceof Separator) {
                new SeparatorAppearance().render(this, context, (Separator) value, sb);
            }
        }
    }
}
