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

package stroom.gwt.workbench.framework.client.preview;

import stroom.gwt.workbench.framework.client.BrowserUtil;
import stroom.gwt.workbench.framework.client.WorkbenchResources;
import stroom.gwt.workbench.framework.client.args.ArgType;
import stroom.gwt.workbench.framework.client.args.Args;
import stroom.gwt.workbench.framework.client.args.ArgsCodec;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.PlayRunner;
import stroom.gwt.workbench.framework.client.play.PlayRunner.LogEntry;
import stroom.gwt.workbench.framework.client.play.PlayRunner.RunStatus;
import stroom.gwt.workbench.framework.client.play.SelfTestHooks;
import stroom.gwt.workbench.framework.client.shortcuts.KeyCombo;
import stroom.gwt.workbench.framework.client.shortcuts.KeyEvents;
import stroom.gwt.workbench.framework.client.shortcuts.Shortcuts;
import stroom.gwt.workbench.framework.client.story.Story;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryDecorator;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.gwt.workbench.framework.client.story.StoryTheme;
import stroom.gwt.workbench.framework.client.story.StoryUrls;
import stroom.gwt.workbench.framework.client.tools.VisionFilter;

import com.google.gwt.dom.client.BodyElement;
import com.google.gwt.dom.client.DivElement;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.dom.client.StyleInjector;
import com.google.gwt.json.client.JSONArray;
import com.google.gwt.json.client.JSONNumber;
import com.google.gwt.json.client.JSONObject;
import com.google.gwt.json.client.JSONString;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.ui.PopupPanel;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.Widget;

import java.util.List;

/// Renders a single story in the preview page (iframe.html), which is shown in the manager's
/// canvas or on its own when opened in isolation. It re-renders the story when its args change,
/// runs its play function and reports progress to the manager. The progress is also put on the
/// page for the test runner (see [RunnerHooks]).
///
/// Errors the story's code throws after it first renders, e.g. in timers, are reported for the
/// whole life of the page by the [PlayRunner] (which every story has, with no steps if it has no
/// play function): they fail the running step, or mark the run as ERRORED.
///
/// With a `selftest` query parameter, the page also exposes the play API's DOM helpers for the
/// self-test (see [SelfTestHooks]).
public class StoryPreview {

    // The same ids/classes as React Storybook's preview
    private static final String ROOT_ID = "workbench-root";
    private static final String SHOW_MAIN_CLASS = "sb-show-main";

    private static final String ERROR_ID = "wbp-error";
    private static final String ERROR_CLASS = "wbp-error";
    private static final String OUTLINE_CLASS = "wbp-outline";
    private static final String VISION_FILTERS_CLASS = "wbp-vision-filters";
    // The query parameter that installs window.__workbenchDom for the self-test
    private static final String SELF_TEST_PARAM = "selftest";

    private final StoryRegistry registry;
    private final StoryDecorator decorator;
    private Story story;
    private Args args;
    // The theme chosen in the workbench's toolbar (the 'theme' global)
    private StoryTheme theme = StoryTheme.DEFAULT;
    private PlayRunner playRunner;
    // The context of the current rendering, to clean up before the next
    private StoryContext context;

    /// @param registry  All the stories.
    /// @param decorator Wraps each story's widget.
    public StoryPreview(final StoryRegistry registry, final StoryDecorator decorator) {
        this.registry = registry;
        this.decorator = decorator;
    }

