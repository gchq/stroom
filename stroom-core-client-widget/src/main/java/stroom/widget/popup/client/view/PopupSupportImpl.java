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

import stroom.task.client.HasTaskListener;
import stroom.util.shared.GwtNullSafe;
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
import com.gwtplatform.mvp.client.View;

import java.util.ArrayList;
import java.util.List;

public class PopupSupportImpl implements PopupSupport {

    private Popup popup;
    private String caption;
    private Boolean modal;

    private View view;
    private HasTaskListener hasTaskListener;
    private HidePopupRequestEvent.Handler hideRequestHandler;
    private HidePopupEvent.Handler hideHandler;
    private List<Element> autoHidePartners;
    private DialogButtons dialogButtons;
    private DialogActionUiHandlers dialogActionHandler;

    public PopupSupportImpl(final View view,
                            final HasTaskListener hasTaskListener,
                            final String caption,
                            final Boolean modal,
                            final Element... autoHidePartners) {
        setView(view);
        setHasTaskListener(hasTaskListener);
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
            final HideRequestUiHandlers uiHandlers = new DefaultHideRequestUiHandlers(event.getPresenterWidget());
            popup = createPopup(popupType, popupSize, uiHandlers);
        }
        final PopupPanel popupPanel = (PopupPanel) popup;

        // Add auto hide partners.
        for (final Element element : GwtNullSafe.list(autoHidePartners)) {
            popupPanel.addAutoHidePartner(element);
        }

        final String uniqueId = createUniqueId(event);

