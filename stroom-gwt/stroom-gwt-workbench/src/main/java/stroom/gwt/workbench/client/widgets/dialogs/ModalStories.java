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

import stroom.gwt.workbench.client.StoryPanels;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.client.widgets.ContentPresenter;
import stroom.gwt.workbench.client.widgets.StoryPopups;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.svg.shared.SvgImage;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.RenamePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.InlineLabel;
import com.google.gwt.user.client.ui.Widget;

/// Stories for Stroom's modal OK/Cancel dialog, matching `Widgets/Dialogs/Modal` in the React
/// Storybook.
///
/// The React `Modal` is the port of a presenter shown with `ShowPopupEvent` as a modal
/// `OK_CANCEL_DIALOG`, with `onHideRequest` telling OK from Cancel, as these stories do.
public final class ModalStories {

    // The React Modal's callback props
    private static final String ON_OK = "onOk";
    private static final String ON_CANCEL = "onCancel";
    private static final String FAIL = "Fail the request";

    private ModalStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("Widgets/Dialogs/Modal", ModalStories.class)
                .layout(StoryLayout.CENTERED)
                // Basic modal with OK / Cancel, auto-focus on Cancel, Escape to close
                .story("Basic", context -> {
                    final StoryPopups popups = StoryPopups.create(context);
                    final Spy[] spies = spies(context);
                    final InlineLabel result = DialogWidgets.result();
                    final Widget button = DialogWidgets.button("Open modal", DialogWidgets.PRIMARY, event ->
                            show(spies, popups, "Confirm action", SvgImage.QUESTION,
                                    "Are you sure you want to proceed? This action cannot be undone.",
                                    ok -> DialogWidgets.setResult(result, "Result: " + (ok
                                            ? "OK"
                                            : "Cancelled"))));
                    return StoryPanels.column(12, button, result);
                })
                // Custom OK label and a title override (GWT dynamic caption)
                .story("CustomLabels", context -> {
                    final StoryPopups popups = StoryPopups.create(context);
                    final Spy[] spies = spies(context);
                    final FlowPanel panel = new FlowPanel();
                    panel.add(DialogWidgets.button("Open editor", DialogWidgets.PRIMARY, event -> {
                        // Differs from React: Stroom's OK_CANCEL_DIALOG has no label option, so the
                        // OK button says OK, not Save.
                        final ContentPresenter presenter = show(spies, popups, "Editor", null,
                                "A modal with a \"Save\" OK button and a dynamic title override.",
                                ok -> {
                                    // Nothing to show
                                });
                        // GWT's dynamic caption: a screen renames its dialog once it is shown
                        RenamePopupEvent.builder(presenter).caption("Edit XPath Filter").fire();
                    }));
                    return panel;
                })
                // OK's request is still running: OK and Cancel are disabled, so Ctrl+Enter and
                // Escape do nothing (they once ran the action again). When the request fails the
                // buttons come back and focus returns to OK.
                .story("PendingRequest", context -> {
                    final StoryPopups popups = StoryPopups.create(context);
                    final Spy[] spies = spies(context);
                    return DialogWidgets.button("Open modal", DialogWidgets.PRIMARY, event ->
                            showPending(spies, popups));
                })
                .withPlay(play -> {
                    play.click(play.getByRole("button", StroomDom.button("Open modal")));
                    final Query ok = play.screen().getByRole("button", StroomDom.button("OK"));
                    play.click(ok);
                    play.expect(play.spy(ON_OK)).toHaveBeenCalledTimes(1);
                    play.expect(ok).toBeDisabled();
                    play.keyboard("{Control>}{Enter}{/Control}");
                    play.keyboard("{Escape}");
                    play.expect(play.spy(ON_OK)).toHaveBeenCalledTimes(1);
                    play.expect(play.spy(ON_CANCEL)).not().toHaveBeenCalled();
                    // The request fails (without taking focus, as a reply wouldn't)
                    play.fireEvent().click(play.screen().getByRole("button", StroomDom.button(FAIL)));
                    play.expect(ok).toBeEnabled();
                    play.expect(ok).toHaveFocus();
                });
    }

    /// The React Modal's `onOk` and `onCancel`, registered as the story renders.
    private static Spy[] spies(final StoryContext context) {
        return new Spy[]{context.fn(ON_OK), context.fn(ON_CANCEL)};
    }

    // A dialog whose OK starts a request that stays pending until the content's button fails it.
    private static void showPending(final Spy[] spies, final StoryPopups popups) {
        final Spy onOk = spies[0];
        final Spy onCancel = spies[1];
        final HidePopupRequestEvent[] pending = new HidePopupRequestEvent[1];
        final FlowPanel content = new FlowPanel();
        content.add(new InlineLabel("Saving takes a while."));
        content.add(DialogWidgets.button(FAIL, DialogWidgets.PRIMARY, event -> {
            if (pending[0] != null) {
                pending[0].reset();
                pending[0] = null;
            }
        }));
        ShowPopupEvent.builder(new ContentPresenter(popups.getEventBus(), content))
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .caption("Save")
                .modal()
                .onHideRequest(event -> {
                    if (event.isOk()) {
                        onOk.call();
                        pending[0] = event;
                    } else {
                        onCancel.call();
                        event.hide();
                    }
                })
                .fire();
    }

    private static ContentPresenter show(final Spy[] spies,
                                         final StoryPopups popups,
                                         final String caption,
                                         final SvgImage icon,
                                         final String text,
                                         final ResultHandler onResult) {
        final Spy onOk = spies[0];
        final Spy onCancel = spies[1];
        final ContentPresenter presenter = ContentPresenter.html(popups.getEventBus(), "<p>" + text + "</p>");
        ShowPopupEvent.builder(presenter)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .icon(icon)
                .caption(caption)
                .modal()
                .onHideRequest(event -> {
                    if (event.isOk()) {
                        onOk.call();
                    } else {
                        onCancel.call();
                    }
                    onResult.onResult(event.isOk());
                    event.hide();
                })
                .fire();
        return presenter;
    }


    // --------------------------------------------------------------------------------


    private interface ResultHandler {

        void onResult(boolean ok);
    }
}