    /// Renders a story into the preview page.
    ///
    /// @param storyId The id of the story to render, from the `id` query parameter.
    public void render(final String storyId) {
        StyleInjector.inject(WorkbenchResources.INSTANCE.previewCss().getText(), true);
        addVisionFilters();
        forwardKeyboardShortcuts();
        final MeasureOverlay measureOverlay = new MeasureOverlay();
        BrowserUtil.addMessageHandler((type, name, detail, value) -> {
            if (BrowserUtil.OUTLINE_MESSAGE.equals(type)) {
                setBodyClass(OUTLINE_CLASS, value);
            } else if (BrowserUtil.MEASURE_MESSAGE.equals(type)) {
                measureOverlay.setEnabled(value);
            } else if (BrowserUtil.VISION_MESSAGE.equals(type)) {
                setVisionFilter(VisionFilter.fromId(name));
            } else if (BrowserUtil.ARGS_MESSAGE.equals(type)) {
                onArgsChanged(name);
            } else if (BrowserUtil.PLAY_CONTROL_MESSAGE.equals(type) && playRunner != null) {
                final PlayRunner.Control control = PlayRunner.Control.fromName(name);
                if (control != null) {
                    playRunner.control(control);
                }
            } else if (BrowserUtil.A11Y_RUN_MESSAGE.equals(type)) {
                A11yRunner.run();
            } else if (BrowserUtil.A11Y_HIGHLIGHT_MESSAGE.equals(type)) {
                A11yRunner.highlight(name);
            } else if (BrowserUtil.A11Y_JUMP_MESSAGE.equals(type) && detail != null) {
                A11yRunner.jumpTo(name, detail);
            }
        });

        if (BrowserUtil.getQueryParameter(SELF_TEST_PARAM) != null) {
            SelfTestHooks.install();
        }
        story = registry.getStory(storyId);
        if (story == null) {
            final String message = "Couldn't find story matching id '" + storyId + "'.\n\n"
                                   + "- Did you just rename a story?\n"
                                   + "- Did you add its stories to the registry?";
            showError(message);
            publishState(RunStatus.ERRORED.name(), List.of(), 0, 0, message);
            return;
        }
        publishState(RunnerJson.RENDERING_STATUS, List.of(), 0, 0, null);

        setBodyClass(SHOW_MAIN_CLASS, true);
        for (final StoryLayout layout : StoryLayout.values()) {
            setBodyClass(layout.getClassName(), layout == story.getLayout());
        }

        args = story.getInitialArgs().merge(
                typedArgs(story, ArgsCodec.decode(BrowserUtil.getQueryParameter(StoryUrls.ARGS_PARAM))));
        theme = StoryUrls.themeFromGlobals(BrowserUtil.getQueryParameter(StoryUrls.GLOBALS_PARAM));
        decorator.applyTheme(theme);
        // Stroom's widgets measure text as they render (e.g. a link tab's width), so the story is
        // only rendered once its fonts have loaded, so that it looks the same every time
        whenFontsLoaded(() -> {
            if (renderStory()) {
                startPlay();
            }
        });
    }

    /// Converts args from a URL or message into their types, ignoring unknown args.
    ///
    /// @param story The story the args are for.
    /// @param raw   The args as decoded by [ArgsCodec].
    /// @return The args with typed values.
    public static Args typedArgs(final Story story, final Args raw) {
        Args typed = Args.empty();
        for (final String name : raw.getNames()) {
            final ArgType argType = story.getArgType(name);
            if (argType != null) {
                final Object value = raw.get(name);
                // A null (from '!null' or '!undefined') unsets the arg, as in Storybook
                typed = value == null
                        ? typed.withNull(name)
                        : typed.with(name, argType.convert(value));
            }
        }
        return typed;
    }

    private void onArgsChanged(final String encodedArgs) {
        if (story == null) {
            return;
        }
        // The manager sends all the changed args, so start from the initial ones
        args = story.getInitialArgs().merge(typedArgs(story, ArgsCodec.decode(encodedArgs)));
        // As in Storybook, changing args re-renders the story without running the play function
        // again, so abandon any steps still to run against the old rendering
        if (playRunner != null) {
            playRunner.stop();
        }
        if (renderStory()) {
            A11yRunner.run();
        }
    }

