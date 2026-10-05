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

package stroom.gwt.workbench.framework.client.manager;

import stroom.gwt.workbench.framework.client.BrowserUtil;
import stroom.gwt.workbench.framework.client.shortcuts.KeyCombo;
import stroom.gwt.workbench.framework.client.shortcuts.KeyEvents;
import stroom.gwt.workbench.framework.client.shortcuts.ShortcutAction;
import stroom.gwt.workbench.framework.client.shortcuts.ShortcutRecording;
import stroom.gwt.workbench.framework.client.shortcuts.Shortcuts;

import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.InputElement;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;

import java.util.function.Consumer;

/// The settings pages shown in place of the canvas and addon panel: 'About' and 'Keyboard
/// shortcuts', where the user can change the shortcuts, as in React Storybook.
public class SettingsPages {

    /// The name of the about page, as used in the URL.
    public static final String ABOUT = "about";
    /// The name of the keyboard shortcuts page, as used in the URL.
    public static final String SHORTCUTS = "shortcuts";

    private static final String TABS_ID = "wbm-settings-tabs";
    private static final String CONTENT_ID = "wbm-settings-content";
    private static final String CLOSE_ID = "wbm-settings-close";
    private static final String ATTR_PAGE = "data-settings-page";
    private static final String ATTR_SHORTCUT = "data-shortcut";
    private static final String RESTORE_ID = "wbm-shortcuts-restore";

    private static final String CLASS_SUCCESS = "wbm-shortcut-input--success";
    private static final String CLASS_ERROR = "wbm-shortcut-input--error";

    private static final String GITHUB_URL = "https://github.com/gchq/stroom";

    private final ShortcutHandler shortcutHandler;
    private final Consumer<String> pageSelectionHandler;
    private final Element tabsElement;
    private final Element contentElement;
    private String page;

    /// @param shortcutHandler      The keyboard shortcuts, which the shortcuts page edits.
    /// @param pageSelectionHandler Called with a page name when the user picks a tab, or null
    ///                             when they close the settings.
    public SettingsPages(final ShortcutHandler shortcutHandler,
                         final Consumer<String> pageSelectionHandler) {
        this.shortcutHandler = shortcutHandler;
        this.pageSelectionHandler = pageSelectionHandler;
        final Document document = Document.get();
        tabsElement = document.getElementById(TABS_ID);
        contentElement = document.getElementById(CONTENT_ID);

        BrowserUtil.addListener(tabsElement, "click", event -> {
            final Element tab = BrowserUtil.closest(Element.as(event.getEventTarget()), "[" + ATTR_PAGE + "]");
            if (tab != null) {
                pageSelectionHandler.accept(tab.getAttribute(ATTR_PAGE));
            }
        });
        BrowserUtil.addListener(document.getElementById(CLOSE_ID), "click",
                event -> pageSelectionHandler.accept(null));
        BrowserUtil.addListener(contentElement, "click", event -> {
            final Element target = Element.as(event.getEventTarget());
            if (BrowserUtil.closest(target, "#" + RESTORE_ID) != null) {
                shortcutHandler.getShortcuts().restoreDefaults();
                shortcutHandler.save();
                render();
            }
        });
        BrowserUtil.addListener(contentElement, "focusin", event -> onShortcutFocus(event, true));
        BrowserUtil.addListener(contentElement, "focusout", event -> onShortcutFocus(event, false));
        BrowserUtil.addListener(contentElement, "keydown", this::onShortcutKeyDown);
    }

    /// Called when the settings pages are hidden, e.g. when a story is shown. Makes sure the
    /// shortcuts run again, as the field recording a shortcut may not get a `focusout` event.
    public void hide() {
        shortcutHandler.setSuspended(false);
    }

    /// Shows a settings page.
    ///
    /// @param page The page, [#ABOUT] or [#SHORTCUTS].
    public void show(final String page) {
        this.page = SHORTCUTS.equals(page)
                ? SHORTCUTS
                : ABOUT;
        render();
    }

