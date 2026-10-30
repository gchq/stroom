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

package stroom.gwt.workbench.client.widgets.editorsandviewers;

import stroom.dispatch.client.RestFactory;
import stroom.editor.client.ChangeCurrentPreferencesEvent;
import stroom.editor.client.CurrentPreferences;
import stroom.entity.client.MarkdownConverter;
import stroom.entity.client.component.DocumentationTab;
import stroom.entity.client.component.DocumentationTabController;
import stroom.entity.client.component.MarkdownEditorController;
import stroom.gwt.workbench.client.FrameDocuments;
import stroom.gwt.workbench.client.StroomThemeDecorator;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.StartupFixtures;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.preferences.client.UserPreferencesManager;
import stroom.ui.config.shared.Theme;
import stroom.ui.config.shared.UserPreferences.Toggle;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NodeList;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.web.bindery.event.shared.EventBus;

/// Stories for Stroom's Markdown editor ([DocumentationTabController], as used for documentation
/// tabs).
///
/// Its toolbar (Edit Documentation and Help with Documentation), which Stroom shows in the document
/// tab's tool bar, is shown above it. It reads the help URL from the UI config, so the stories use
/// a [ScreenHarness].
public final class MarkdownEditorStories {

    // Spy names
    private static final String ON_DIRTY = "onDirty";

    private static final RestFixtures FIXTURES = RestFixtures.builder().build();

    // The default UI config without the help URL, so the help isn't configured
    private static final String NO_HELP_UI_CONFIG = StartupFixtures.DEFAULT_UI_CONFIG
            .replace("\"helpUrl\": \"https://gchq.github.io/stroom-docs/7.13/docs\",", "");

    private static final String SAMPLE_MARKDOWN = """
            # Release Notes

            A short **Markdown** document exercising the preview renderer.

            ## Highlights

            - **Bold** and *italic* and `inline code`
            - A [link to the docs](https://example.com)
            - ~~Struck-through~~ text

            ### A code block

            ```rust
            fn main() {
                println!("hello, markdown");
            }
            ```

            ### A table

            | Feature      | Status |
            | ------------ | :----: |
            | Headings     |   ✔    |
            | Tables       |   ✔    |
            | Task lists   |   ✔    |

            ### Tasks

            - [x] Port the renderer
            - [ ] Wire the backend seam

            > Blockquotes are supported too.
            """;

    private MarkdownEditorStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("Widgets/Editors & Viewers/MarkdownEditor", MarkdownEditorStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // Edit and preview split: GWT opens a non-blank document in preview mode, so the
                // story clicks the Edit Documentation button once
                .story("EditPreviewSplit", context -> {
                    final ScreenHarness harness = ScreenHarness.create(context, FIXTURES);
                    final Widget widget = markdownEditor(harness, SAMPLE_MARKDOWN, false);
                    Scheduler.get().scheduleDeferred(() -> clickEditButton(widget));
                    return widget;
                })
                // A blank, writable document opens straight in the split edit view
                .story("BlankWritable", context -> {
                    final ScreenHarness harness = ScreenHarness.create(context, FIXTURES);
                    return markdownEditor(harness, "", false);
                })
                // Read only: no toolbar, only the rendered Markdown
                .story("ReadOnlyPreview", context -> {
                    final ScreenHarness harness = ScreenHarness.create(context, FIXTURES);
                    return markdownEditor(harness, SAMPLE_MARKDOWN, true);
                })
                // The rendered document says it is in English, for screen readers (gchq/stroom#5408),
                // and its link, which opens in a new tab, says so (gchq/stroom#5406)
                .withPlay(play -> {
                    final Query frame = play.querySelector("iframe#markdown-frame");
                    play.waitFor(() -> play.expect(FrameDocuments.language(frame)).toBe("en"));
                    play.expect(FrameDocuments.newTabLinks(frame))
                            .toBe("link to the docs (opens in new tab) | noopener noreferrer");
                    play.expect(play.spy(ScreenHarness.ALERT_SPY)).not().toHaveBeenCalled();
                })
                // A change of theme renders the preview again in the new theme's colours (the
                // preview kept the old theme's until its text changed)
                .story("ThemeChange", context -> {
                    final ScreenHarness harness = ScreenHarness.create(context, FIXTURES);
                    return markdownEditor(harness, SAMPLE_MARKDOWN, true);
                })
                .withPlay(play -> {
                    final Query frame = play.querySelector("iframe#markdown-frame");
                    final boolean light = !StroomThemeDecorator.isDark();
                    play.waitFor(() -> play.expect(FrameDocuments.count(frame, LIGHT_FRAME))
                            .toBe(light ? 1 : 0));
                    play.run("change the theme", () -> changeTheme(light ? Theme.DARK : Theme.LIGHT));
                    play.waitFor(() -> play.expect(FrameDocuments.count(frame, LIGHT_FRAME))
                            .toBe(light ? 0 : 1));
                    // Back to the page's theme, for the screenshot
                    play.run("change the theme back", () -> changeTheme(light ? Theme.LIGHT : Theme.DARK));
                    play.waitFor(() -> play.expect(FrameDocuments.count(frame, LIGHT_FRAME))
                            .toBe(light ? 1 : 0));
                    play.expect(play.spy(ScreenHarness.ALERT_SPY)).not().toHaveBeenCalled();
                })
                // With no help URL configured, "Help with Documentation" shows "Help is not configured!"
                .story("HelpNotConfigured", context -> {
                    final ScreenHarness harness = ScreenHarness.builder(context, FIXTURES)
                            .uiConfig(NO_HELP_UI_CONFIG)
                            .realAlerts()
                            .build();
                    return markdownEditor(harness, SAMPLE_MARKDOWN, false);
                });
    }

