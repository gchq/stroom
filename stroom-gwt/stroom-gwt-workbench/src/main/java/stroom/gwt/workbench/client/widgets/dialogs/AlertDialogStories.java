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

package stroom.gwt.workbench.client.widgets.dialogs;

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.alert.client.event.PromptEvent;
import stroom.alert.client.presenter.PromptPresenter;
import stroom.alert.client.view.PromptViewImpl;
import stroom.gwt.workbench.client.StoryPanels;
import stroom.gwt.workbench.client.widgets.StoryPopups;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;

import com.google.gwt.event.shared.HasHandlers;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.InlineLabel;
import com.google.gwt.user.client.ui.Widget;

/// Stories for Stroom's alert, confirmation and prompt dialogs.
///
/// A Stroom screen shows these by firing `AlertEvent`, `ConfirmEvent` or `PromptEvent`, which
/// Stroom's `AlertPlugin` (with `CommonAlertPresenter`) and `PromptPresenter` handle. The stories
/// fire the same events.
public final class AlertDialogStories {

    private static final String DETAIL = "java.lang.RuntimeException: boom\n"
                                         + "\tat com.example.Foo.bar(Foo.java:42)\n"
                                         + "\tat com.example.Baz.qux(Baz.java:17)";

    private AlertDialogStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("Widgets/Dialogs/AlertDialog", AlertDialogStories.class)
                .layout(StoryLayout.CENTERED)
                // Info / Warning / Error alerts (Close button only)
                .story("Alerts_", "Alerts (info / warn / error)", AlertDialogStories::alertsStory)
                // Confirmation dialogs — OK / Cancel, callback receives the boolean result
                .story("Confirm", AlertDialogStories::confirm)
                // Queued alerts — trigger several at once; they show sequentially
                .story("Queued", context -> {
                    final HasHandlers handlers = alertHandlers(context);
                    return StoryPanels.row(12, DialogWidgets.button("Trigger 3 alerts", DialogWidgets.PRIMARY,
                            event -> {
                                AlertEvent.fireInfo(handlers, "First alert (info).", null);
                                AlertEvent.fireWarn(handlers, "Second alert (warning).", null);
                                AlertEvent.fireError(handlers, "Third alert (error).", null);
                            }));
                })
                // Prompt dialog — text input with OK / Cancel
                .story("Prompt", AlertDialogStories::prompt);
    }

    private static Widget alertsStory(final StoryContext context) {
        final HasHandlers handlers = alertHandlers(context);
        final FlowPanel row = StoryPanels.row(12,
                DialogWidgets.button("Info", DialogWidgets.PRIMARY, event -> AlertEvent.fireInfo(handlers,
                        "The processing job completed successfully with 1,234 records processed.", null)),
                DialogWidgets.button("Warning", DialogWidgets.SECONDARY, event -> AlertEvent.fireWarn(handlers,
                        "The pipeline configuration is invalid.", null)),
                DialogWidgets.button("Error", DialogWidgets.SECONDARY, event -> AlertEvent.fireError(handlers,
                        "Failed to connect to database: connection refused on port 5432.", null)),
                DialogWidgets.button("Error + detail", DialogWidgets.SECONDARY, event -> AlertEvent.fireError(
                        handlers, "Save failed", DETAIL, null)));
        return row;
    }

    private static Widget confirm(final StoryContext context) {
        final HasHandlers handlers = alertHandlers(context);
        final InlineLabel result = DialogWidgets.result();
        final FlowPanel buttons = StoryPanels.row(12,
                DialogWidgets.button("Confirm", DialogWidgets.PRIMARY, event -> ConfirmEvent.fire(handlers,
                        "Delete these items?", ok -> showResult(result, ok))),
                DialogWidgets.button("Confirm (warn)", DialogWidgets.SECONDARY, event -> ConfirmEvent.fireWarn(
                        handlers,
                        "This will overwrite existing data. Continue?",
                        ok -> showResult(result, ok))));
        return StoryPanels.column(12, buttons, result);
    }

    /// The prompt, shared with `Widgets/Dialogs/PromptDialog`.
    ///
    /// @param context The story's context.
    /// @return The story's widget.
    static Widget prompt(final StoryContext context) {
        final StoryPopups popups = StoryPopups.create(context);
        // As GWTP's proxy would register Stroom's PromptPresenter for the event
        final PromptPresenter promptPresenter = new PromptPresenter(popups.getEventBus(), new PromptViewImpl(),
                null);
        popups.addCleanUp(popups.getEventBus().addHandler(PromptEvent.getType(), promptPresenter)::removeHandler);
        final HasHandlers handlers = DialogWidgets.handlers(popups.getEventBus());

        final InlineLabel result = DialogWidgets.result();
        // Stroom's PromptViewImpl uses the browser's own prompt
        // (Window.prompt), not a styled dialog, and so accepts blank input.
        final Widget button = DialogWidgets.button("Show prompt", DialogWidgets.PRIMARY, event -> PromptEvent.fire(
                handlers,
                "Enter a name for the new item:",
                "Untitled",
                value -> DialogWidgets.setResult(result, value == null
                        ? "Cancelled"
                        : "Got: " + value)));
        return StoryPanels.column(12, button, result);
    }

    private static HasHandlers alertHandlers(final StoryContext context) {
        return DialogWidgets.handlers(StoryPopups.create(context).withAlerts().getEventBus());
    }

    private static void showResult(final InlineLabel result, final boolean ok) {
        DialogWidgets.setResult(result, "Result: " + (ok
                ? "Confirmed"
                : "Cancelled"));
    }
}
