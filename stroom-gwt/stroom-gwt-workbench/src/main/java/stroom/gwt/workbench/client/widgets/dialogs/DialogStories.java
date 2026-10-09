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

import stroom.gwt.workbench.client.widgets.ContentPresenter;
import stroom.gwt.workbench.client.widgets.StoryPopups;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.svg.shared.SvgImage;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Widget;

/// Stories for Stroom's dialogs.
///
/// Stroom has no dialog widget that a screen creates itself: a presenter is shown in a dialog
/// with `ShowPopupEvent`, whose `PopupType` picks the buttons (`CloseContent`, `OkCancelContent`,
/// `AcceptRejectContent`, ...) and whose `PopupSize` makes it a `ResizableDialog` rather than a
/// `Dialog`. Each story shows a stand-in presenter holding some text in a kind of dialog, with
/// Stroom's `PopupManager`.
public final class DialogStories {

    private DialogStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        // No args: every story renders its own dialog
        registry.component("Widgets/Dialogs/Dialog", DialogStories.class)
                .layout(StoryLayout.CENTERED)
                // Dialog — Close button only (non-resizable, draggable)
                .story("DialogClose", "Dialog — Close button",
                        context -> trigger(context, "Open Close dialog", dialog(
                            PopupType.CLOSE_DIALOG, "Information")
                            .text("This is a non-resizable dialog with a single Close button.",
                                    "Drag the title bar to move. Mirrors GWT CloseContent.")))
                // Dialog — OK / Cancel (non-resizable, draggable)
                .story("DialogOkCancel", "Dialog — OK / Cancel",
                        context -> trigger(context, "Open OK/Cancel dialog", dialog(
                            PopupType.OK_CANCEL_DIALOG, "Confirm Action")
                            .text("Are you sure you want to proceed?")))
                // Resizable Dialog — Close button
                .story("ResizableClose", "Resizable Dialog — Close",
                        context -> trigger(context, "Open resizable Close dialog", dialog(
                            PopupType.CLOSE_DIALOG, "Resizable Panel")
                            .size(500, 350)
                            .text("This dialog can be resized by dragging its edges.",
                                    "Drag title bar to move. Resize from any edge or corner.")))
                // Resizable Dialog — OK / Cancel
                .story("ResizableOkCancel", "Resizable Dialog — OK / Cancel",
                        context -> trigger(context, "Open resizable OK/Cancel dialog", dialog(
                            PopupType.OK_CANCEL_DIALOG, "Edit Settings")
                            .size(600, 400)
                            .text("A resizable dialog with OK and Cancel buttons.",
                                    "Mirrors GWT ResizableOkCancelContent.")))
                // Resizable Dialog — Accept / Reject (green Accept)
                .story("AcceptReject", "Resizable Dialog — Accept / Reject",
                        context -> trigger(context, "Open Accept/Reject dialog", dialog(
                            PopupType.ACCEPT_REJECT_DIALOG, "Review Changes")
                            .size(550, 350)
                            .text("Review the following changes before accepting or rejecting.",
                                    "Accept = green (GWT pattern), Reject = secondary.")))
                // Resizable Dialog — Save / Cancel
                .story("SaveCancel", "Resizable Dialog — Save / Cancel",
                        context -> trigger(context, "Open Save/Cancel dialog", dialog(
                            // Stroom has no Save/Cancel dialog type; a screen that
                            // saves uses OK_CANCEL_DIALOG, whose buttons are OK and Cancel.
                            PopupType.OK_CANCEL_DIALOG, "Edit Document")
                            .size(600, 420)
                            .text("Make your changes below, then Save or Cancel.")))
                // Resizable Dialog — Create / OK / Cancel
                .story("CreateOkCancel", "Resizable Dialog — Create / OK / Cancel",
                        context -> trigger(context, "Open Create/OK/Cancel dialog", dialog(
                            PopupType.CREATE_OK_CANCEL_DIALOG, "Create New Item")
                            .size(600, 400)
                            .text("Configure the new item, then Create, OK, or Cancel.")))
                // Alert popup — WARNING icon in title bar + Close
                .story("AlertPopup", "Alert popup",
                        context -> trigger(context, "Show Alert", dialog(
                            PopupType.CLOSE_DIALOG, "Alert")
                            .icon(SvgImage.WARNING)
                            .modal()
                            .text("Unable to process the request. The pipeline configuration is invalid.")))
                // Confirm popup — QUESTION icon in title bar + OK/Cancel
                .story("ConfirmPopup", "Confirm popup",
                        context -> trigger(context, "Show Confirm", dialog(
                            PopupType.OK_CANCEL_DIALOG, "Confirm")
                            .icon(SvgImage.QUESTION)
                            .modal()
                            .text("Are you sure you want to delete this item? This action cannot be undone.")))
                // Info popup — INFO icon in title bar + Close
                .story("InfoPopup", "Info popup",
                        context -> trigger(context, "Show Info", dialog(
                            PopupType.CLOSE_DIALOG, "Information")
                            .icon(SvgImage.INFO)
                            .modal()
                            .text("The processing job completed successfully with 1,234 records processed.")))
                // Error popup — ERROR icon in title bar + Close
                .story("ErrorPopup", "Error popup",
                        context -> trigger(context, "Show Error", dialog(
                            PopupType.CLOSE_DIALOG, "Error")
                            .icon(SvgImage.ERROR)
                            .modal()
                            .text("Failed to connect to database: connection refused on port 5432.")))
                // Modal — movable dialog with OK/Cancel
                .story("Modal", "Modal — movable OK/Cancel dialog",
                        context -> trigger(context, "Open modal", dialog(
                            PopupType.OK_CANCEL_DIALOG, "Confirm action")
                            .icon(SvgImage.QUESTION)
                            .modal()
                            .text("Are you sure you want to proceed? This action cannot be undone.")))
                // Resizable Modal — movable + resizable dialog with OK/Cancel
                .story("ResizableModal", "Resizable Modal — movable + resizable OK/Cancel",
                        context -> trigger(context, "Open resizable modal", dialog(
                            PopupType.OK_CANCEL_DIALOG, "Edit Configuration")
                            .size(650, 450)
                            .modal()
                            .text("A resizable modal dialog with OK and Cancel buttons.",
                                    "Use for complex editing forms that need flexible sizing.")));
    }

    private static DialogSpec dialog(final PopupType popupType, final String caption) {
        return new DialogSpec(popupType, caption);
    }

    /// A primary button that opens the dialog.
    private static Widget trigger(final StoryContext context, final String label, final DialogSpec spec) {
        final StoryPopups popups = StoryPopups.create(context);
        final FlowPanel panel = new FlowPanel();
        panel.add(DialogWidgets.button(label, DialogWidgets.PRIMARY, event -> spec.show(popups)));
        return panel;
    }


    // --------------------------------------------------------------------------------


    /// The dialog a story shows, as `ShowPopupEvent` options.
    private static final class DialogSpec {

        private final PopupType popupType;
        private final String caption;
        private PopupSize popupSize;
        private SvgImage icon;
        private boolean modal;
        private String html = "";

        private DialogSpec(final PopupType popupType, final String caption) {
            this.popupType = popupType;
            this.caption = caption;
        }

        /// Stroom's dialogs are resizable when given a size.
        private DialogSpec size(final int width, final int height) {
            this.popupSize = PopupSize.resizable(width, height);
            return this;
        }

        private DialogSpec icon(final SvgImage icon) {
            this.icon = icon;
            return this;
        }

        private DialogSpec modal() {
            this.modal = true;
            return this;
        }

        /// The dialog's paragraphs (constant text, so safe as HTML).
        private DialogSpec text(final String... paragraphs) {
            final StringBuilder sb = new StringBuilder();
            for (final String paragraph : paragraphs) {
                sb.append("<p>").append(paragraph).append("</p>");
            }
            this.html = sb.toString();
            return this;
        }

        private void show(final StoryPopups popups) {
            // A new presenter each time, as Stroom's presenters are, since PopupManager toggles a
            // presenter that is shown twice
            final ContentPresenter presenter = ContentPresenter.html(popups.getEventBus(), html);
            ShowPopupEvent.builder(presenter)
                    .popupType(popupType)
                    .popupSize(popupSize)
                    .icon(icon)
                    .caption(caption)
                    .modal(modal)
                    .fire();
        }
    }
}
