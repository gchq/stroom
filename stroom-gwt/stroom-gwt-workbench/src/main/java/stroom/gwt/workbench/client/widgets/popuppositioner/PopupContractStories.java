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


package stroom.gwt.workbench.client.widgets.popuppositioner;

import stroom.gwt.workbench.client.widgets.StoryPopups;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.play.Value;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupPosition.PopupLocation;
import stroom.widget.popup.client.presenter.Position;
import stroom.widget.popup.client.presenter.PositionUtil;
import stroom.widget.util.client.Rect;

import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Style;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// The popup contract: how a popup should open, close, nest and be placed, as one parametrised
/// suite over the popups of Stroom's widgets.
///
/// Each story is a clause of the contract run over every case of a registry ([#cases()]), made of
/// the real Stroom widgets, which says per case what GWT does where that differs from the contract
/// (e.g. a date box handles Escape itself, so it is left out of `EscapeCloses`), so each clause
/// asserts what GWT does.
public final class PopupContractStories {

    private static final String NEIGHBOUR_ID = "popup-contract-neighbour";
    private static final String NEIGHBOUR = "#" + NEIGHBOUR_ID;
    // The neighbour button's clicks, counted with a listener
    private static final String NEIGHBOUR_CLICK = "neighbourClick";

    private static final String HELP_PANEL = ".help-button-tooltip";
    private static final String QUICK_FILTER_PANEL = ".quickFilter-tooltip:not(.help-button-tooltip)";
    // Stroom's menus and submenus are each a `simplePopup-popup` holding a cell table of
    // `menuItem-outer` rows
    private static final String MENU_PANEL = ".simplePopup-popup:has(.menuItem-outer)";
    private static final String MENU_ROW = ".menuCellTable > tbody > tr";
    // A row's content, which the menu's cell table handles mouse events on (not on the row)
    private static final String MENU_ITEM = ".menuItem-outer";
    private static final String MENU_TEXT = ".menuItem-text, .menuItem-simpleText";
    private static final String OUTER_PANEL = ".simplePopup-popup:has(.cascade-outer-panel)";
    private static final String INNER_PANEL = ".simplePopup-popup:has(.cascade-inner-panel)";

    private static final List<PopupCase> CASES = cases();
    private static final PopupCase MENU_CASE = find("MenuPanel");
    // Every popup any story opens, for counting the open popups
    private static final String ALL_PANELS = allPanels();

    private PopupContractStories() {
        // Static utility
    }

