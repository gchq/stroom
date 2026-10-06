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

import stroom.data.client.presenter.TextPresenter;
import stroom.data.client.presenter.TextUiHandlers;
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.util.shared.DefaultLocation;
import stroom.util.shared.TextRange;

import com.google.gwt.user.client.ui.Widget;
import com.google.web.bindery.event.shared.SimpleEventBus;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorMode;

import java.util.Collections;

/// Stories for Stroom's data [TextPresenter] (`stroom.data.client`, the editor that shows stream
/// data, with its stepping button), matching `Widgets/Editors & Viewers/TextPresenter` in the
/// React Storybook.
public final class TextPresenterStories {

    // Spy names, the same as the React TextPresenter's props
    private static final String ON_CHANGE = AceEditorStories.ON_CHANGE;
    private static final String ON_CLEAR = "onClear";
    private static final String ON_BEGIN_STEPPING = "onBeginStepping";

    private static final String WIDTH = "720px";
    private static final String HEIGHT = "380px";

    private TextPresenterStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("Widgets/Editors & Viewers/TextPresenter", TextPresenterStories.class)
                .layout(StoryLayout.CENTERED)
                // Read-only XML with line numbers and a highlighted range (the <EventSource> element)
                .story("XmlReadOnlyHighlighted", context -> {
                    final TextPresenter presenter = textPresenter(context, AceEditorStories.SAMPLE_XML);
                    presenter.setMode(AceEditorMode.XML);
                    showLineNumbers(presenter);
                    presenter.setHighlights(Collections.singletonList(TextRange.of(
                            DefaultLocation.of(5, 5),
                            DefaultLocation.of(8, 19))));
                    return framed(presenter);
                })
                // Editable, two-way bound via onChange
                .story("Editable", context -> {
                    final TextPresenter presenter = textPresenter(context, AceEditorStories.SAMPLE_XML);
                    presenter.setMode(AceEditorMode.XML);
                    presenter.setReadOnly(false);
                    showLineNumbers(presenter);
                    return framed(presenter);
                })
                // A properties-style log with the stepping button showing
                .story("WithSteppingControls", context -> {
                    final TextPresenter presenter = textPresenter(context, AceEditorStories.SAMPLE_TEXT);
                    presenter.setMode(AceEditorMode.PROPERTIES);
                    showLineNumbers(presenter);
                    presenter.setControlsVisible(true);
                    return framed(presenter);
                })
                // Error state: the editor is replaced by the error panel
                .story("ErrorState", context -> {
                    final TextPresenter presenter = textPresenter(context, "");
                    presenter.setErrorText("Unable to display source [1234:1:1]",
                            "The requested data could not be read.\nStream may have been deleted.");
                    return framed(presenter);
                });
    }

    private static TextPresenter textPresenter(final StoryContext context, final String text) {
        final TextPresenter presenter = EditorWidgets.textPresenter(new SimpleEventBus());
        presenter.setText(text);
        final Spy onChange = context.fn(ON_CHANGE);
        // The editor's change events have no value (Editor fires them with null), so read the text
        presenter.addValueChangeHandler(event -> onChange.call(presenter.getText()));
        // React's uiHandlers prop.
        // Differs from React: beginStepping is recorded by a spy rather than calling window.alert
        presenter.setUiHandlers(new SpyTextUiHandlers(context.fn(ON_CLEAR), context.fn(ON_BEGIN_STEPPING)));
        return presenter;
    }

    /// React's `showLineNumbers`. The data TextPresenter hides line numbers (and makes the option
    /// unavailable) as Stroom formats the data it shows.
    private static void showLineNumbers(final TextPresenter presenter) {
        presenter.getLineNumbersOption().setAvailable(true);
        presenter.getLineNumbersOption().setOn(true);
    }

    private static Widget framed(final TextPresenter presenter) {
        return EditorWidgets.frame(presenter.getWidget(), WIDTH, HEIGHT);
    }


    // --------------------------------------------------------------------------------


    /// Records the presenter's UI handler calls (React's `uiHandlers` prop) with spies.
    private static class SpyTextUiHandlers implements TextUiHandlers {

        private final Spy onClear;
        private final Spy onBeginStepping;

        private SpyTextUiHandlers(final Spy onClear, final Spy onBeginStepping) {
            this.onClear = onClear;
            this.onBeginStepping = onBeginStepping;
        }

        @Override
        public void clear() {
            onClear.call();
        }

        @Override
        public void beginStepping() {
            onBeginStepping.call();
        }
    }
}
