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
import stroom.editor.client.presenter.CurrentPreferences;
import stroom.entity.client.presenter.MarkdownConverter;
import stroom.entity.client.presenter.MarkdownEditPresenter;
import stroom.entity.client.presenter.MarkdownPreviewPresenter;
import stroom.entity.client.view.MarkdownEditViewImpl;
import stroom.entity.client.view.MarkdownPreviewViewImpl;
import stroom.gwt.workbench.client.FrameDocuments;
import stroom.gwt.workbench.client.StroomThemeDecorator;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.StartupFixtures;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.iframe.client.presenter.IFramePresenter;
import stroom.iframe.client.view.IFrameViewImpl;
import stroom.preferences.client.UserPreferencesManager;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.dom.client.NodeList;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.web.bindery.event.shared.EventBus;

/// Stories for Stroom's Markdown editor ([MarkdownEditPresenter], as used for documentation
/// tabs).
///
/// The presenter's toolbar (Edit and Documentation help), which Stroom shows in the document
/// tab's tool bar, is shown above it. The presenter reads the help URL from the UI config, so
/// the stories use a [ScreenHarness].
public final class MarkdownEditorStories {

    // Spy names
    private static final String ON_CHANGE = AceEditorStories.ON_CHANGE;
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
            - A [link to the docs](https://example.com) (opens in a new tab)
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
                // story clicks the Edit button once
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
                // The rendered document says it is in English, for screen readers (gchq/stroom#5408)
                .withPlay(play -> {
                    final Query frame = play.querySelector("iframe#markdown-frame");
                    play.waitFor(() -> play.expect(FrameDocuments.language(frame)).toBe("en"));
                    play.expect(play.spy(ScreenHarness.ALERT_SPY)).not().toHaveBeenCalled();
                })
                // With no help URL configured, "Documentation help" shows "Help is not configured!"
                .story("HelpNotConfigured", context -> {
                    final ScreenHarness harness = ScreenHarness.builder(context, FIXTURES)
                            .uiConfig(NO_HELP_UI_CONFIG)
                            .realAlerts()
                            .build();
                    return markdownEditor(harness, SAMPLE_MARKDOWN, false);
                });
    }

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
        final UserPreferencesManager userPreferencesManager = new PageThemeUserPreferencesManager(
                harness.getRestFactory(), currentPreferences);
        final MarkdownConverter markdownConverter = new MarkdownConverter(userPreferencesManager);
        final MarkdownPreviewPresenter previewPresenter = new MarkdownPreviewPresenter(
                eventBus,
                new MarkdownPreviewViewImpl(GWT.create(MarkdownPreviewViewImpl.Binder.class)),
                EditorWidgets.editorPresenter(eventBus),
                new IFramePresenter(eventBus, new IFrameViewImpl()),
                harness.getUiConfigCache(),
                userPreferencesManager,
                markdownConverter);
        final MarkdownEditPresenter presenter = new MarkdownEditPresenter(
                eventBus,
                new MarkdownEditViewImpl(GWT.create(MarkdownEditViewImpl.Binder.class)),
                previewPresenter,
                EditorWidgets.editorPresenter(eventBus),
                new IFramePresenter(eventBus, new IFrameViewImpl()),
                harness.getUiConfigCache(),
                userPreferencesManager,
                markdownConverter);
        // As GWTP does when the presenter is revealed in its document tab
        presenter.bind();
        harness.unbindOnCleanUp(presenter);

        final Spy onChange = harness.fn(ON_CHANGE);
        harness.addRegistration(previewPresenter.addValueChangeHandler(event ->
                onChange.call(previewPresenter.getText())));
        final Spy onDirty = harness.fn(ON_DIRTY);
        harness.addRegistration(presenter.addDirtyHandler(event -> onDirty.call(event.isDirty())));

        // As the document tab does: read only first, then the text
        presenter.setReadOnly(readOnly);
        presenter.setText(text);

        // A 520px high flex column
        final FlowPanel frame = new FlowPanel();
        frame.getElement().getStyle().setProperty("height", "520px");
        frame.getElement().getStyle().setProperty("display", "flex");
        frame.getElement().getStyle().setProperty("flexDirection", "column");
        for (final Widget toolbar : presenter.getToolbars()) {
            toolbar.getElement().getStyle().setProperty("flex", "0 0 auto");
            frame.add(toolbar);
        }
        final Widget content = presenter.getWidget();
        content.getElement().getStyle().setProperty("flex", "1 1 auto");
        content.getElement().getStyle().setProperty("minHeight", "0");
        content.getElement().getStyle().setProperty("position", "relative");
        frame.add(content);
        harness.add(frame);
        return harness.asWidget();
    }

    /// Clicks the toolbar's Edit button (`editModeButton`) when the story renders.
    private static void clickEditButton(final Widget widget) {
        final NodeList<Element> buttons = widget.getElement().getElementsByTagName("button");
        for (int i = 0; i < buttons.getLength(); i++) {
            final Element button = buttons.getItem(i);
            if ("Edit".equals(button.getTitle())) {
                // InlineSvgButton acts on a primary mouse down and up, not a click event
                button.dispatchEvent(Document.get().createMouseDownEvent(
                        0, 0, 0, 0, 0, false, false, false, false, NativeEvent.BUTTON_LEFT));
                button.dispatchEvent(Document.get().createMouseUpEvent(
                        0, 0, 0, 0, 0, false, false, false, false, NativeEvent.BUTTON_LEFT));
                return;
            }
        }
    }


    // --------------------------------------------------------------------------------


    /// Reports the theme classes of the workbench's page (`iframe.html`'s `<html>` element) as the
    /// current user's, as Stroom's would be once the user has logged in.
    private static class PageThemeUserPreferencesManager extends UserPreferencesManager {

        private PageThemeUserPreferencesManager(final RestFactory restFactory,
                                                final CurrentPreferences currentPreferences) {
            super(restFactory, currentPreferences);
        }

        @Override
        public String getCurrentPreferenceClasses() {
            return Document.get().getDocumentElement().getClassName();
        }
    }
}
