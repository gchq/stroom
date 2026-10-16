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


package stroom.gwt.workbench.client.widgets.menu;

import stroom.gwt.workbench.client.widgets.StoryPopups;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.play.TextMatch;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.svg.shared.SvgImage;
import stroom.widget.button.client.Button;
import stroom.widget.button.client.Toolbar;
import stroom.widget.button.client.ToolbarButton;
import stroom.widget.menu.client.presenter.Item;
import stroom.widget.menu.client.presenter.ShowMenuEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupPosition.PopupLocation;
import stroom.widget.util.client.KeyBinding.Action;
import stroom.widget.util.client.Rect;

import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Style;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.Widget;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/// Stories for Stroom's menus (`ShowMenuEvent`, handled by Stroom's `Menu` with a
/// `MenuPresenter` per menu and submenu).
public final class MenuPanelStories {

    // Stroom's menu is a cell table with this class and `role="menu"`.
    static final String MENU = ".menuCellTable";
    // The highlighted row is the cell table's keyboard-selected row.
    private static final String ACTIVE_ROW = ".menuCellTable tr[class*='KeyboardSelectedRow']";
    private static final String NO_ACTION = "(no action yet)";
    private static final String ON_CHOSEN = "onChosen";
    private static final String OPTIONS_ID = "menuPanelStories-options";