    /// The registry: the cases, with what GWT does per clause.
    private static List<PopupCase> cases() {
        final List<PopupCase> cases = new ArrayList<>();
        cases.add(PopupCase.of("HelpButton", PopupHarnesses::helpButton)
                .opener("button.help-button")
                // The button and the icon inside it
                .partners("button.help-button", "button.help-button svg")
                .panel(HELP_PANEL)
                .insideTarget(HELP_PANEL + " h4")
                .placement("button.help-button", PopupLocation.RIGHT, 0,
                        "ShowHelpEvent — new PopupPosition(new Rect(element), PopupLocation.RIGHT)"));
        cases.add(PopupCase.of("QuickFilter help", PopupHarnesses::quickFilter)
                .opener(".quickFilter button.help-button")
                .partners(".quickFilter button.help-button", ".quickFilter button.help-button svg")
                .panel(QUICK_FILTER_PANEL)
                .insideTarget(QUICK_FILTER_PANEL));
        cases.add(PopupCase.of("FormGroup help", PopupHarnesses::formGroup)
                .opener("button.form-group-help")
                .partners("button.form-group-help", "button.form-group-help svg")
                .panel(HELP_PANEL)
                .insideTarget(HELP_PANEL)
                .placement("button.form-group-help", PopupLocation.RIGHT, 0,
                        "the same ShowHelpEvent default — FormGroup's help is a HelpButton"));
        cases.add(PopupCase.of("TypeFilter", PopupHarnesses::typeFilter)
                .opener(".type-filter-trigger")
                .partners(".type-filter-trigger")
                // The type filter's list is also a `menuCellTable`, but without menu rows
                .panel(".simplePopup-popup:has(.menuCellTable):not(:has(.menuItem-outer))")
                .insideTarget(".simplePopup-popup:not(:has(.menuItem-outer)) .simplePopup-content")
                // Left out of EscapeCloses: Escape is handled by the popup's list, not the popup
                // (TypeFilterSelectionEventManager.onClose → TypeFilterPresenter.escape() → hideSelf())
                .storyCases(true, false)
                .placement(".type-filter-trigger", PopupLocation.RIGHT, 3,
                        "TypeFilterPresenter.show — new Rect(element).grow(3), PopupLocation.RIGHT"));
        cases.add(PopupCase.of("Popover (SettingBlock)", PopupHarnesses::settingBlock)
                .opener(".setting-block")
                // The block and the label inside it
                .partners(".setting-block", ".setting-block .gwt-Label")
                .panel(".simplePopup-popup:has(.popover-body)")
                .insideTarget(".popover-body"));
        cases.add(PopupCase.of("SelectionBox", PopupHarnesses::selectionBox)
                .opener(".svgIconBox-icon-outer")
                // Both of GWT's partners (BaseSelectionBox: the text box and the icon box), each
                // with an inner child
                .partners(".SelectionBox-outer", ".SelectionBox-renderBox", ".svgIconBox-icon-outer",
                        ".svgIconBox-icon-inner")
                .panel(".SelectionPopup")
                .insideTarget(".selectionList")
                // SelectionPopup positions itself with a shadow width of 4
                // (PositionUtil.getPosition(4, ...)), which grows the anchor by 4px
                .placement("input.SelectionBox-textBox", PopupLocation.BELOW, 4,
                        "SelectionPopup.show — new PopupPosition(new Rect(relativeElement), BELOW)"));
        cases.add(PopupCase.of("CustomDateBox", PopupHarnesses::dateBox)
                .opener("input")
                .partners("input")
                .panel(".dateBoxPopup")
                // GWT's DatePicker's days grid is `datePickerDays`
                .insideTarget(".dateBoxPopup .datePickerDays")
                // MyDateBox.showDatePicker() is guarded by `if (!popup.isShowing())`, and the box
                // handles Escape itself (DateBoxHandler.onKeyDown)
                .storyCases(false, false)
                .triggerToggles(false)
                .placement("input", PopupLocation.BELOW, 0, "MyDateBox — popup.showRelativeTo(this)"));
        cases.add(PopupCase.of("MenuPanel", PopupHarnesses::menu)
                .opener(".menu-harness-trigger")
                .partners(".menu-harness-trigger")
                .panel(MENU_PANEL)
                // The panel's own content box, not a row — clicking a row runs its command
                .insideTarget(MENU_PANEL + " .simplePopup-content")
                // Left out of EscapeCloses: a menu's Escape (MenuViewImpl.escape() →
                // MenuPresenter.hideAll()) is checked by EscapeClosesTheWholeMenuHierarchy
                .storyCases(true, false)
                // A Stroom menu shown with ShowMenuEvent is placed where the call site asks
                .placement(".menu-harness-trigger", PopupLocation.BELOW, 0,
                        "ShowMenuEvent — new PopupPosition(new Rect(button), PopupLocation.BELOW)"));
        return cases;
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("Widgets/PopupPositioner/Popup contract", PopupContractStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // §2a Dismissal
                .story("OutsideClickReachesTarget", PopupContractStories::render)
                .withPlay(play -> {
                    for (final PopupCase c : CASES) {
                        final Value<Integer> before = play.capture("before",
                                play.spy(NEIGHBOUR_CLICK).callCount());
                        openPopup(play, c);
                        play.click(play.querySelector(NEIGHBOUR));
                        expectClosed(play, c);
                        play.expect(c.getName() + ": the dismissing click must reach the button",
                                () -> play.spy(NEIGHBOUR_CLICK).getCallCount() - before.get()).toBe(1);
                        closeAll(play);
                    }
                })
                .story("TriggerClickClosesAndStaysClosed", PopupContractStories::render)
                .withPlay(play -> {
                    for (final PopupCase c : CASES) {
                        if (c.isInTriggerStory()) {
                            openPopup(play, c);
                            play.click(play.querySelector(c.getOpener()));
                            if (c.isTriggerToggles()) {
                                expectClosed(play, c);
                            }
                            // And still closed a beat later — a reopen would land on the next frame
                            play.sleep(60);
                            // A case marked triggerToggles(false) in cases() stays open
                            play.expect(c.getName() + ": must stay closed",
                                    () -> PopupDom.isOpen(c.getPanel())).toBe(!c.isTriggerToggles());
                            closeAll(play);
                        }
                    }
                })
                .story("EveryRegisteredPartnerIsExempt", PopupContractStories::render)
                .withPlay(play -> {
                    for (final PopupCase c : CASES) {
                        for (final String partner : c.getPartners()) {
                            openPopup(play, c);
                            play.fireEvent().mouseDown(play.querySelector(partner));
                            // Give auto-hide a frame in which to have wrongly fired
                            play.sleep(30);
                            // A case marked partnersExempt(false) in cases() is auto-hidden instead
                            play.expect(c.getName() + ": mousedown on partner \"" + partner
                                                + "\" must not auto-hide the popup",
                                    () -> PopupDom.isOpen(c.getPanel())).toBe(c.isPartnersExempt());
                            closeAll(play);
                        }
                    }
                })
                .story("NonPartnerMousedownDoesAutoHide", PopupContractStories::render)
                .withPlay(play -> {
                    for (final PopupCase c : CASES) {
                        openPopup(play, c);
                        play.fireEvent().mouseDown(play.querySelector(NEIGHBOUR));
                        expectClosed(play, c);
                        closeAll(play);
                    }
                })
                .story("InsideClickKeepsItOpen", PopupContractStories::render)
                .withPlay(play -> {
                    for (final PopupCase c : CASES) {
                        openPopup(play, c);
                        play.click(play.screen().querySelector(c.getInsideTarget()));
                        play.expect(c.getName() + ": a click inside must not auto-hide it",
                                () -> PopupDom.isOpen(c.getPanel())).toBe(true);
                        closeAll(play);
                    }
                })
                .story("EscapeCloses", PopupContractStories::render)
                .withPlay(play -> {
                    for (final PopupCase c : CASES) {
                        if (c.isInEscapeStory()) {
                            openPopup(play, c);
                            play.keyboard("{Escape}");
                            if (c.isEscapeCloses()) {
                                expectClosed(play, c);
                            } else {
                                // A case marked escapeCloses(false) in cases() ignores Escape
                                play.sleep(30);
                                play.expect(c.getName() + ": Escape is ignored",
                                        () -> PopupDom.isOpen(c.getPanel())).toBe(true);
                            }
                            closeAll(play);
                        }
                    }
                })
                .story("NoShieldBehindAnyPopup", PopupContractStories::render)
                .withPlay(play -> {
                    for (final PopupCase c : CASES) {
                        openPopup(play, c);
                        play.expect(c.getName() + ": no full-viewport layer may sit behind a popup",
                                () -> PopupDom.shields(c.getPanel())).toEqual(Collections.emptyList());
                        closeAll(play);
                    }
                })
                // Nesting: the cascade, and which popup takes the key
                .story("ClosingAPortalledParentClosesItsChild", PopupContractStories::render)
                .withPlay(play -> {
                    openPortalCascade(play);
                    // Dismiss the outer popup by clicking away from both
                    play.click(play.querySelector(NEIGHBOUR));
                    play.waitFor(() -> play.expect("outer should close", () -> PopupDom.isOpen(OUTER_PANEL))
                            .toBe(false));
                    play.expect("the child it opened must close with it, not be orphaned",
                            () -> PopupDom.isOpen(INNER_PANEL)).toBe(false);
                    closeAll(play);
                })
                .story("ClickInsideAPortalledChildKeepsTheParentOpen", PopupContractStories::render)
                .withPlay(play -> {
                    openPortalCascade(play);
                    play.fireEvent().mouseDown(play.screen().querySelector(INNER_PANEL));
                    play.sleep(30);
                    play.expect("parent must stay open", () -> PopupDom.isOpen(OUTER_PANEL)).toBe(true);
                    play.expect("child must stay open", () -> PopupDom.isOpen(INNER_PANEL)).toBe(true);
                    closeAll(play);
                })
                .story("EscapeClosesTheInnermostPopupOnly", PopupContractStories::render)
                .withPlay(play -> {
                    openPortalCascade(play);
                    play.keyboard("{Escape}");
                    play.waitFor(() -> play.expect("innermost should close", () -> PopupDom.isOpen(INNER_PANEL))
                            .toBe(false));
                    play.expect("its opener must stay open", () -> PopupDom.isOpen(OUTER_PANEL)).toBe(true);
                    // A second Escape then takes the parent
                    play.keyboard("{Escape}");
                    play.waitFor(() -> play.expect(() -> PopupDom.isOpen(OUTER_PANEL)).toBe(false));
                    closeAll(play);
                })
                .story("NestedRealWidgetsRouteEscapeToTheInnermost", PopupContractStories::render)
                .withPlay(play -> {
                    final PopupCase sel = find("SelectionBox");
                    openPopup(play, sel);
                    play.click(play.screen().querySelector(sel.getPanel() + " button.help-button"));
                    // The help popup is on the page's body, not in the list
                    play.waitFor(() -> play.expect("help should open", () -> PopupDom.isOpen(QUICK_FILTER_PANEL))
                            .toBe(true));
                    play.keyboard("{Escape}");
                    play.waitFor(() -> play.expect("help should close", () -> PopupDom.isOpen(QUICK_FILTER_PANEL))
                            .toBe(false));
                    play.expect("the field list must survive closing its help",
                            () -> PopupDom.isOpen(sel.getPanel())).toBe(true);
                    closeAll(play);
                })
                .story("RegistryDoesNotLeak", PopupContractStories::render)
                .withPlay(play -> {
                    // GWT has no registry of open popups, so this counts the shown popups of every
                    // case
                    play.expect(() -> PopupDom.countOpen(ALL_PANELS)).toBe(0);
                    for (final PopupCase c : CASES) {
                        openPopup(play, c);
                        play.expect(c.getName() + ": should be registered while open",
                                () -> PopupDom.countOpen(ALL_PANELS)).toBeGreaterThan(0);
                        closeAll(play);
                        play.expect(c.getName() + ": should unregister on close",
                                () -> PopupDom.countOpen(ALL_PANELS)).toBe(0);
                    }
                })
                // §2b Positioning
                .story("NeverCoversItsTrigger", PopupContractStories::render)
                .withPlay(play -> {
                    for (final PopupCase c : CASES) {
                        openPopup(play, c);
                        play.expect(c.getName() + ": popup must not cover its own trigger",
                                () -> PopupDom.overlaps(PopupDom.rect(c.getPanel()), PopupDom.rect(c.getOpener())))
                                .toBe(false);
                        closeAll(play);
                    }
                })
                .story("PlacedOnItsRequestedSide", PopupContractStories::render)
                .withPlay(PopupContractStories::placedOnItsRequestedSide)
                .story("QuickFilterHelpUsesGwtsOwnOffset", PopupContractStories::render)
                .withPlay(play -> {
                    final PopupCase c = find("QuickFilter help");
                    openPopup(play, c);
                    final String filter = c.within(".quickFilter");
                    play.expect("left should be filter.left + 4",
                                    () -> Math.abs(PopupDom.rect(c.getPanel())[0] - (PopupDom.rect(filter)[0] + 4)))
                            .toBeLessThanOrEqual(1);
                    play.expect("top should be filter.top + 26",
                                    () -> Math.abs(PopupDom.rect(c.getPanel())[1] - (PopupDom.rect(filter)[1] + 26)))
                            .toBeLessThanOrEqual(1);
                    // The regression that motivated this: the popup must be on screen
                    play.expect("popup must not render below the fold", () -> PopupDom.rect(c.getPanel())[1])
                            .toBeLessThan(Window.getClientHeight());
                    closeAll(play);
                })
                .story("PositionerFlipsAndClamps", PopupContractStories::render)
                .withPlay(PopupContractStories::positionerFlipsAndClamps)
                // §6 Submenus
                .story("SubmenuOpensClearOfItsParent", PopupContractStories::render)
                .withPlay(play -> {
                    openSubmenu(play);
                    play.expect("submenu was flipped — harness too near the right edge",
                            () -> menu(1)[0] >= menuRow(0, 1)[2]).toBe(true);
                    play.expect("a submenu must not cover the item that opened it",
                            () -> PopupDom.overlaps(menu(1), menuRow(0, 1))).toBe(false);
                    closeAll(play);
                })
                .story("SubmenuFirstItemAlignsWithItsParentRow", PopupContractStories::render)
                .withPlay(PopupContractStories::submenuFirstItemAligns)
                .story("MenuPaintsExactlyOneBorder", PopupContractStories::render)
                .withPlay(play -> {
                    openNestedSubmenus(play);
                    final String rootMenu = MENU_PANEL;
                    play.expect("a menu must paint one border", () -> PopupDom.paintedBorders(rootMenu))
                            .toHaveLength(1);
                    play.expect(() -> PopupDom.paintedBorders(rootMenu).get(0))
                            .toContain("simplePopup-background-behind");
                    // And the first row sits 4px below the panel's top edge, the inset that the
                    // submenu anchor's outset cancels
                    play.expect("first-row inset should be 4px",
                            () -> Math.abs(menuRow(0, 0)[1] - menu(0)[1] - 4)).toBeLessThanOrEqual(0.5);
                    closeAll(play);
                })
                .story("SubmenuAnchorIsClippedToItsMenu", PopupContractStories::render)
                .withPlay(PopupContractStories::submenuAnchorIsClipped)
                .story("ClosingAMenuClosesItsDescendants", PopupContractStories::render)
                .withPlay(play -> {
                    openNestedSubmenus(play);
                    // Click the root's parent row, which toggles its submenu shut.
                    // The submenu was opened by the keyboard, and Stroom only toggles shut a
                    // submenu that a click opened, so that a click meant to open a submenu that
                    // hovering has only just opened doesn't close it. The first click (whose
                    // mousedown auto-hides the innermost level) claims it; the second closes it
                    final Query parentRow = play.screen().querySelectorAll(MENU_PANEL + " " + MENU_ITEM).nth(1);
                    play.click(parentRow);
                    play.waitFor(() -> play.expect(PopupContractStories::visibleMenus).toBe(2));
                    play.click(parentRow);
                    play.waitFor(() -> play.expect(
                            "closing a submenu must close the submenu it had open, leaving only the root",
                            PopupContractStories::visibleMenus).toBe(1));
                    play.expect("the root menu itself must stay open", () -> menuRow(0, 0)[3] > 0).toBe(true);
                    closeAll(play);
                })
                .story("EscapeClosesTheWholeMenuHierarchy", PopupContractStories::render)
                .withPlay(play -> {
                    openNestedSubmenus(play);
                    play.keyboard("{Escape}");
                    // The innermost menu's popup takes Escape as its close action, and closes the
                    // whole menu (it once closed only its own level)
                    play.waitFor(() -> play.expect("Escape closes every level",
                            PopupContractStories::visibleMenus).toBe(0));
                    closeAll(play);
                })
                .story("ArrowLeftClosesOnlyTheCurrentLevel", PopupContractStories::render)
                .withPlay(play -> {
                    openNestedSubmenus(play);
                    play.keyboard("{ArrowLeft}");
                    play.waitFor(() -> play.expect("ArrowLeft should close just the deepest level",
                            PopupContractStories::visibleMenus).toBe(2));
                    play.keyboard("{ArrowLeft}");
                    play.waitFor(() -> play.expect(PopupContractStories::visibleMenus).toBe(1));
                    closeAll(play);
                })
                .story("OutsideClickClosesTheWholeChain", PopupContractStories::render)
                .withPlay(play -> {
                    final Value<Integer> before = play.capture("before", play.spy(NEIGHBOUR_CLICK).callCount());
                    // From the deepest level, so the whole chain has to come down at once
                    openNestedSubmenus(play);
                    play.click(play.querySelector(NEIGHBOUR));
                    play.waitFor(() -> play.expect(PopupContractStories::visibleMenus).toBe(0));
                    play.expect("the dismissing click must reach the button",
                            () -> play.spy(NEIGHBOUR_CLICK).getCallCount() - before.get()).toBe(1);
                    closeAll(play);
                })
                .story("ParentItemClickTogglesItsSubmenu", PopupContractStories::render)
                .withPlay(play -> {
                    openSubmenu(play);
                    final Query parentRow = play.screen().querySelectorAll(MENU_PANEL + " " + MENU_ITEM).nth(1);
                    // The submenu was opened by the keyboard, and Stroom only toggles shut a
                    // submenu that a click opened (see ClosingAMenuClosesItsDescendants),
                    // so the first click on its parent row leaves it open
                    play.click(parentRow);
                    play.sleep(60);
                    play.expect(PopupContractStories::visibleMenus).toBe(2);
                    // Clicking the open parent row again closes just its submenu; the parent menu
                    // stays up
                    play.click(parentRow);
                    play.waitFor(() -> play.expect(PopupContractStories::visibleMenus).toBe(1));
                    // ... clicking it again opens it again, and a click then closes that
                    play.click(parentRow);
                    play.waitFor(() -> play.expect(PopupContractStories::visibleMenus).toBe(2));
                    play.click(parentRow);
                    play.waitFor(() -> play.expect(PopupContractStories::visibleMenus).toBe(1));
                    // A submenu that hovering opened isn't closed by a click on its parent row (the
                    // click was likely meant to open it)
                    play.hover(play.screen().querySelectorAll(MENU_PANEL + " " + MENU_ITEM).nth(0));
                    play.hover(parentRow);
                    play.waitFor(() -> play.expect(PopupContractStories::visibleMenus).toBe(2));
                    play.click(parentRow);
                    play.sleep(60);
                    play.expect(PopupContractStories::visibleMenus).toBe(2);
                    closeAll(play);
                });
    }

    /// Every case in its own labelled container, in a grid so that every trigger is in the
    /// viewport, with the portal cascade and a neighbour button beside them.
    private static Widget render(final StoryContext context) {
        final Spy neighbourClick = context.fn(NEIGHBOUR_CLICK);
        final StoryPopups popups = StoryPopups.create(context).withHelp().withMenus();

        final FlowPanel grid = new FlowPanel();
        final Style style = grid.getElement().getStyle();
        style.setProperty("display", "grid");
        style.setProperty("gridTemplateColumns", "repeat(3, minmax(0, 240px))");
        style.setProperty("gap", "40px");
        style.setProperty("padding", "16px");
        style.setProperty("alignItems", "start");
        for (final PopupCase c : CASES) {
            grid.add(container(c.getName(), c.getName(), c.render(popups)));
        }
        // Not a case — it exists only for the cascade stories, which need a child popup that is
        // not a DOM descendant of its parent
        grid.add(container("PortalCascade", "Portal cascade", PopupHarnesses.portalCascade(popups)));
        // A neighbour button beside the popups: the dismissing click must both close the popup
        // and press this
        final Button neighbour = new Button("Neighbour");
        neighbour.getElement().setId(NEIGHBOUR_ID);
        neighbour.addClickHandler(event -> neighbourClick.call());
        grid.add(neighbour);
        return grid;
    }

    private static Widget container(final String name, final String label, final Widget widget) {
        final FlowPanel container = new FlowPanel();
        container.getElement().setAttribute("data-case", name);
        container.getElement().getStyle().setProperty("position", "relative");
        final Label caption = new Label(label);
        final Style style = caption.getElement().getStyle();
        style.setProperty("fontSize", "11px");
        style.setProperty("opacity", "0.6");
        style.setProperty("marginBottom", "4px");
        container.add(caption);
        container.add(widget);
        return container;
    }

    private static void openPopup(final Play play, final PopupCase c) {
        play.click(play.querySelector(c.getOpener()));
        play.waitFor(() -> play.expect(c.getName() + ": should be open", () -> PopupDom.isOpen(c.getPanel()))
                .toBe(true));
    }

    private static void expectClosed(final Play play, final PopupCase c) {
        play.waitFor(() -> play.expect(c.getName() + ": should be closed", () -> PopupDom.isOpen(c.getPanel()))
                .toBe(false));
    }

    /// Close whatever is open, so cases do not leak into each other.
    private static void closeAll(final Play play) {
        play.keyboard("{Escape}");
        play.click(play.body());
        play.waitFor(() -> play.expect("open popups", () -> PopupDom.countOpen(ALL_PANELS)).toBe(0));
    }

    private static void openPortalCascade(final Play play) {
        play.click(play.querySelector(".cascade-outer-trigger"));
        play.waitFor(() -> play.expect(() -> PopupDom.isOpen(OUTER_PANEL)).toBe(true));
        play.click(play.screen().querySelector(OUTER_PANEL + " .cascade-inner-trigger"));
        play.waitFor(() -> play.expect(() -> PopupDom.isOpen(INNER_PANEL)).toBe(true));
    }

    /// Open the root menu and the submenu of its parent row.
    private static void openSubmenu(final Play play) {
        openPopup(play, MENU_CASE);
        // Stroom's Menu focuses the root's first row when it is shown, so one ArrowDown reaches
        // "Parent", and moving onto a parent row opens its submenu at once. ArrowRight then moves
        // the focus into the submenu, which can only take it once it is shown, so the keys are
        // pressed one at a time.
        play.waitFor(() -> play.expect("the menu should have the focus", () -> menuHasFocus(0)).toBe(true));
        play.keyboard("{ArrowDown}");
        play.waitFor(() -> play.expect(PopupContractStories::visibleMenus).toBe(2));
        play.keyboard("{ArrowRight}");
        play.waitFor(() -> play.expect("the submenu should have the focus", () -> menuHasFocus(1)).toBe(true));
    }

    /// Open all three levels: root → "Parent" → "Nested parent".
    private static void openNestedSubmenus(final Play play) {
        openSubmenu(play);
        // The submenu is now the active panel, so keys route to it: down onto "Nested parent",
        // then right
        play.keyboard("{ArrowDown}");
        play.waitFor(() -> play.expect("all three levels should be open", PopupContractStories::visibleMenus)
                .toBe(3));
        play.keyboard("{ArrowRight}");
        play.waitFor(() -> play.expect("the innermost menu should have the focus", () -> menuHasFocus(2))
                .toBe(true));
    }

    private static void placedOnItsRequestedSide(final Play play) {
        final List<PopupCase> placed = new ArrayList<>();
        for (final PopupCase c : CASES) {
            if (c.getPlacement() != null) {
                placed.add(c);
            }
        }
        // Guard against the list silently emptying
        play.expect("no case declares an expected placement", placed::size).toBeGreaterThanOrEqual(5);

        for (final PopupCase c : placed) {
            final PopupCase.Placement pl = c.getPlacement();
            openPopup(play, c);
            final String why = c.getName() + ": expected " + pl.getLocation()
                               + (pl.getShadow() > 0
                    ? " grow(" + pl.getShadow() + ")"
                    : "") + " per " + pl.getGwt();
            play.expect(why + " — left", () -> {
                final Element panel = PopupDom.element(c.getPanel());
                return Math.abs(panel.getAbsoluteLeft() - expectedPosition(c, panel).getLeft());
            }).toBeLessThanOrEqual(1);
            play.expect(why + " — top", () -> {
                final Element panel = PopupDom.element(c.getPanel());
                return Math.abs(panel.getAbsoluteTop() - expectedPosition(c, panel).getTop());
            }).toBeLessThanOrEqual(1);
            closeAll(play);
        }
    }

    /// Recomputes `PositionUtil.getPosition` from the live anchor and the live panel size.
    private static Position expectedPosition(final PopupCase c, final Element panel) {
        final PopupCase.Placement pl = c.getPlacement();
        final Rect anchor = new Rect(PopupDom.element(c.within(pl.getAnchor()))).grow(pl.getShadow());
        return PositionUtil.getPosition(new PopupPosition(anchor, pl.getLocation()),
                panel.getOffsetWidth(),
                panel.getOffsetHeight());
    }

    /// `position/flips-at-the-viewport-edge` and `position/clamps-when-neither-side-fits`,
    /// asserted against GWT's `PositionUtil.getPosition`.
    private static void positionerFlipsAndClamps(final Play play) {
        // PositionUtil reads the window's size, so the anchors near the edges are placed relative
        // to the real viewport
        final double vw = Window.getClientWidth();
        final double vh = Window.getClientHeight();

        // RIGHT with room: flush against the anchor's right edge, top-aligned
        expectPosition(play, rect(100, 200, 140, 220), PopupLocation.RIGHT, 300, 100, 140, 200);
        // RIGHT with no room to the right but room to the left: the popup's right edge is aligned
        // with the anchor's right edge, so it overlaps the anchor
        expectPosition(play, rect(vw - 100, 200, vw - 60, 220), PopupLocation.RIGHT, 300, 100, vw - 360, 200);
        // BELOW with room: aligned to the anchor's left edge, under its bottom
        expectPosition(play, rect(100, 200, 140, 220), PopupLocation.BELOW, 300, 100, 100, 220);
        // BELOW with no room below but room above: flips to sit on the anchor's top edge
        expectPosition(play, rect(100, vh - 100, 140, vh - 40), PopupLocation.BELOW, 300, 100, 100, vh - 200);
        // Neither side fits (popup taller than the viewport): clamped to the top edge
        expectPosition(play, rect(100, 400, 140, 420), PopupLocation.BELOW, 300, (int) vh + 100, 100, 0);
        // Neither side fits horizontally (popup wider than the viewport): clamped to the left edge
        expectPosition(play, rect(500, 200, 540, 220), PopupLocation.RIGHT, (int) vw + 200, 100, 0, 200);

        // The result is always fully on screen when the popup fits at all
        final Rect corner = rect(vw - 20, vh - 20, vw, vh);
        play.expect(() -> position(corner, PopupLocation.BELOW, 300, 100).getLeft() + 300)
                .toBeLessThanOrEqual(vw);
        play.expect(() -> position(corner, PopupLocation.BELOW, 300, 100).getTop() + 100)
                .toBeLessThanOrEqual(vh);
        play.expect(() -> position(corner, PopupLocation.BELOW, 300, 100).getLeft()).toBeGreaterThanOrEqual(0);
        play.expect(() -> position(corner, PopupLocation.BELOW, 300, 100).getTop()).toBeGreaterThanOrEqual(0);
    }

    private static void expectPosition(final Play play,
                                       final Rect anchor,
                                       final PopupLocation location,
                                       final int width,
                                       final int height,
                                       final double left,
                                       final double top) {
        play.expect(() -> toMap(position(anchor, location, width, height))).toEqual(toMap(left, top));
    }

    private static Position position(final Rect anchor,
                                     final PopupLocation location,
                                     final int width,
                                     final int height) {
        return PositionUtil.getPosition(new PopupPosition(anchor, location), width, height);
    }

    private static Map<String, Object> toMap(final Position position) {
        return toMap(position.getLeft(), position.getTop());
    }

    private static Map<String, Object> toMap(final double left, final double top) {
        final Map<String, Object> map = new LinkedHashMap<>();
        map.put("left", left);
        map.put("top", top);
        return map;
    }

    private static void submenuFirstItemAligns(final Play play) {
        openSubmenu(play);
        // "Lots of space": placed on the requested side, and not pushed back on screen
        play.expect("submenu was flipped — not the case under test", () -> menu(1)[0] >= menuRow(0, 1)[2])
                .toBe(true);
        play.expect("submenu was clamped vertically — not the case under test",
                () -> menu(1)[3] <= Window.getClientHeight()).toBe(true);
        play.expect("submenu label not level with its parent's",
                () -> Math.abs(menuText(1, 0)[1] - menuText(0, 1)[1])).toBeLessThanOrEqual(0.5);
        play.expect("submenu label centre off", () -> {
            final double[] st = menuText(1, 0);
            final double[] pt = menuText(0, 1);
            return Math.abs((st[1] + st[5] / 2) - (pt[1] + pt[5] / 2));
        }).toBeLessThanOrEqual(0.5);
        closeAll(play);
    }

    /// `submenu/anchor-clipped-when-item-partly-scrolled`, asserted on GWT's `Rect.min` and
    /// `growX`/`growY`, which `MenuPresenter.showSubMenu` anchors submenus with.
    private static void submenuAnchorIsClipped(final Play play) {
        final Rect panel = rect(100, 100, 300, 200);
        // A row hanging below the panel's bottom edge — scrolled half out of view
        final Rect row = rect(100, 180, 300, 260);
        play.expect(() -> toMap(Rect.min(panel, row))).toEqual(toMap(rect(100, 180, 300, 200)));
        // Anisotropic outset: 2px horizontal, 4px vertical (GWT growX(2).growY(4))
        play.expect(() -> toMap(Rect.min(panel, row).growX(2).growY(4))).toEqual(toMap(rect(98, 176, 302, 204)));
        // Disjoint rects give a degenerate, never an inverted, anchor
        final Rect disjoint = Rect.min(panel, rect(400, 400, 500, 500));
        play.expect(disjoint::getRight).toBeGreaterThanOrEqual(disjoint.getLeft());
        play.expect(disjoint::getBottom).toBeGreaterThanOrEqual(disjoint.getTop());
    }

    private static Map<String, Object> toMap(final Rect rect) {
        final Map<String, Object> map = new LinkedHashMap<>();
        map.put("left", rect.getLeft());
        map.put("top", rect.getTop());
        map.put("right", rect.getRight());
        map.put("bottom", rect.getBottom());
        return map;
    }

    /// A rect from its left, top, right and bottom.
    private static Rect rect(final double left, final double top, final double right, final double bottom) {
        return new Rect(top, bottom, left, right);
    }

    private static int visibleMenus() {
        return PopupDom.countOpen(MENU_PANEL);
    }

    private static double[] menu(final int index) {
        return PopupDom.rectOfShown(MENU_PANEL, index, null, 0);
    }

    private static double[] menuRow(final int index, final int row) {
        return PopupDom.rectOfShown(MENU_PANEL, index, MENU_ROW, row);
    }

    private static double[] menuText(final int index, final int row) {
        return PopupDom.rectOfShown(MENU_PANEL, index, MENU_ROW + ":nth-child(" + (row + 1) + ") :is("
                                                         + MENU_TEXT + ")", 0);
    }

    /// @param index The index of the shown menu, 0 for the root.
    /// @return True if the menu has the keyboard focus.
    private static boolean menuHasFocus(final int index) {
        return PopupDom.shownHasFocus(MENU_PANEL, index);
    }

    private static PopupCase find(final String name) {
        for (final PopupCase c : CASES) {
            if (c.getName().equals(name)) {
                return c;
            }
        }
        throw new IllegalArgumentException("No case " + name);
    }

    private static String allPanels() {
        final List<String> panels = new ArrayList<>();
        for (final PopupCase c : CASES) {
            panels.add(c.getPanel());
        }
        panels.add(OUTER_PANEL);
        panels.add(INNER_PANEL);
        return String.join(", ", panels);
    }
}