    /// Renders the story with the current args, replacing any previous rendering: first the
    /// previous rendering's clean ups run (see [StoryContext#addCleanUp(Runnable)]), so that its
    /// timers, pending requests etc. are disposed before the preview removes what is left on the
    /// page, then its widgets and popups are removed. A failure in any of these is logged to the
    /// browser's console and reported to the play runner (failing the running step, or marking
    /// the run as errored), and doesn't stop the others or the new rendering.
    ///
    /// @return True if it rendered without an error.
    private boolean renderStory() {
        final RootPanel rootPanel = RootPanel.get(getOrCreateDiv(ROOT_ID, null).getId());
        if (context != null) {
            final StoryContext previous = context;
            context = null;
            try {
                final RuntimeException failure = previous.cleanUp();
                if (failure != null) {
                    reportRenderingError("Error cleaning up story '" + story.getId() + "'", failure);
                }
            } catch (final RuntimeException e) {
                reportRenderingError("Error cleaning up story '" + story.getId() + "'", e);
            }
        }
        try {
            rootPanel.clear();
        } catch (final RuntimeException e) {
            reportRenderingError("Error removing the previous rendering of story '" + story.getId() + "'", e);
        }
        try {
            removePopups();
        } catch (final RuntimeException e) {
            reportRenderingError("Error removing the popups of story '" + story.getId() + "'", e);
        }
        showError(null);
        // Highlights outline elements of the old rendering
        A11yRunner.clearHighlights();
        try {
            context = new StoryContext(story, args, theme);
            final Widget widget = decorator.decorate(story.getRenderer().render(context));
            rootPanel.add(widget);
            return true;
        } catch (final RuntimeException e) {
            final String message = "Error rendering story '" + story.getId() + "':\n\n" + e;
            showError(message);
            reportInteractions(RunStatus.ERRORED, List.of(), 0, 0, message);
            return false;
        }
    }

    /// Reports a failure replacing the previous rendering: logs it to the browser's console and,
    /// if there is a play runner, reports it as an error in the story (see
    /// [PlayRunner#reportError(String)]).
    private void reportRenderingError(final String message, final RuntimeException e) {
        final String text = message + ": " + e;
        consoleError(text);
        if (playRunner != null) {
            playRunner.reportError(text);
        }
    }

    /// Runs a task once the fonts that stories use have loaded (Stroom's Roboto, in its regular,
    /// medium and bold weights), or after a few seconds if they haven't (e.g. a font is missing),
    /// or at once if the browser can't tell.
    ///
    /// @param task What to do.
    private static native void whenFontsLoaded(Runnable task) /*-{
        var done = false;
        var finish = $entry(function () {
            if (!done) {
                done = true;
                task.@java.lang.Runnable::run()();
            }
        });
        var fonts = $doc.fonts;
        if (!fonts || !fonts.load || !$wnd.Promise) {
            finish();
            return;
        }
        $wnd.setTimeout(finish, 3000);
        $wnd.Promise.all([
            fonts.load('400 14px Roboto'),
            fonts.load('italic 400 14px Roboto'),
            fonts.load('500 14px Roboto'),
            fonts.load('700 14px Roboto')
        ]).then(finish, finish);
    }-*/;

    private static native void consoleError(String message) /*-{
        if ($wnd.console && $wnd.console.error) {
            $wnd.console.error(message);
        }
    }-*/;

    /// Removes everything the previous rendering added to the page's body as GWT widgets, e.g.
    /// dialogs, menus and Stroom's frames, so that they don't stay on screen, or match the play
    /// function's queries, after the story is rendered again. Popups are hidden first so that
    /// their glass is removed too.
    private static void removePopups() {
        final RootPanel body = RootPanel.get();
        for (int i = body.getWidgetCount() - 1; i >= 0; i--) {
            final Widget widget = body.getWidget(i);
            if (widget instanceof PopupPanel) {
                // Hiding also removes the popup's glass
                ((PopupPanel) widget).hide();
            }
            widget.removeFromParent();
        }
    }

    private void startPlay() {
        // A story without a play function gets a runner with no steps, which reports COMPLETED
        // with no interactions (so the manager shows its empty state), and still reports errors
        // the story throws later, as ERRORED
        final Play play = new Play();
        try {
            if (story.getPlay() != null) {
                story.getPlay().play(play);
            }
        } catch (final RuntimeException e) {
            final String message = "Error in play function of story '" + story.getId() + "':\n\n" + e;
            showError(message);
            reportInteractions(RunStatus.ERRORED, List.of(), 0, 0, message);
            return;
        }
        playRunner = new PlayRunner(play, getOrCreateDiv(ROOT_ID, null), this::renderStory,
                (status, entries, nextStep, stepCount) -> {
                    reportInteractions(status, entries, nextStep, stepCount, null);
                    // As in Storybook, check accessibility once the play function has finished
                    if (status == RunStatus.COMPLETED || status == RunStatus.ERRORED) {
                        A11yRunner.run();
                    }
                });
        playRunner.start();
    }

