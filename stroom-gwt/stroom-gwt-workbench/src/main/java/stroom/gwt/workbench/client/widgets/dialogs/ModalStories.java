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
import stroom.gwt.workbench.client.widgets.StoryContent;
import stroom.gwt.workbench.client.widgets.StoryPopups;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.svg.shared.SvgImage;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.popup.client.DialogAction;
import stroom.widget.popup.client.PopupType;
import stroom.widget.popup.client.event.DisablePopupEvent;
import stroom.widget.popup.client.event.EnablePopupEvent;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.RenamePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.text.client.InlineLabel;

import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Widget;

/// Stories for Stroom's modal OK/Cancel dialog.
///
/// Each story shows a controller with `ShowPopupEvent` as a modal `OK_CANCEL_DIALOG`, with
/// `onHideRequest` telling OK from Cancel.
public final class ModalStories {

    // Spy names
    private static final String ON_OK = "onOk";
    private static final String ON_CANCEL = "onCancel";
    private static final String FAIL = "Fail the request";
    private static final String DISABLE_OK = "Disable OK";
    private static final String ENABLE_OK = "Enable OK";

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
                        // Stroom's OK_CANCEL_DIALOG has no label option, so the OK button says OK, not the
                        // "Save" the text describes.
                        final StoryContent controller = show(spies, popups, "Editor", null,
                                "A modal with a \"Save\" OK button and a dynamic title override.",
                                ok -> {
                                    // Nothing to show
                                });
                        // GWT's dynamic caption: a screen renames its dialog once it is shown
                        RenamePopupEvent.builder(controller).caption("Edit XPath Filter").fire();
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
                })
                // The content disables OK (DisablePopupEvent, as a form that isn't valid yet does).
                // Every hide request disables both buttons and enables them again when it is reset;
                // the reset must not enable an OK the content disabled (it once did). Here Cancel's
                // request is vetoed (reset): OK stays disabled, Ctrl+Enter doesn't run it, and the
                // focus goes back to Cancel rather than to the page's body. EnablePopupEvent then
                // enables OK.
                .story("OkDisabledSurvivesReset", context -> {
                    final StoryPopups popups = StoryPopups.create(context);
                    final Spy[] spies = spies(context);
                    return DialogWidgets.button("Open modal", DialogWidgets.PRIMARY, event ->
                            showOkGated(spies, popups, false));
                })
                .withPlay(play -> {
                    play.click(play.getByRole("button", StroomDom.button("Open modal")));
                    final Query ok = play.screen().getByRole("button", StroomDom.button("OK"));
                    final Query cancel = play.screen().getByRole("button", StroomDom.button("Cancel"));
                    play.expect(ok).toBeEnabled();
                    play.click(play.screen().getByRole("button", StroomDom.button(DISABLE_OK)));
                    play.expect(ok).toBeDisabled();
                    // Cancel's hide request is vetoed
                    play.click(cancel);
                    play.expect(play.spy(ON_CANCEL)).toHaveBeenCalledTimes(1);
                    play.expect(cancel).toBeEnabled();
                    play.expect(ok).toBeDisabled();
                    // The focus stays in the dialog, on the button that was pressed
                    play.expect(play.screen().querySelector(StroomDom.DIALOG + " :focus")).not().toBeNull();
                    play.expect(cancel).toHaveFocus();
                    play.keyboard("{Control>}{Enter}{/Control}");
                    play.expect(play.spy(ON_OK)).not().toHaveBeenCalled();
                    play.expect(ok).toBeDisabled();
                    play.click(play.screen().getByRole("button", StroomDom.button(ENABLE_OK)));
                    play.expect(ok).toBeEnabled();
                    play.keyboard("{Control>}{Enter}{/Control}");
                    play.expect(play.spy(ON_OK)).toHaveBeenCalledTimes(1);
                })
                // OK's own request disables OK (e.g. the server found the form wasn't valid after
                // all) and is reset: OK stays disabled, so Ctrl+Enter doesn't run it again
                .story("OkDisabledByItsRequest", context -> {
                    final StoryPopups popups = StoryPopups.create(context);
                    final Spy[] spies = spies(context);
                    return DialogWidgets.button("Open modal", DialogWidgets.PRIMARY, event ->
                            showOkGated(spies, popups, true));
                })
                .withPlay(play -> {
                    play.click(play.getByRole("button", StroomDom.button("Open modal")));
                    final Query ok = play.screen().getByRole("button", StroomDom.button("OK"));
                    final Query cancel = play.screen().getByRole("button", StroomDom.button("Cancel"));
                    play.click(ok);
                    play.expect(play.spy(ON_OK)).toHaveBeenCalledTimes(1);
                    play.expect(ok).toBeDisabled();
                    play.expect(cancel).toBeEnabled();
                    // The focus stays in the dialog: OK stays disabled, so it moves on to the dialog's
                    // next tab stop, Cancel (it was left on the page's body, outside the dialog)
                    play.waitFor(() -> play.expect(cancel).toHaveFocus());
                    play.keyboard("{Control>}{Enter}{/Control}");
                    play.expect(play.spy(ON_OK)).toHaveBeenCalledTimes(1);
                    play.expect(ok).toBeDisabled();
                });
    }

    /// The `onOk` and `onCancel` spies, registered as the story renders.
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
        ControllerDialog.showOnce(popups, new StoryContent(popups.getEventBus(), content))
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

    // A dialog whose content disables and enables OK with its own buttons, as a form does while it
    // isn't valid. Cancel's hide request is always vetoed (reset). If okDisablesOk, OK's request
    // disables OK and is reset; otherwise OK closes the dialog.
    private static void showOkGated(final Spy[] spies, final StoryPopups popups, final boolean okDisablesOk) {
        final Spy onOk = spies[0];
        final Spy onCancel = spies[1];
        final FlowPanel content = new FlowPanel();
        final StoryContent controller = new StoryContent(popups.getEventBus(), content);
        content.add(new InlineLabel("OK is allowed only while the form is valid."));
        content.add(DialogWidgets.button(DISABLE_OK, DialogWidgets.SECONDARY, event ->
                DisablePopupEvent.builder(controller).action(DialogAction.OK).fire()));
        content.add(DialogWidgets.button(ENABLE_OK, DialogWidgets.SECONDARY, event ->
                EnablePopupEvent.builder(controller).action(DialogAction.OK).fire()));
        ControllerDialog.showOnce(popups, controller)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .caption("Edit")
                .modal()
                .onHideRequest(event -> {
                    if (event.isOk()) {
                        onOk.call();
                        if (okDisablesOk) {
                            DisablePopupEvent.builder(controller).action(DialogAction.OK).fire();
                            event.reset();
                        } else {
                            event.hide();
                        }
                    } else {
                        onCancel.call();
                        event.reset();
                    }
                })
                .fire();
    }

    private static StoryContent show(final Spy[] spies,
                                         final StoryPopups popups,
                                         final String caption,
                                         final SvgImage icon,
                                         final String text,
                                         final ResultHandler onResult) {
        final Spy onOk = spies[0];
        final Spy onCancel = spies[1];
        final StoryContent controller = StoryContent.html(popups.getEventBus(), "<p>" + text + "</p>");
        ControllerDialog.showOnce(popups, controller)
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
        return controller;
    }


    // --------------------------------------------------------------------------------


    private interface ResultHandler {

        void onResult(boolean ok);
    }
}