    private MenuPanelStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        // Every story renders its own demo
        registry.component("Widgets/Menu/MenuPanel", MenuPanelStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // Icon + text items, a disabled item, and separators
                .story("Basic", context -> demo(context, "Open Menu",
                        "Basic context menu — icon + text items, disabled item, separator",
                        true,
                        result -> Arrays.asList(
                                // A Stroom menu item's shortcut is the key binding of its action,
                                // and there are no copy/cut actions, so Ctrl+C and Ctrl+X aren't
                                // shown.
                                MenuWidgets.icon("Copy", SvgImage.COPY, () -> result.accept("Copy")).build(),
                                MenuWidgets.icon("Cut", SvgImage.CLEAR, () -> result.accept("Cut")).build(),
                                MenuWidgets.iconDisabled("Paste", SvgImage.CLIPBOARD)
                                        .tooltip("Nothing in clipboard")
                                        .build(),
                                MenuWidgets.separator(),
                                MenuWidgets.simple("Select all", () -> result.accept("Select All")),
                                MenuWidgets.simple("Deselect", () -> result.accept("Deselect")),
                                MenuWidgets.separator(),
                                MenuWidgets.icon("Delete", SvgImage.DELETE, () -> result.accept("Delete")).build())))
                .withPlay(play -> {
                    // Open the menu, click an item — its action fires and the menu closes
                    play.click(play.getByRole("button", TextMatch.containingIgnoreCase("open menu")));
                    play.click(play.screen().findByText("Copy"));
                    play.waitFor(() -> play.expect(play.getByText("Copy", "strong")).toBeInTheDocument());
                })
                // Two levels of cascading submenus revealed on hover / Arrow Right
                .story("Submenus", context -> demo(context, "Open Menu",
                        "Cascading submenus — hover a parent row (or press Arrow Right) to reveal",
                        true,
                        result -> Arrays.asList(
                                // No shortcut, as Stroom has no "new" action
                                MenuWidgets.icon("New", SvgImage.ADD, () -> result.accept("New")).build(),
                                MenuWidgets.separator(),
                                MenuWidgets.parent("Export as…", SvgImage.ADD_BELOW,
                                        MenuWidgets.simple("CSV", () -> result.accept("Export CSV")),
                                        MenuWidgets.simple("JSON", () -> result.accept("Export JSON")),
                                        MenuWidgets.simple("XML", () -> result.accept("Export XML")),
                                        MenuWidgets.separator(),
                                        MenuWidgets.parent("Advanced…", null,
                                                MenuWidgets.simple("Parquet", () -> result.accept("Export Parquet")),
                                                MenuWidgets.simple("Avro", () -> result.accept("Export Avro")))),
                                MenuWidgets.parent("View", SvgImage.CODE,
                                        MenuWidgets.simple("Compact", () -> result.accept("View Compact")),
                                        MenuWidgets.simple("Detailed", () -> result.accept("View Detailed"))),
                                MenuWidgets.separator(),
                                MenuWidgets.icon("Delete", SvgImage.DELETE, () -> result.accept("Delete")).build())))
                .withPlay(play -> {
                    play.click(play.getByRole("button", TextMatch.containingIgnoreCase("open menu")));
                    final Query menu = play.screen().findByRole("menu");
                    // A parent item says it opens a sub menu; a plain item doesn't
                    play.expect(play.within(menu).getByRole("menuitem", TextMatch.containing("Export as")))
                            .toHaveAttribute("aria-haspopup", "menu");
                    play.expect(play.within(menu).getByRole("menuitem", TextMatch.containing("New")))
                            .not().toHaveAttribute("aria-haspopup");

                    // A sub menu is named by its parent item's text, without its arrow
                    play.waitFor(() -> play.expect(play.within(menu).getByRole("menuitem", TextMatch.containing("New")))
                            .toHaveFocus());
                    play.keyboard("{ArrowDown}");
                    // The root menu is the first: the sub menu may open once its item is reached
                    play.waitFor(() -> play.expect(play.within(play.screen().getAllByRole("menu").first())
                            .getByRole("menuitem", TextMatch.containing("Export as"))).toHaveFocus());
                    play.keyboard("{ArrowRight}");
                    final Query subMenu = play.screen().findByRole("menu", "Export as…");
                    play.expect(subMenu).toHaveAttribute("aria-label", "Export as…");
                    play.expect(subMenu).not().toHaveAttribute("aria-labelledby");
                    play.expect(play.within(subMenu).getByRole("menuitem", "CSV")).toBeInTheDocument();
                    play.keyboard("{Escape}");
                    play.waitFor(() -> play.expect(play.screen().queryByRole("menu")).toBeNull());
                })
                // Display-only info rows above interactive items
                .story("Info", context -> demo(context, "Open Menu",
                        "Info (non-interactive) rows — display-only context rows",
                        false,
                        result -> Arrays.asList(
                                MenuWidgets.info("Stream ID: 123456"),
                                MenuWidgets.info("Type: RAW_EVENTS"),
                                MenuWidgets.separator(),
                                MenuWidgets.simple("Open", () -> {
                                    // Does nothing
                                }),
                                MenuWidgets.simple("Close", () -> {
                                    // Does nothing
                                }))))
                // A highlighted (bold accent) menu item
                .story("Highlighted", context -> demo(context, "Open Menu",
                        "Highlighted (bold accent) menu item",
                        true,
                        result -> Arrays.asList(
                                // No Ctrl+A shortcut (Stroom's SELECT_ALL action would be
                                // misleading here)
                                MenuWidgets.icon("Normal action", SvgImage.ADD, () -> result.accept("Normal")).build(),
                                MenuWidgets.icon("Dangerous action!", SvgImage.DELETE, () -> result.accept("Danger!"))
                                        .highlight(true)
                                        .build(),
                                MenuWidgets.separator(),
                                MenuWidgets.icon("Another action", SvgImage.COPY, () -> result.accept("Another"))
                                        .build())))
                // Open the menu then use Arrow Up/Down, Enter, and Escape
                .story("Keyboard", context -> demo(context, "Open Menu (then use keyboard)",
                        "Keyboard navigation — open the menu then use ↑ ↓ arrows, Enter, Escape",
                        true,
                        result -> Arrays.asList(
                                MenuWidgets.icon("Open", SvgImage.OPEN, () -> result.accept("Open")).build(),
                                MenuWidgets.icon("Save", SvgImage.SAVE, () -> result.accept("Save"))
                                        .action(Action.ITEM_SAVE)
                                        .build(),
                                // No shortcut, as Stroom has no reload action
                                MenuWidgets.icon("Reload", SvgImage.REFRESH, () -> result.accept("Reload")).build(),
                                MenuWidgets.separator(),
                                MenuWidgets.iconDisabled("Print", SvgImage.CANCEL).build(),
                                MenuWidgets.separator(),
                                MenuWidgets.simple("Close", () -> result.accept("Close")))))
                .withPlay(play -> {
                    play.click(play.getByRole("button", TextMatch.containingIgnoreCase("open menu")));
                    // Stroom's Menu focuses the first enabled item when it is shown, so no focus()
                    // is needed
                    final Query menu = play.screen().findByRole("menu");
                    play.waitFor(() -> play.expect(menu).toBeVisible());
                    // Every item, the disabled one too, is a menu item; the separators aren't
                    play.expect(play.within(menu).getAllByRole("menuitem")).toHaveLength(5);
                    final Query print = play.within(menu).getByRole("menuitem", "Print");
                    play.expect(print).toHaveAttribute("aria-disabled", "true");
                    // The arrow keys reach the disabled item, so it is read out (as disabled), but
                    // Enter does nothing and the menu stays open
                    // Wait for the menu to focus its first item, or the keys may go elsewhere
                    play.waitFor(() -> play.expect(play.within(menu).getByRole("menuitem", "Open"))
                            .toHaveFocus());
                    play.keyboard("{End}");
                    play.waitFor(() -> play.expect(play.screen().querySelector(ACTIVE_ROW))
                            .toHaveTextContent(TextMatch.containing("Close")));
                    play.keyboard("{ArrowUp}");
                    play.waitFor(() -> play.expect(play.screen().querySelector(ACTIVE_ROW))
                            .toHaveTextContent(TextMatch.containing("Print")));
                    play.expect(print).toHaveFocus();
                    play.keyboard("{Enter}");
                    play.expect(menu).toBeVisible();
                    play.expect(play.getByText(NO_ACTION, "strong")).toBeInTheDocument();
                    // Home → first selectable row ("Open")
                    play.keyboard("{Home}");
                    play.waitFor(() -> play.expect(play.screen().querySelector(ACTIVE_ROW))
                            .toHaveTextContent(TextMatch.containing("Open")));
                    // Activate the highlighted "Open"
                    play.keyboard("{Enter}");
                    play.waitFor(() -> play.expect(play.getByText("Open", "strong")).toBeInTheDocument());
                })
                // A toolbar button marked as opening a menu says so, and whether its menu is open
                // (the ARIA menu button pattern); a plain opener says neither
                .story("MenuButton", MenuPanelStories::menuButton)
                .withPlay(play -> {
                    final Query actions = play.getByRole("button", "Show Actions");
                    final Query plain = play.getByRole("button", TextMatch.containing("Plain Opener"));
                    play.expect(actions).toHaveAttribute("aria-haspopup", "menu");
                    play.expect(actions).toHaveAttribute("aria-expanded", "false");

                    // Opened: expanded. Escape closes it: collapsed
                    play.click(actions);
                    final Query menu = play.screen().findByRole("menu");
                    play.waitFor(() -> play.expect(actions).toHaveAttribute("aria-expanded", "true"));
                    // The menu is named by the button that opened it, which is given an id for it
                    play.expect(actions).toHaveAttribute("id");
                    play.expect(menu).toHaveAttribute("aria-labelledby");
                    play.expect(menu).not().toHaveAttribute("aria-label");
                    play.expect(play.screen().getByRole("menu", "Show Actions")).toBeInTheDocument();
                    play.waitFor(() -> play.expect(play.within(menu).getByRole("menuitem", "Copy"))
                            .toHaveFocus());
                    play.keyboard("{Escape}");
                    play.waitFor(() -> play.expect(play.screen().queryByRole("menu")).toBeNull());
                    play.expect(actions).toHaveAttribute("aria-expanded", "false");

                    // Choosing an item closes it too
                    play.click(actions);
                    play.waitFor(() -> play.expect(actions).toHaveAttribute("aria-expanded", "true"));
                    play.click(play.within(play.screen().findByRole("menu")).getByRole("menuitem", "Copy"));
                    play.expect(play.spy(ON_CHOSEN)).toHaveBeenCalledWith("Copy");
                    play.waitFor(() -> play.expect(play.screen().queryByRole("menu")).toBeNull());
                    play.expect(actions).toHaveAttribute("aria-expanded", "false");

                    // The button is the menu's auto-hide partner and Stroom's Menu closes a menu
                    // shown again with the same items, so a second press toggles it closed
                    play.click(actions);
                    play.waitFor(() -> play.expect(actions).toHaveAttribute("aria-expanded", "true"));
                    play.click(actions);
                    play.waitFor(() -> play.expect(play.screen().queryByRole("menu")).toBeNull());
                    play.expect(actions).toHaveAttribute("aria-expanded", "false");

                    // Opening another menu closes this one, which says so; the plain opener isn't
                    // marked, so it gets no menu button attributes
                    play.click(actions);
                    play.waitFor(() -> play.expect(actions).toHaveAttribute("aria-expanded", "true"));
                    play.click(plain);
                    play.waitFor(() -> play.expect(play.screen().getByRole("menu")).toHaveTextContent(
                            TextMatch.containing("Refresh")));
                    play.expect(actions).toHaveAttribute("aria-expanded", "false");
                    play.expect(plain).not().toHaveAttribute("aria-expanded");
                    play.expect(plain).not().toHaveAttribute("aria-haspopup");
                    // An unmarked opener doesn't name its menu (Stroom names such menus explicitly)
                    play.expect(play.screen().getByRole("menu")).not().toHaveAttribute("aria-labelledby");
                    play.expect(play.screen().getByRole("menu")).not().toHaveAttribute("aria-label");
                    play.keyboard("{Escape}");
                    play.waitFor(() -> play.expect(play.screen().queryByRole("menu")).toBeNull());
                    play.expect(plain).not().toHaveAttribute("aria-expanded");

                    // A button that already has an id keeps it, and names its menu by it
                    final Query options = play.getByRole("button", "Options");
                    play.click(options);
                    final Query optionsMenu = play.screen().findByRole("menu", "Options");
                    play.expect(options).toHaveAttribute("id", OPTIONS_ID);
                    play.expect(optionsMenu).toHaveAttribute("aria-labelledby", OPTIONS_ID);
                    play.keyboard("{Escape}");
                    play.waitFor(() -> play.expect(play.screen().queryByRole("menu")).toBeNull());

                    // A menu given a name is named by it rather than by its (marked) button
                    final Query add = play.getByRole("button", "Add Thing");
                    play.click(add);
                    final Query addMenu = play.screen().findByRole("menu", "Add");
                    play.expect(addMenu).toHaveAttribute("aria-label", "Add");
                    play.expect(addMenu).not().toHaveAttribute("aria-labelledby");
                    play.expect(add).toHaveAttribute("aria-expanded", "true");
                    play.keyboard("{Escape}");
                    play.waitFor(() -> play.expect(play.screen().queryByRole("menu")).toBeNull());
                });
    }

