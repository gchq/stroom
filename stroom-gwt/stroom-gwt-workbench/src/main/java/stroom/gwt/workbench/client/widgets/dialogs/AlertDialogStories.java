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
import stroom.gwt.workbench.client.StoryPanels;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.client.widgets.StoryPopups;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.TextMatch;
import stroom.gwt.workbench.framework.client.play.Value;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.widget.text.client.InlineLabel;

import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.shared.HasHandlers;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Widget;

/// Stories for Stroom's alert and confirmation dialogs.
///
/// A Stroom screen shows these by firing `AlertEvent` or `ConfirmEvent`, which Stroom's
/// `AlertPlugin` (with `CommonAlertController`) handles. The stories fire the same events.
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
                // An alert's detail is shown and hidden by a 'Show Detail' button (it was a link
                // that added a browser history entry), which says whether the detail is shown
                // (aria-expanded) and which area it shows (aria-controls). A stack trace starts
                // hidden
                .story("ShowDetail", AlertDialogStories::alertsStory)
                .withPlay(play -> {
                    final Play screen = play.screen();
                    final Value<Integer> historyBefore = play.capture("history length",
                            AlertDialogStories::historyLength);
                    play.click(play.getByRole("button", "Error + detail"));
                    final Play dialog = screen.within(screen.findByText("Save failed").closest(StroomDom.DIALOG));
                    final Query showDetail = dialog.getByRole("button", "Show Detail");
                    final Query detail = dialog.getByText(TextMatch.containing("RuntimeException: boom"));
                    play.expect(showDetail).toHaveAttribute("aria-expanded", "false");
                    play.expect(detail).not().toBeVisible();
                    play.expect("the area it controls holds the detail", () -> {
                        final Element controlled = Document.get().getElementById(
                                showDetail.element().get().getAttribute("aria-controls"));
                        return controlled != null && controlled.isOrHasChild(detail.element().get());
                    }).toBe(true);
                    // From the keyboard: Enter shows the detail, Space hides it; the name stays
                    play.tab(true);
                    play.expect(showDetail).toHaveFocus();
                    play.keyboard("{Enter}");
                    play.waitFor(() -> play.expect(showDetail).toHaveAttribute("aria-expanded", "true"));
                    play.expect(detail).toBeVisible();
                    play.expect(dialog.getByRole("button", "Show Detail")).toHaveFocus();
                    play.keyboard(" ");
                    play.waitFor(() -> play.expect(showDetail).toHaveAttribute("aria-expanded", "false"));
                    play.expect(detail).not().toBeVisible();
                    // A click toggles it too, and none of it adds a browser history entry
                    play.click(showDetail);
                    play.waitFor(() -> play.expect(detail).toBeVisible());
                    play.expect("the history length", () -> historyLength() - historyBefore.get()).toBe(0);
                    play.click(dialog.getByRole("button", StroomDom.button("Close")));
                })
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
                });
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

    private static native int historyLength() /*-{
        return $wnd.history.length;
    }-*/;

    private static HasHandlers alertHandlers(final StoryContext context) {
        return DialogWidgets.handlers(StoryPopups.create(context).withAlerts().getEventBus());
    }

    private static void showResult(final InlineLabel result, final boolean ok) {
        DialogWidgets.setResult(result, "Result: " + (ok
                ? "Confirmed"
                : "Cancelled"));
    }
}
