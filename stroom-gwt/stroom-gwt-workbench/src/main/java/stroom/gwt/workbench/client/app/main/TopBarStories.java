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

package stroom.gwt.workbench.client.app.main;

import stroom.gwt.workbench.client.a11y.FocusRingClipping;
import stroom.gwt.workbench.client.app.screen.ContentStorySupport;
import stroom.gwt.workbench.client.widgets.tree.TreeFixtures;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.util.shared.NullSafe;

import com.google.gwt.dom.client.Element;

import java.util.ArrayList;
import java.util.List;

/// Stories of `App/Main/TopBar`: the controls along the top of Stroom's app shell (put together by
/// [ShellScreen], as in `App/Main/AppShell`) and their keyboard focus rings:
///
/// * Main Menu, beside the Stroom logo at the top of the explorer (`Explorer`).
/// * Hide Sidebar, to the left of the document tabs (`CurveTabLayout`).
/// * Ask Stroom AI and Show Main Menu, the application's toolbar at the top right (`MainToolbar`).
///
/// A focus ring shows only with `:focus-visible`, which a script can't put an element in. So, as
/// Storybook's pseudo-states addon does (and `Widgets/Layout/SplitPaneStates`), `FocusRings` copies
/// Stroom's own focus rules for these controls with each `:focus-visible` and `:focus` turned into a
/// class (`.pseudo-focus-visible`, `.pseudo-focus`) and gives every control those classes, so that
/// all four rings show at once and can be measured. `Keyboard` is the shell as it is: the test
/// runner's real input check (`test-runner/lib/real-input/top-bar.mjs`) tabs to each control with
/// the real keyboard and checks it draws the same ring.
public final class TopBarStories {

    // The id of the style element holding the copied rules
    private static final String PSEUDO_STYLE_ID = "top-bar-pseudo-states";
    private static final String MAIN_MENU = "Main Menu";
    private static final String HIDE_SIDEBAR = "Hide Sidebar";
    private static final String ASK_AI = "Ask Stroom AI";
    private static final String SHOW_MENU = "Show Main Menu";

    private TopBarStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Main/TopBar", TopBarStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // Every top bar control showing its keyboard focus ring, which must be drawn in
                // full: nothing around a control may cut it off
                .story("FocusRings", context -> {
                    addPseudoStateRules(PSEUDO_STYLE_ID);
                    context.addCleanUp(() -> removeStyle(PSEUDO_STYLE_ID));
                    return AppShellStories.render(context, TreeFixtures.fixtureTree());
                })
                .withPlay(play -> {
                    final List<Query> controls = controls(play);
                    play.run("show every control's focus ring", () -> {
                        for (final Query control : controls) {
                            showFocus(control.element().get());
                        }
                    });
                    for (final Query control : controls) {
                        play.expect(control.describe() + " focus ring",
                                        control.computedStyle("box-shadow"))
                                .not().toBe("none");
                    }
                    // All four at once, so that one failure shows every ring that is cut off
                    play.expect("the top bar's focus rings cut off", () -> cuts(controls))
                            .toEqual(List.of());
                    ContentStorySupport.expectNoProblems(play);
                })
                // The shell as it is, tabbed through with the real keyboard by the real input check
                .story("Keyboard", context -> AppShellStories.render(context, TreeFixtures.fixtureTree()))
                .withPlay(play -> {
                    controls(play);
                    ContentStorySupport.expectNoProblems(play);
                });
    }

    // Waits for the shell, then finds the top bar's controls, from left to right
    private static List<Query> controls(final Play play) {
        play.findByTitle(MAIN_MENU);
        // The sidebar button's name is its aria-label; the others' their title
        return List.of(
                play.getByTitle(MAIN_MENU),
                play.getByRole("button", HIDE_SIDEBAR),
                play.getByTitle(ASK_AI),
                play.getByTitle(SHOW_MENU));
    }

    // Where each control's ring is cut off, each cut named by its control, e.g.
    // 'Main Menu: top 4px by .explorer'
    private static List<String> cuts(final List<Query> controls) {
        final List<String> cuts = new ArrayList<>();
        for (final Query control : controls) {
            final Element element = control.element().get();
            final String name = NullSafe.isNonBlankString(element.getAttribute("aria-label"))
                    ? element.getAttribute("aria-label")
                    : element.getTitle();
            for (final String cut : FocusRingClipping.cuts(control).get()) {
                cuts.add(name + ": " + cut);
            }
        }
        return cuts;
    }

    // --------------------------------------------------------------------------------
    // Pseudo-states

    private static void showFocus(final Element control) {
        control.addClassName("pseudo-focus-visible");
        control.addClassName("pseudo-focus");
    }

    // Copies the rules of Stroom's style sheets that draw these controls' keyboard focus (those of
    // toolbar buttons and of .focus-without-border buttons that use :focus-visible), each :focus-visible
    // and :focus turned into a class (.pseudo-focus-visible), into a style element of their own with
    // the id given, keeping any @media rule they are in (e.g. forced colours) and following @imports
    private static native void addPseudoStateRules(String id) /*-{
        var existing = $doc.getElementById(id);
        if (existing) {
            existing.remove();
        }
        var pseudo = /:(focus-visible|focus)(?![\w-])/g;
        var copy = function (rules) {
            var text = '';
            for (var i = 0; i < rules.length; i++) {
                var rule = rules[i];
                if (rule.selectorText !== undefined) {
                    var selector = rule.selectorText;
                    if ((selector.indexOf('toolbar-button') >= 0 || selector.indexOf('focus-without-border') >= 0)
                        && selector.indexOf(':focus-visible') >= 0) {
                        text += selector.replace(pseudo, '.pseudo-$1') + ' {' + rule.style.cssText + '}\n';
                    }
                } else if (rule.styleSheet) {
                    // An @import
                    text += copy(rule.styleSheet.cssRules);
                } else if (rule.media && rule.cssRules) {
                    var inner = copy(rule.cssRules);
                    if (inner) {
                        text += '@media ' + rule.media.mediaText + ' {\n' + inner + '}\n';
                    }
                }
            }
            return text;
        };
        var css = '';
        for (var s = 0; s < $doc.styleSheets.length; s++) {
            try {
                css += copy($doc.styleSheets[s].cssRules);
            } catch (e) {
                // A style sheet from another origin can't be read
            }
        }
        var style = $doc.createElement('style');
        style.id = id;
        style.textContent = css;
        $doc.head.appendChild(style);
    }-*/;

    private static native void removeStyle(String id) /*-{
        var style = $doc.getElementById(id);
        if (style) {
            style.remove();
        }
    }-*/;
}