    private void render() {
        // Re-rendering removes any shortcut field being recorded into without a focusout event
        shortcutHandler.setSuspended(false);
        final SafeHtmlBuilder tabs = new SafeHtmlBuilder();
        appendTab(tabs, ABOUT, "About");
        appendTab(tabs, SHORTCUTS, "Keyboard shortcuts");
        tabsElement.setInnerSafeHtml(tabs.toSafeHtml());

        final SafeHtmlBuilder content = new SafeHtmlBuilder();
        if (SHORTCUTS.equals(page)) {
            renderShortcuts(content);
        } else {
            renderAbout(content);
        }
        contentElement.setInnerSafeHtml(content.toSafeHtml());
    }

    private void appendTab(final SafeHtmlBuilder builder, final String tabPage, final String label) {
        final boolean selected = tabPage.equals(page);
        builder.appendHtmlConstant("<button type=\"button\" role=\"tab\" class=\"wbm-tab"
                                   + (selected
                ? " wbm-tab--selected"
                : "")
                                   + "\" aria-selected=\"" + selected + "\" " + ATTR_PAGE + "=\""
                                   + tabPage + "\">")
                .appendEscaped(label)
                .appendHtmlConstant("</button>");
    }

    private static void renderAbout(final SafeHtmlBuilder builder) {
        builder.appendHtmlConstant("<div class=\"wbm-about\"><div class=\"wbm-about__logo\">");
        appendBrand(builder);
        builder.appendHtmlConstant("</div><div class=\"wbm-about__card\">"
                                   + "<div class=\"wbm-about__title\">Stroom GWT Workbench</div>"
                                   + "<p>The workbench shows Stroom's GWT widgets in isolation, so they can be "
                                   + "developed, tried with different arguments, tested and checked for "
                                   + "accessibility one at a time.</p>"
                                   + "<p>Stories are Java classes registered with a "
                                   + "<code>StoryRegistry</code> and compiled with GWT.</p>"
                                   + "</div>"
                                   + "<div class=\"wbm-about__links\">");
        appendLinkButton(builder, GITHUB_URL, "wbm-icon-github", "GitHub");
        // Credit for the ideas, icons and formats taken from Storybook, which isn't affiliated
        builder.appendHtmlConstant("</div><div class=\"wbm-about__footer\">Inspired by Storybook. "
                                   + "Some icons and formats are derived from Storybook (MIT licence). "
                                   + "Not affiliated with or endorsed by Storybook or Chromatic.</div></div>");
    }

    /// Appends the workbench's brand, i.e. Stroom's logo mark and the workbench's name.
    ///
    /// @param builder The builder to append to.
    static void appendBrand(final SafeHtmlBuilder builder) {
        builder.appendHtmlConstant("<span class=\"wbm-brand__mark-name\"><svg class=\"wbm-brand__mark\" "
                                   + "aria-hidden=\"true\"><use href=\"#wbm-logo\"></use></svg>"
                                   + "<span class=\"wbm-brand__name\">Stroom GWT Workbench</span></span>");
    }

    private static void appendLinkButton(final SafeHtmlBuilder builder,
                                         final String url,
                                         final String icon,
                                         final String label) {
        builder.appendHtmlConstant("<a class=\"wbm-link-button\" target=\"_blank\" rel=\"noopener noreferrer\" "
                                   + "href=\"" + url + "\">");
        MenuHtml.appendIcon(builder, "wbm-icon", icon);
        builder.appendEscaped(label).appendHtmlConstant("</a>");
    }