    /// A toolbar whose 'Show Actions' button is marked as opening a menu, and a plain Stroom
    /// `Button` that opens another menu with itself as the opener but isn't marked (as a logo or a
    /// table cell that only positions a menu). The toolbar's 'Options' button already has an id,
    /// and its 'Add Thing' button's menu is given a name of its own.
    private static Widget menuButton(final StoryContext context) {
        final StoryPopups popups = StoryPopups.create(context).withMenus();
        final Spy onChosen = context.fn(ON_CHOSEN);

        final FlowPanel panel = new FlowPanel();
        panel.getElement().getStyle().setProperty("padding", "20px 16px");

        // The same list each time, so that Stroom's Menu toggles the menu on a second press
        final List<Item> actionItems = Arrays.asList(
                MenuWidgets.icon("Copy", SvgImage.COPY, () -> onChosen.call("Copy")).build(),
                MenuWidgets.icon("Delete", SvgImage.DELETE, () -> onChosen.call("Delete")).build());
        final Toolbar toolbar = new Toolbar("Item actions");
        final ToolbarButton actions = toolbar.button(SvgImage.ELLIPSES_VERTICAL, "Show Actions", () -> {
            // Set below, once the button exists
        });
        actions.setOnClick(() -> ShowMenuEvent.builder()
                .items(actionItems)
                .popupPosition(new PopupPosition(new Rect(actions.getElement()), PopupLocation.BELOW))
                .opener(actions.getElement())
                .fire(popups.getEventBus()::fireEvent));
        actions.setOpensMenu(true);

        final List<Item> optionItems = Arrays.asList(
                MenuWidgets.simple("Wrap Lines", () -> onChosen.call("Wrap Lines")));
        final ToolbarButton options = toolbar.button(SvgImage.SETTINGS, "Options", () -> {
            // Set below, once the button exists
        });
        options.getElement().setId(OPTIONS_ID);
        options.setOnClick(() -> ShowMenuEvent.builder()
                .items(optionItems)
                .popupPosition(new PopupPosition(new Rect(options.getElement()), PopupLocation.BELOW))
                .opener(options.getElement())
                .fire(popups.getEventBus()::fireEvent));
        options.setOpensMenu(true);

        final List<Item> addItems = Arrays.asList(
                MenuWidgets.simple("Folder", () -> onChosen.call("Folder")));
        final ToolbarButton add = toolbar.button(SvgImage.ADD, "Add Thing", () -> {
            // Set below, once the button exists
        });
        add.setOnClick(() -> ShowMenuEvent.builder()
                .name("Add")
                .items(addItems)
                .popupPosition(new PopupPosition(new Rect(add.getElement()), PopupLocation.BELOW))
                .opener(add.getElement())
                .fire(popups.getEventBus()::fireEvent));
        add.setOpensMenu(true);
        panel.add(toolbar);

        final List<Item> plainItems = Arrays.asList(
                MenuWidgets.simple("Refresh", () -> onChosen.call("Refresh")));
        final Button plain = new Button();
        plain.setText("Plain Opener ▾");
        plain.getElement().getStyle().setProperty("margin", "4px");
        plain.addClickHandler(event -> ShowMenuEvent.builder()
                .items(plainItems)
                .popupPosition(new PopupPosition(new Rect(plain.getElement()), PopupLocation.BELOW))
                .opener(plain.getElement())
                .fire(popups.getEventBus()::fireEvent));
        panel.add(plain);
        return panel;
    }