    // The preview's frame document in Stroom's light theme (its <html> has the theme's classes)
    private static final String LIGHT_FRAME = "html." + Theme.LIGHT.getCssClass();

    // The preferences and bus of the editor shown, for the ThemeChange story's play, which can't
    // reach its render's locals
    private static CurrentPreferences shownPreferences;
    private static EventBus shownEventBus;

    private static Widget markdownEditor(final ScreenHarness harness,
                                         final String text,
                                         final boolean readOnly) {
        final EventBus eventBus = harness.getEventBus();
        // Workaround: Stroom sets the current preferences when the user logs in
        // (UserPreferencesManager.setCurrentPreferences, which also sets the page's theme classes),
        // which the harness doesn't do, and the Markdown converter needs their theme (for the
        // preview's frame), so the story's manager reports the workbench page's theme
        final CurrentPreferences currentPreferences = harness.getInjector().getCurrentPreferences();
        currentPreferences.setTheme(StroomThemeDecorator.getStroomThemeName());
        shownPreferences = currentPreferences;
        shownEventBus = eventBus;
        final UserPreferencesManager userPreferencesManager = new PageThemeUserPreferencesManager(
                harness.getRestFactory(), currentPreferences);
        final MarkdownConverter markdownConverter = new MarkdownConverter(userPreferencesManager);
        final DocumentationTabController documentationTab = new DocumentationTabController(
                eventBus,
                new DocumentationTab(),
                () -> new MarkdownEditorController(
                        eventBus,
                        () -> EditorWidgets.codeEditorController(eventBus),
                        markdownConverter),
                harness.getUiConfigCache(),
                markdownConverter);
        harness.own(documentationTab);

        final Spy onDirty = harness.fn(ON_DIRTY);
        harness.addRegistration(documentationTab.addDirtyListener(onDirty::call));

        // As the document tab does: read only first, then the text
        documentationTab.setReadOnly(readOnly);
        documentationTab.setText(text);

        // A 520px high flex column
        final FlowPanel frame = new FlowPanel();
        frame.getElement().getStyle().setProperty("height", "520px");
        frame.getElement().getStyle().setProperty("display", "flex");
        frame.getElement().getStyle().setProperty("flexDirection", "column");
        for (final Widget toolbar : documentationTab.getDocumentButtons()) {
            toolbar.getElement().getStyle().setProperty("flex", "0 0 auto");
            frame.add(toolbar);
        }
        final Widget content = documentationTab.asWidget();
        content.getElement().getStyle().setProperty("flex", "1 1 auto");
        content.getElement().getStyle().setProperty("minHeight", "0");
        content.getElement().getStyle().setProperty("position", "relative");
        frame.add(content);
        harness.add(frame);
        return harness.asWidget();
    }

    // The ThemeChange story's: sets the theme the converter reads and tells the app, as the
    // preferences dialog does once the user picks one
    private static void changeTheme(final Theme theme) {
        shownPreferences.setTheme(theme.getThemeName());
        // The code editors read this too, which the harness's preferences leave unset
        if (shownPreferences.getEditorLiveAutoCompletion() == null) {
            shownPreferences.setEditorLiveAutoCompletion(Toggle.OFF);
        }
        ChangeCurrentPreferencesEvent.fire(shownEventBus::fireEvent, shownPreferences);
    }

    /// Clicks the toolbar's Edit Documentation button (`editModeButton`) when the story renders.
    private static void clickEditButton(final Widget widget) {
        final NodeList<Element> buttons = widget.getElement().getElementsByTagName("button");
        for (int i = 0; i < buttons.getLength(); i++) {
            final Element button = buttons.getItem(i);
            if ("Edit Documentation".equals(button.getTitle())) {
                // A real button: a click presses it
                button.dispatchEvent(Document.get().createClickEvent(
                        1, 0, 0, 0, 0, false, false, false, false));
                return;
            }
        }
    }


    // --------------------------------------------------------------------------------


    /// Reports the theme classes of the workbench's page (`iframe.html`'s `<html>` element) as the
    /// current user's, as Stroom's would be once the user has logged in, but with the theme the
    /// preferences now hold (a story may change it, as the user may).
    private static class PageThemeUserPreferencesManager extends UserPreferencesManager {

        private final CurrentPreferences currentPreferences;

        private PageThemeUserPreferencesManager(final RestFactory restFactory,
                                                final CurrentPreferences currentPreferences) {
            super(restFactory, currentPreferences);
            this.currentPreferences = currentPreferences;
        }

        @Override
        public String getCurrentPreferenceClasses() {
            final String pageClasses = Document.get().getDocumentElement().getClassName();
            for (final Theme theme : Theme.values()) {
                if (theme.getThemeName().equals(currentPreferences.getTheme())) {
                    String classes = pageClasses;
                    for (final Theme other : Theme.values()) {
                        classes = classes.replace(other.getCssClass(), "");
                    }
                    return classes.trim() + " " + theme.getCssClass();
                }
            }
            return pageClasses;
        }
    }
}