    private void reportInteractions(final RunStatus status,
                                    final List<LogEntry> entries,
                                    final int nextStep,
                                    final int stepCount,
                                    final String error) {
        publishState(status.name(), entries, nextStep, stepCount, error);
        final JSONObject json = new JSONObject();
        json.put("status", new JSONString(status.name()));
        json.put("nextStep", new JSONNumber(nextStep));
        json.put("stepCount", new JSONNumber(stepCount));
        final JSONArray array = new JSONArray();
        for (final LogEntry entry : entries) {
            final JSONObject item = new JSONObject();
            item.put("depth", new JSONNumber(entry.getDepth()));
            item.put("text", new JSONString(entry.getText() != null
                    ? entry.getText()
                    : ""));
            item.put("status", new JSONString(entry.getStatus().name()));
            if (entry.getError() != null) {
                item.put("error", new JSONString(entry.getError()));
            }
            array.set(array.size(), item);
        }
        json.put("entries", array);
        BrowserUtil.postToManager(BrowserUtil.INTERACTIONS_MESSAGE, story != null
                ? story.getId()
                : null, json.toString());
    }

    /// Exposes the state of the story to the test runner, see [RunnerHooks].
    private void publishState(final String status,
                              final List<LogEntry> entries,
                              final int nextStep,
                              final int stepCount,
                              final String error) {
        RunnerHooks.publishPlayState(status, RunnerJson.playState(
                story != null
                        ? story.getId()
                        : BrowserUtil.getQueryParameter(StoryUrls.ID_PARAM),
                status,
                story != null && story.getPlay() != null,
                entries,
                nextStep,
                stepCount,
                error));
    }

    /// Adds the SVG filters that simulate vision deficiencies to the page.
    private static void addVisionFilters() {
        final DivElement container = Document.get().createDivElement();
        container.setClassName(VISION_FILTERS_CLASS);
        container.setInnerSafeHtml(SafeHtmlUtils.fromTrustedString(
                WorkbenchResources.INSTANCE.visionFiltersSvg().getText()));
        Document.get().getBody().appendChild(container);
    }

    /// Simulates a vision deficiency by filtering the whole page, as Storybook does.
    ///
    /// @param filter The filter, or null for none.
    private static void setVisionFilter(final VisionFilter filter) {
        Document.get().getBody().getStyle().setProperty("filter", filter != null
                ? filter.getCssFilter()
                : "");
    }

    /// Sends key presses to the manager so its keyboard shortcuts work while the canvas has the
    /// focus, as in Storybook.
    private static void forwardKeyboardShortcuts() {
        Event.addNativePreviewHandler(preview -> {
            if (preview.getTypeInt() == Event.ONKEYDOWN) {
                final NativeEvent event = preview.getNativeEvent();
                final KeyCombo combo = KeyEvents.toCombo(event);
                if (combo != null) {
                    final boolean typing = KeyEvents.isTyping(event);
                    // The manager handles the shortcut asynchronously so can't stop the browser's
                    // default action, e.g. alt+left going back in history, so stop it here
                    if (!typing && isShortcut(combo)) {
                        event.preventDefault();
                    }
                    BrowserUtil.postToManager(BrowserUtil.KEYDOWN_MESSAGE, combo.toString(), typing
                            ? "typing"
                            : null);
                }
            }
        });
    }

    /// @return True if the key combination is one of the user's workbench shortcuts. They are
    /// read each time as the user may have changed them in the manager.
    private static boolean isShortcut(final KeyCombo combo) {
        final Shortcuts shortcuts = new Shortcuts();
        shortcuts.load(BrowserUtil.getLocalStorage(Shortcuts.STORAGE_KEY));
        return shortcuts.find(combo) != null;
    }

    private static Element getOrCreateDiv(final String id, final String className) {
        final Element existing = Document.get().getElementById(id);
        if (existing != null) {
            return existing;
        }
        final DivElement div = Document.get().createDivElement();
        div.setId(id);
        if (className != null) {
            div.setClassName(className);
        }
        Document.get().getBody().appendChild(div);
        return div;
    }

    private static void setBodyClass(final String className, final boolean set) {
        final BodyElement body = Document.get().getBody();
        if (set) {
            body.addClassName(className);
        } else {
            body.removeClassName(className);
        }
    }

    private static void showError(final String message) {
        getOrCreateDiv(ERROR_ID, ERROR_CLASS).setInnerText(message != null
                ? message
                : "");
    }
}