    /// A description, a button that toggles the menu below it, and (when `showResult`) the last
    /// action.
    private static Widget demo(final StoryContext context,
                               final String buttonLabel,
                               final String description,
                               final boolean showResult,
                               final Function<Consumer<String>, List<Item>> itemsFactory) {
        final StoryPopups popups = StoryPopups.create(context).withMenus();

        final FlowPanel panel = new FlowPanel();
        final FlowPanel demo = new FlowPanel();
        demo.getElement().getStyle().setProperty("padding", "20px 16px");
        final HTMLPanel descriptionPanel = new HTMLPanel("p", description);
        style(descriptionPanel.getElement(), "0 0 12px");
        demo.add(descriptionPanel);

        final HTMLPanel resultPanel = new HTMLPanel("p", "Last action: <strong></strong>");
        style(resultPanel.getElement(), "0 16px");
        final Element strong = resultPanel.getElement().getElementsByTagName("strong").getItem(0);
        strong.setInnerText(NO_ACTION);
        final List<Item> items = itemsFactory.apply(strong::setInnerText);

        // A Stroom Button, as Stroom's dialogs and forms use
        final Button button = new Button();
        button.setText(buttonLabel + " ▾");
        button.getElement().getStyle().setProperty("margin", "4px");
        // As Stroom's toolbar buttons show their menus: below the button, which is the menu's
        // auto-hide partner, so a second click toggles it closed
        button.addClickHandler(event -> ShowMenuEvent.builder()
                .items(items)
                .popupPosition(new PopupPosition(new Rect(button.getElement()), PopupLocation.BELOW))
                .addAutoHidePartner(button.getElement())
                .fire(popups.getEventBus()::fireEvent));
        demo.add(button);

        panel.add(demo);
        if (showResult) {
            panel.add(resultPanel);
        }
        return panel;
    }

    private static void style(final Element element, final String margin) {
        final Style style = element.getStyle();
        style.setProperty("color", "var(--text-color)");
        style.setProperty("fontSize", "0.85rem");
        style.setProperty("margin", margin);
    }
}
