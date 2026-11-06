/*
 * Copyright 2026 Crown Copyright
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

package stroom.widget.button.client;

import stroom.svg.shared.SvgImage;
import stroom.widget.menu.client.IconMenuItem;
import stroom.widget.menu.client.Item;
import stroom.widget.menu.client.MenuRequest;
import stroom.widget.popup.client.PopupPosition;
import stroom.widget.popup.client.PopupPosition.PopupLocation;
import stroom.widget.util.client.GlobalResizeObserver;
import stroom.widget.util.client.Rect;

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.dom.client.ButtonElement;
import com.google.gwt.dom.client.Element;
import com.google.gwt.uibinder.client.UiConstructor;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Widget;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/// A named group of icon buttons that sit together, e.g. a document's Save and Save As. The group
/// is one tab stop and the arrow keys move between its buttons (see [ButtonGroupFocus]); with two
/// or more buttons a screen reader announces it as a toolbar, by its name.
///
/// Buttons are made from an icon and a name, both always given, and start enabled:
///
/// ```
/// final Toolbar toolbar = new Toolbar("Dictionary actions");
/// final ToolbarButton save = toolbar.button(SvgImage.SAVE, "Save", this::save);
/// toolbar.toggle(SvgImage.FILTER, "Show Column Filters", this::setFiltering);
/// save.setEnabled(false);
/// ```
///
/// It can also be declared in a UiBinder template, with its buttons as children:
///
/// ```
/// <b:Toolbar ui:field="actions" name="Dictionary actions">
///     <b:ToolbarButton ui:field="save" svg="SAVE" title="Save"/>
/// </b:Toolbar>
/// ```
///
/// Buttons apart on a row (e.g. at either end) are separate toolbars, laid out by a [ToolbarRow].
///
/// When its buttons don't fit its room (as when the user's toolbar display preference shows their
/// labels, which makes them wider), those at the end go into a More menu, opened by a button the
/// toolbar adds at its end while it needs it (so that otherwise it holds only its own widgets, which
/// code may count), so that every button can still be reached. The toolbar fits its buttons again
/// whenever its size or the page's preference classes change.
public class Toolbar extends FlowPanel {

    // A button or separator that doesn't fit, shown in the More menu instead (toolbar.css)
    private static final String OVERFLOWED = "toolbar__item--overflowed";
    // The toolbars shown, fitted again when the page's preference classes change
    private static final Set<Toolbar> SHOWN = new HashSet<>();
    private static JavaScriptObject pageClassWatcher;

    private final ButtonGroupFocus groupFocus;
    // Opens the More menu, the last of the toolbar's buttons while some don't fit
    private final ToolbarButton more;
    private String name;

    /// @param name The toolbar's name, announced with it, e.g. "Dictionary actions".
    @UiConstructor
    public Toolbar(final String name) {
        setStyleName("toolbar");
        groupFocus = ButtonGroupFocus.install(getElement());
        groupFocus.setName(name);
        this.name = name;
        more = new ToolbarButton();
        more.setSvg(SvgImage.ELLIPSES_HORIZONTAL);
        more.setTitle("More");
        more.setOpensMenu(true);
        more.setOnClick(this::showMore);
    }

    /// Adds a widget, before the More button if the toolbar has added it.
    ///
    /// @param widget The widget, e.g. a button.
    @Override
    public void add(final Widget widget) {
        if (widget != more && more.getParent() == this) {
            insert(widget, getWidgetIndex(more));
        } else {
            super.add(widget);
        }
    }

    /// Adds a button.
    ///
    /// @param icon    The button's icon.
    /// @param name    The button's name, shown as its tooltip and announced by screen readers.
    /// @param onClick What clicking it does.
    /// @return The button, e.g. to disable or hide it.
    public ToolbarButton button(final SvgImage icon, final String name, final Runnable onClick) {
        return button(icon, name, null, onClick);
    }

    /// Adds a button with a label shorter than its name, shown when the toolbar display preference
    /// shows text, e.g. "Edit" for "Edit Selected Index Field". The name must start with it (see
    /// [ToolbarButton#setLabel(String)]).
    ///
    /// @param icon    The button's icon.
    /// @param name    The button's name, shown as its tooltip and announced by screen readers.
    /// @param label   The label, or null to show the name.
    /// @param onClick What clicking it does.
    /// @return The button, e.g. to disable or hide it.
    public ToolbarButton button(final SvgImage icon,
                                final String name,
                                final String label,
                                final Runnable onClick) {
        final ToolbarButton button = new ToolbarButton();
        button.setSvg(icon);
        button.setTitle(name);
        button.setLabel(label);
        button.setOnClick(onClick);
        add(button);
        return button;
    }

    /// Adds a button that is pressed (on) or not, starting not pressed.
    ///
    /// @param icon     The button's icon.
    /// @param name     The button's name, shown as its tooltip and announced by screen readers.
    /// @param onToggle Given the button's new state when it is pressed.
    /// @return The button, e.g. to set its state.
    public ToolbarToggleButton toggle(final SvgImage icon,
                                      final String name,
                                      final Consumer<Boolean> onToggle) {
        return toggle(icon, name, null, onToggle);
    }

    /// Adds a button that is pressed (on) or not, starting not pressed, with a label shorter than
    /// its name (see [#button(SvgImage, String, String, Runnable)]).
    ///
    /// @param icon     The button's icon.
    /// @param name     The button's name, shown as its tooltip and announced by screen readers.
    /// @param label    The label, or null to show the name.
    /// @param onToggle Given the button's new state when it is pressed.
    /// @return The button, e.g. to set its state.
    public ToolbarToggleButton toggle(final SvgImage icon,
                                      final String name,
                                      final String label,
                                      final Consumer<Boolean> onToggle) {
        final ToolbarToggleButton button = new ToolbarToggleButton();
        button.setSvg(icon);
        button.setTitle(name);
        button.setLabel(label);
        button.setOnToggle(onToggle);
        add(button);
        return button;
    }

    /// Adds a refresh button, which also shows whether what it refreshes is refreshing and can be made
    /// to pause and resume updates (see [RefreshButton]). It names itself for what pressing it does.
    ///
    /// @param onClick What clicking it does.
    /// @return The button, e.g. to show that what it refreshes is refreshing.
    public RefreshButton refresh(final Runnable onClick) {
        final RefreshButton button = new RefreshButton();
        button.setOnClick(onClick);
        add(button);
        return button;
    }

    /// Adds a line between the buttons before and after it. They are still one group, so the arrow
    /// keys cross it.
    public void separator() {
        add(new ToolbarSeparator());
    }

    /// @param name The toolbar's name, announced with it.
    public void setName(final String name) {
        groupFocus.setName(name);
        this.name = name;
    }

    /// Puts the buttons in a column, which Up and Down move along rather than Left and Right.
    ///
    /// @param vertical Whether the buttons are in a column.
    public void setVertical(final boolean vertical) {
        // Laid out as a column by its aria-orientation, which the group sets
        groupFocus.setVertical(vertical);
    }

    @Override
    protected void onLoad() {
        super.onLoad();
        SHOWN.add(this);
        watchPageClasses();
        GlobalResizeObserver.addListener(getElement(), element -> fit());
        fit();
        // Attaching the buttons resets their tab indexes (FocusWidget.onAttach)
        groupFocus.update();
    }

    @Override
    protected void onUnload() {
        GlobalResizeObserver.removeListener(getElement());
        SHOWN.remove(this);
        super.onUnload();
    }

    /// Fits the buttons to the toolbar's room: those at the end that don't fit go into the More
    /// menu, and the More button shows while any do. A column of buttons isn't fitted.
    public void fit() {
        if (!isAttached()) {
            return;
        }
        final List<Widget> items = items();
        for (final Widget item : items) {
            item.removeStyleName(OVERFLOWED);
        }
        if (more.getParent() == this) {
            remove(more);
        }
        if (groupFocus.isVertical() || fits()) {
            return;
        }
        super.add(more);
        // From the end, until the rest and the More button fit; a separator left last goes too
        for (int i = items.size() - 1; i >= 0 && !fits(); i--) {
            items.get(i).addStyleName(OVERFLOWED);
        }
        for (int i = items.size() - 1; i >= 0; i--) {
            final Widget item = items.get(i);
            if (item.getStyleName().contains(OVERFLOWED)) {
                continue;
            }
            if (item instanceof ToolbarSeparator) {
                item.addStyleName(OVERFLOWED);
            } else {
                break;
            }
        }
    }

    /// @return The buttons that don't fit and are in the More menu, in order.
    public List<ToolbarButton> getOverflowedButtons() {
        final List<ToolbarButton> buttons = new ArrayList<>();
        for (final Widget item : items()) {
            if (item.getStyleName().contains(OVERFLOWED)) {
                addButtons(item, buttons);
            }
        }
        return buttons;
    }

    // The toolbar's buttons, separators and toolbars within it, without the More button
    private List<Widget> items() {
        final List<Widget> items = new ArrayList<>();
        for (int i = 0; i < getWidgetCount(); i++) {
            final Widget widget = getWidget(i);
            if (widget != more && widget.isVisible()) {
                items.add(widget);
            }
        }
        return items;
    }

    // A button that is shown, or a toolbar's within it (a document tab's own buttons)
    private static void addButtons(final Widget item, final List<ToolbarButton> buttons) {
        if (item instanceof final ToolbarButton button) {
            if (!button.getStyleName().contains("toolbar-button--invisible")) {
                buttons.add(button);
            }
        } else if (item instanceof final Toolbar toolbar) {
            for (final Widget inner : toolbar.items()) {
                addButtons(inner, buttons);
            }
        }
    }

    // Whether what the toolbar shows fits its box, give or take a pixel's rounding
    private boolean fits() {
        final Element element = getElement();
        return element.getScrollWidth() <= element.getClientWidth() + 1;
    }

    private void showMore() {
        final List<Item> items = new ArrayList<>();
        for (final ToolbarButton button : getOverflowedButtons()) {
            items.add(new IconMenuItem.Builder()
                    .icon(button.getSvg())
                    .text(button.getTitle())
                    .enabled(button.isEnabled())
                    .command(() -> button.getElement().<ButtonElement>cast().click())
                    .build());
        }
        MenuRequest.fireFromOpener(more.getElement(),
                name == null
                        ? "More"
                        : "More " + name,
                items,
                new PopupPosition(new Rect(more.getElement()), PopupLocation.BELOW));
    }

    // Once, for every toolbar: fits each one shown again when the page's preference classes change
    // (e.g. the toolbar display preference), which can change their buttons' widths but not their
    // own size
    private static void watchPageClasses() {
        if (pageClassWatcher == null) {
            pageClassWatcher = observePageClasses(() -> {
                for (final Toolbar toolbar : new ArrayList<>(SHOWN)) {
                    toolbar.fit();
                }
            });
        }
    }

    private static native JavaScriptObject observePageClasses(Runnable onChange) /*-{
        var observer = new MutationObserver($entry(function () {
            onChange.@java.lang.Runnable::run()();
        }));
        observer.observe($doc.documentElement, {attributes: true, attributeFilter: ['class']});
        return observer;
    }-*/;
}
