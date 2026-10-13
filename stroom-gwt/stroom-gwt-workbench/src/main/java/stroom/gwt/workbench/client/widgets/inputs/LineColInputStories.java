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

package stroom.gwt.workbench.client.widgets.inputs;

import stroom.gwt.workbench.client.StoryPanels;
import stroom.gwt.workbench.client.widgets.StoryArgs;
import stroom.gwt.workbench.framework.client.args.ArgType;
import stroom.gwt.workbench.framework.client.args.Args;
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.util.shared.Location;
import stroom.widget.linecolinput.client.LineColInput;

import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;

import java.util.Optional;
import java.util.function.Consumer;

/// Stories for [LineColInput].
public final class LineColInputStories {

    // Arg names
    private static final String LINE = "line";
    private static final String COL = "col";
    private static final String ENABLED = "enabled";
    private static final String ON_CHANGE = InputWidgets.ON_CHANGE;

    private LineColInputStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("Widgets/Inputs/LineColInput", LineColInputStories.class)
                .layout(StoryLayout.CENTERED)
                // LineColInput has no placeholder
                .argType(ArgType.number(LINE).description("The line number (GWT setValue(line, col))."))
                .argType(ArgType.number(COL).description("The column number (GWT setValue(line, col))."))
                .argType(ArgType.bool(ENABLED).description("GWT setEnabled(...).").defaultSummary("true"))
                .argType(ArgType.action(ON_CHANGE)
                        .description("Called with the line and column when the value is committed."))
                // Basic line:col input, reporting the committed value
                .story("Basic", context -> {
                    final LineColInput input = lineColInput(context, 42, 10);
                    final Label result = new Label("—");
                    result.getElement().getStyle().setProperty("marginTop", "8px");
                    result.getElement().getStyle().setProperty("fontSize", "12px");
                    onCommit(input, location -> result.setText(location == null
                            ? "L:None C:None"
                            : "L:" + location.getLineNo() + " C:" + location.getColNo()));
                    return StoryPanels.column(8, input, result);
                })
                // Disabled line:col input
                .story("Disabled", LineColInputStories::fromArgs)
                .withArgs(Args.of(LINE, 1, COL, 1, ENABLED, false));
    }

    /// An input made entirely from the story's args, so the Controls addon changes it.
    private static Widget fromArgs(final StoryContext context) {
        final Args args = context.getArgs();
        final Long line = StoryArgs.getLong(args, LINE);
        final Long col = StoryArgs.getLong(args, COL);
        final LineColInput input = lineColInput(context,
                line == null
                        ? null
                        : line.intValue(),
                col == null
                        ? null
                        : col.intValue());
        input.setEnabled(StoryArgs.getBoolean(args, ENABLED, true));
        return input;
    }

    private static LineColInput lineColInput(final StoryContext context, final Integer line, final Integer col) {
        final LineColInput input = new LineColInput();
        input.setValue(line, col);
        final Spy onChange = context.fn(ON_CHANGE);
        onCommit(input, location -> {
            if (location == null) {
                onChange.call(null, null);
            } else {
                onChange.call(location.getLineNo(), location.getColNo());
            }
        });
        return input;
    }

    /// Calls the consumer with the input's location (null when it is empty) when the value is
    /// committed, i.e. on Enter or blur.
    private static void onCommit(final LineColInput input, final Consumer<Location> consumer) {
        // LineColInput has no change callback (Stroom reads getLocation() when it needs the
        // value), and getLocation() throws for text that isn't a valid location, so invalid text
        // reports nothing here. GWT highlights invalid text as it is typed (on key up).
        final TextBox textBox = input.getTextBox();
        final Runnable commit = () -> {
            try {
                final Optional<Location> location = input.getLocation();
                consumer.accept(location.orElse(null));
            } catch (final RuntimeException e) {
                // Not a valid location, which LineColInput highlights
            }
        };
        textBox.addBlurHandler(event -> commit.run());
        textBox.addKeyDownHandler(event -> {
            if (event.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
                commit.run();
            }
        });
    }
}
