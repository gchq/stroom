/*
 * Copyright 2016 Crown Copyright
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

package stroom.widget.popup.client.view;

import stroom.svg.shared.SvgImage;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.shared.NullSafe;
import stroom.widget.component.client.ContextValue;
import stroom.widget.component.client.Contexts;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerNode;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupSupport;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.ui.PopupPanel;
import com.google.gwt.user.client.ui.Widget;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class PopupSupportImpl implements PopupSupport {

    private Popup popup;
    private SvgImage icon;
    private String caption;
    private Boolean modal;

    // What the popup shows, whose context gives the dialog's task monitor to all it owns
    private final Controller controller;
    // Whether the dialog's task monitor is the one the controller provides, to take back when hidden
    private boolean providesTaskMonitor;
    private HidePopupRequestEvent.Handler hideRequestHandler;
    private HidePopupEvent.Handler hideHandler;
    private List<Element> autoHidePartners;
    private DialogButtons dialogButtons;
    private DialogActionHandler dialogActionHandler;

    public PopupSupportImpl(final Controller controller,
                            final SvgImage icon,
                            final String caption,
                            final Boolean modal,
                            final Element... autoHidePartners) {
        this.controller = controller;
        setIcon(icon);
        setCaption(caption);

        if (modal != null) {
            setModal(modal);
        }

        if (autoHidePartners != null) {
            for (final Element element : autoHidePartners) {
                addAutoHidePartner(element);
            }
        }
    }

    private void addAutoHidePartner(final Element element) {
        if (autoHidePartners == null) {
            autoHidePartners = new ArrayList<>();
        }
        autoHidePartners.add(element);
    }

    @Override
    public void show(final ShowPopupEvent event) {
        final PopupType popupType = event.getPopupType();
        final PopupPosition popupPosition = event.getPopupPosition();
        final PopupSize popupSize = event.getPopupSize();
        hideRequestHandler = event.getHideRequestHandler();
        hideHandler = event.getHideHandler();

        if (popup == null) {
            final Controller shown = event.getController();
            popup = createPopup(popupType, popupSize, request -> HidePopupRequestEvent
                    .builder(shown)
                    .autoClose(request.isAutoClose())
                    .action(request.getAction())
                    .cancelHandler(request.getCancelHandler())
                    .fire());
        }
        final PopupPanel popupPanel = (PopupPanel) popup;

        // Add auto hide partners.
        for (final Element element : NullSafe.list(autoHidePartners)) {
            popupPanel.addAutoHidePartner(element);
        }

        PopupUtil.showPopup(
                popup,
                NullSafe.isTrue(modal),
                popupPosition,
                popupSize,
                popupType,
                () -> {
                    // Tell the handler that the popup is visible.
                    if (event.getShowHandler() != null) {
                        Scheduler.get().scheduleDeferred(() ->
                                event.getShowHandler().onShow(event));
                    } else if (dialogButtons != null) {
                        // If no custom handler is specified then focus the buttons
                        Scheduler.get().scheduleDeferred(() -> dialogButtons.focus());
                    }
                });
    }

    @Override
    public void hideRequest(final HidePopupRequestEvent event) {
        if (hideRequestHandler != null) {
            hideRequestHandler.onHideRequest(event);
        }
    }

    @Override
    public void hide(final HidePopupEvent event) {
        if (popup != null) {
            popup.forceHide(event.isAutoClose());
            popup = null;
            clearTaskMonitor();
            hideRequestHandler = null;
            if (hideHandler != null) {
                hideHandler.onHide(event);
                hideHandler = null;
            }
        }
    }

    @Override
    public void setEnabled(final boolean enabled) {
        if (dialogButtons != null) {
            dialogButtons.setEnabled(enabled);
        }
    }

    @Override
    public void setEnabled(final DialogAction action, final boolean enabled) {
        if (dialogButtons != null) {
            dialogButtons.setEnabled(action, enabled);
        }
    }

    @Override
    public void setIcon(final SvgImage icon) {
        this.icon = icon;
        if (popup != null) {
            popup.setIcon(icon);
        }
    }

    @Override
    public void setCaption(final String caption) {
        this.caption = caption;
        if (popup != null) {
            popup.setCaption(caption);
        }
    }

    private void setModal(final boolean modal) {
        this.modal = modal;
    }

    private Popup createPopup(final PopupType popupType,
                              final PopupSize popupSize,
                              final Consumer<HideRequest> onHideRequest) {
        dialogActionHandler = action -> onHideRequest.accept(
                new HideRequest(action, null));

        Popup popup = null;
        switch (popupType) {
            case POPUP: {
                final SimplePopup simplePopup = new SimplePopup();
                simplePopup.setOnAction(dialogActionHandler::onDialogAction);
                simplePopup.setContent(controller.asWidget());
                popup = simplePopup;
                break;
            }
            case DIALOG: {
                final AbstractPopupPanel dialog = createDialog(popupSize, dialogActionHandler);
                // (A fixed-size dialog with no buttons has never shown its tasks)
                if (popupSize != null) {
                    setTaskMonitor((TaskMonitorFactory) dialog);
                }
                dialog.setContent(controller.asWidget());
                popup = dialog;
                break;
            }
            case CLOSE_DIALOG: {
                final CloseContent content = new CloseContent();
                content.setOnHideRequest(onHideRequest);
                content.setContent(controller.asWidget());
                popup = createDialogWith(content, content, popupSize);
                break;
            }
            case OK_CANCEL_DIALOG: {
                final OkCancelContent content = new OkCancelContent();
                content.setOnHideRequest(onHideRequest);
                content.setContent(controller.asWidget());
                popup = createDialogWith(content, content, popupSize);
                break;
            }
            case CREATE_OK_CANCEL_DIALOG: {
                final CreateOkCancelContent content = new CreateOkCancelContent();
                content.setOnHideRequest(onHideRequest);
                content.setContent(controller.asWidget());
                popup = createDialogWith(content, content, popupSize);
                break;
            }
            case ACCEPT_REJECT_DIALOG: {
                final AcceptRejectContent content = new AcceptRejectContent();
                content.setOnHideRequest(onHideRequest);
                content.setContent(controller.asWidget());
                popup = createDialogWith(content, content, popupSize);
                break;
            }
        }

        if (popup != null && icon != null) {
            popup.setIcon(icon);
        }
        if (popup != null && caption != null) {
            popup.setCaption(caption);
        }

        return popup;
    }

    // A dialog holding content with buttons below it, which are told what the user asks of the
    // dialog (Escape, Ctrl+Enter), as a click on them would be
    private Popup createDialogWith(final Widget content,
                                   final DialogButtons buttons,
                                   final PopupSize popupSize) {
        dialogButtons = buttons;
        dialogActionHandler = buttons;
        final AbstractPopupPanel dialog = createDialog(popupSize, buttons);
        setTaskMonitor((TaskMonitorFactory) dialog);
        dialog.setContent(content);
        return dialog;
    }

    // A dialog that can be resized as its size says, or one of a fixed size if it has none
    private static AbstractPopupPanel createDialog(final PopupSize popupSize,
                                                   final DialogActionHandler onAction) {
        final AbstractPopupPanel dialog = popupSize != null
                ? new ResizableDialog(popupSize)
                : new Dialog();
        dialog.setOnAction(onAction::onDialogAction);
        return dialog;
    }

    // What the dialog shows, and everything it owns (through its context), show their tasks on the
    // dialog, unless it provides a task monitor of its own (e.g. a list's pager)
    private void setTaskMonitor(final TaskMonitorFactory taskMonitorFactory) {
        final ControllerNode node = controller.getControllerNode();
        // Null only for a mock (e.g. Mockito's, in a test)
        if (node != null) {
            final ContextValue<TaskMonitorFactory> provided = node.provided(Contexts.TASK_MONITOR);
            if (provided == null || provided.get() == Contexts.TASK_MONITOR.getDefaultValue()) {
                node.setProvided(Contexts.TASK_MONITOR, taskMonitorFactory);
                providesTaskMonitor = true;
            }
        }
    }

    // A controller shown again is given its next dialog's task monitor, not this one's
    private void clearTaskMonitor() {
        if (providesTaskMonitor) {
            controller.getControllerNode().setProvided(Contexts.TASK_MONITOR,
                    Contexts.TASK_MONITOR.getDefaultValue());
            providesTaskMonitor = false;
        }
    }

    @Override
    public void onDialogAction(final DialogAction action) {
        if (dialogActionHandler != null) {
            dialogActionHandler.onDialogAction(action);
        }
    }
}