    private void renderShortcuts(final SafeHtmlBuilder builder) {
        final Shortcuts shortcuts = shortcutHandler.getShortcuts();
        builder.appendHtmlConstant("<div class=\"wbm-shortcuts\"><h1>Keyboard shortcuts</h1>"
                                   + "<div class=\"wbm-shortcuts__header\"><span>Commands</span>"
                                   + "<span>Shortcut</span></div>");
        for (final ShortcutAction action : ShortcutAction.values()) {
            builder.appendHtmlConstant("<div class=\"wbm-shortcuts__row\"><span>")
                    .appendEscaped(action.getLabel())
                    .appendHtmlConstant("</span><input class=\"wbm-shortcut-input\" readonly "
                                        + "spellcheck=\"false\" " + ATTR_SHORTCUT + "=\""
                                        + action.getId() + "\" aria-label=\"")
                    .appendEscaped(action.getLabel())
                    .appendHtmlConstant("\" value=\"")
                    .appendEscaped(displayString(shortcuts.get(action)))
                    .appendHtmlConstant("\"></div>");
        }
        builder.appendHtmlConstant("<button type=\"button\" class=\"wbm-restore-button\" id=\""
                                   + RESTORE_ID + "\">");
        MenuHtml.appendIcon(builder, "wbm-icon", "wbm-icon-undo");
        builder.appendHtmlConstant("Restore defaults</button></div>");
    }

    private void onShortcutFocus(final NativeEvent event, final boolean focused) {
        final InputElement input = getShortcutInput(event);
        if (input == null) {
            return;
        }
        // Stop shortcuts running while recording a new one
        shortcutHandler.setSuspended(focused);
        input.removeClassName(CLASS_ERROR);
        input.removeClassName(CLASS_SUCCESS);
        input.removeAttribute("title");
        final ShortcutAction action = ShortcutAction.fromId(input.getAttribute(ATTR_SHORTCUT));
        input.setValue(focused
                ? ""
                : displayString(shortcutHandler.getShortcuts().get(action)));
        input.setAttribute("placeholder", focused
                ? "Type keys"
                : "");
    }

    private void onShortcutKeyDown(final NativeEvent event) {
        final InputElement input = getShortcutInput(event);
        if (input == null) {
            return;
        }
        final KeyCombo combo = KeyEvents.toCombo(event);
        if (combo == null) {
            // Only a modifier so far
            return;
        }
        final ShortcutRecording.Outcome outcome = ShortcutRecording.classify(combo);
        if (outcome == ShortcutRecording.Outcome.PASS_THROUGH) {
            // Let Tab move the focus on, so the keyboard user isn't trapped in the field
            return;
        }
        event.preventDefault();
        final ShortcutAction action = ShortcutAction.fromId(input.getAttribute(ATTR_SHORTCUT));
        if (action == null) {
            return;
        }
        switch (outcome) {
            case CANCEL:
                // Losing the focus shows the previous shortcut again
                input.blur();
                break;
            case CLEAR:
                shortcutHandler.getShortcuts().clear(action);
                input.setValue("");
                showResult(input, null);
                break;
            case RECORD:
                input.setValue(combo.toDisplayString());
                showResult(input, shortcutHandler.getShortcuts().set(action, combo));
                break;
            default:
                // Not a valid shortcut on its own, e.g. an arrow key
                break;
        }
    }

    /// Shows whether a new shortcut was accepted, saving it if so.
    ///
    /// @param conflict The label of what already uses the keys, or null if they were accepted.
    private void showResult(final InputElement input, final String conflict) {
        if (conflict == null) {
            shortcutHandler.save();
            input.removeClassName(CLASS_ERROR);
            input.addClassName(CLASS_SUCCESS);
            input.removeAttribute("title");
        } else {
            input.removeClassName(CLASS_SUCCESS);
            input.addClassName(CLASS_ERROR);
            input.setTitle("Already used by: " + conflict);
        }
    }

    private static String displayString(final KeyCombo combo) {
        return combo != null
                ? combo.toDisplayString()
                : "";
    }

    private static InputElement getShortcutInput(final NativeEvent event) {
        final Element target = Element.as(event.getEventTarget());
        if (target != null && target.hasAttribute(ATTR_SHORTCUT)) {
            return target.cast();
        }
        return null;
    }
}