        PopupUtil.showPopup(
                uniqueId,
                popup,
                GwtNullSafe.isTrue(modal),
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

//    @Override
//    public void show(final ShowPopupEvent event) {
//        final PopupType popupType = event.getPopupType();
//        final PopupPosition popupPosition = event.getPopupPosition();
//        final PopupSize popupSize = event.getPopupSize();
//        hideRequestHandler = event.getHideRequestHandler();
//        hideHandler = event.getHideHandler();
//
//        if (popup == null) {
//            final HideRequestUiHandlers uiHandlers = new DefaultHideRequestUiHandlers(event.getPresenterWidget());
//            popup = createPopup(popupType, popupSize, uiHandlers);
//        }
//
//        // Add auto hide partners.
//        final PopupPanel popupPanel = (PopupPanel) popup;
//        for (final Element element : GwtNullSafe.list(autoHidePartners)) {
//            popupPanel.addAutoHidePartner(element);
//        }
//
//        final String uniqueId = createUniqueId(event);
//
//        PopupUtil.showPopup(
//                uniqueId,
//                popup,
//                GwtNullSafe.isTrue(modal),
//                popupPosition,
//                popupSize,
//                popupType,
//                () -> {
//                    if (event.getShowHandler() != null) {
//                        event.getShowHandler().onShow(event);
//                    } else if (dialogButtons != null) {
//                        // If no custom handler is specified then focus the buttons
//                        dialogButtons.focus();
//                    }
//                });
//    }

    private String createUniqueId(final ShowPopupEvent event) {
//        GWT.log("popupPosition: " + event.getPopupPosition());

        // Create an id to uniquely identify the show popup call, in this case
        // the presenter that launched it and the position rect of that presenter.
        // The position is to support context menus where multiple popups are spawned
        // from the same presenter
        final String className = GwtNullSafe.getOrElse(
                event.getPresenterWidget(),
                Object::getClass,
                Class::getName,
                "UNKNOWN_CLASS");
        final String position = GwtNullSafe.get(
                event.getPopupPosition(),
                PopupPosition::getRelativeRect,
                rect ->
                        rect.getTop() + "_" +
                                rect.getLeft() + "_" +
                                rect.getBottom() + "_" +
                                rect.getRight());
        final String id = className + "__" + position;
//        GWT.log("id: " + id);
        return id;
    }

//    private int getSize(final int current, Size size) {
//        int newSize = current;
//        if (size != null) {
//            if (size.getInitial() == null) {
//                size.setInitial(current);
//            }
//
//            if (size.getMin() == null) {
//                size.setMin(Math.min(current, size.getInitial()));
//            }
//
//            newSize = Math.max(size.getMin(), size.getInitial());
//        }
//        return newSize;
//    }

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
            hideRequestHandler = null;
            if (hideHandler != null) {
                hideHandler.onHide(event);
                hideHandler = null;
            }
            CurrentFocus.pop();
        }
    }

    @Override
    public void setEnabled(final boolean enabled) {
        if (dialogButtons != null) {
            dialogButtons.setEnabled(enabled);
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
                              final HideRequestUiHandlers uiHandlers) {
        Popup popup = null;

        dialogActionHandler = action -> uiHandlers.hideRequest(
                new HideRequest(action, null));

        if (popupSize != null) {
            switch (popupType) {
                case POPUP: {
                    popup = new SimplePopup(dialogActionHandler);
                    popup.setContent(view.asWidget());
                    break;
                }
                case DIALOG: {
                    final ResizableDialog resizableDialog = new ResizableDialog(dialogActionHandler, popupSize);
                    hasTaskListener.setTaskListener(resizableDialog);
                    resizableDialog.setContent(view.asWidget());
                    popup = resizableDialog;

                    break;
                }
                case CLOSE_DIALOG: {
                    final ResizableCloseContent closeContent = new ResizableCloseContent(uiHandlers);
                    closeContent.setContent(view.asWidget());
                    dialogButtons = closeContent;
                    dialogActionHandler = closeContent;

                    final ResizableDialog resizableDialog = new ResizableDialog(closeContent, popupSize);
                    hasTaskListener.setTaskListener(resizableDialog);
                    resizableDialog.setContent(closeContent);
                    popup = resizableDialog;

                    break;
                }
                case OK_CANCEL_DIALOG: {
                    final ResizableOkCancelContent okCancelContent = new ResizableOkCancelContent(uiHandlers);
                    okCancelContent.setContent(view.asWidget());
                    dialogActionHandler = okCancelContent;
                    dialogButtons = okCancelContent;

                    final ResizableDialog resizableDialog = new ResizableDialog(okCancelContent, popupSize);
                    hasTaskListener.setTaskListener(resizableDialog);
                    resizableDialog.setContent(okCancelContent);
                    popup = resizableDialog;

                    break;
                }
                case ACCEPT_REJECT_DIALOG: {
                    final ResizableAcceptRejectContent acceptRejectContent = new ResizableAcceptRejectContent(
                            uiHandlers);
                    acceptRejectContent.setContent(view.asWidget());
                    dialogActionHandler = acceptRejectContent;
                    dialogButtons = acceptRejectContent;

                    final ResizableDialog resizableDialog = new ResizableDialog(acceptRejectContent, popupSize);
                    hasTaskListener.setTaskListener(resizableDialog);
                    resizableDialog.setContent(acceptRejectContent);
                    popup = resizableDialog;

                    break;
                }
            }
        } else {
            switch (popupType) {
                case POPUP: {
                    popup = new SimplePopup(dialogActionHandler);
                    popup.setContent(view.asWidget());
                    break;
                }
                case DIALOG: {
                    popup = new Dialog(dialogActionHandler);
                    popup.setContent(view.asWidget());
                    break;
                }
                case CLOSE_DIALOG: {
                    final CloseContent closeContent = new CloseContent(uiHandlers);
                    closeContent.setContent(view.asWidget());
                    dialogActionHandler = closeContent;
                    dialogButtons = closeContent;

                    final Dialog dialog = new Dialog(closeContent);
                    hasTaskListener.setTaskListener(dialog);
                    dialog.setContent(closeContent);
                    popup = dialog;

                    break;
                }
                case OK_CANCEL_DIALOG: {
                    final OkCancelContent okCancelContent = new OkCancelContent(uiHandlers);
                    okCancelContent.setContent(view.asWidget());
                    dialogActionHandler = okCancelContent;
                    dialogButtons = okCancelContent;

                    final Dialog dialog = new Dialog(okCancelContent);
                    hasTaskListener.setTaskListener(dialog);
                    dialog.setContent(okCancelContent);
                    popup = dialog;

                    break;
                }
                case ACCEPT_REJECT_DIALOG: {
                    final AcceptRejectContent acceptRejectContent = new AcceptRejectContent(uiHandlers);
                    acceptRejectContent.setContent(view.asWidget());
                    dialogActionHandler = acceptRejectContent;
                    dialogButtons = acceptRejectContent;

                    final Dialog dialog = new Dialog(acceptRejectContent);
                    hasTaskListener.setTaskListener(dialog);
                    dialog.setContent(acceptRejectContent);
                    popup = dialog;

                    break;
                }
            }
        }

        if (caption != null) {
            popup.setCaption(caption);
        }

        return popup;
    }

    private void setView(final View view) {
        this.view = view;
    }

    private void setHasTaskListener(final HasTaskListener hasTaskListener) {
        this.hasTaskListener = hasTaskListener;
    }

    @Override
    public void onDialogAction(final DialogAction action) {
        if (dialogActionHandler != null) {
            dialogActionHandler.onDialogAction(action);
        }
    }
}
