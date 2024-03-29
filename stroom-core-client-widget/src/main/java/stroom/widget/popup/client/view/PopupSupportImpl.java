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

import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupSupport;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.popup.client.presenter.Position;
import stroom.widget.popup.client.presenter.PositionUtil;
import stroom.widget.popup.client.presenter.Size;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.ui.PopupPanel;
import com.gwtplatform.mvp.client.View;

import java.util.ArrayList;
import java.util.List;

public class PopupSupportImpl implements PopupSupport {

    private static final int POPUP_SHADOW_WIDTH = 9;
    private Popup popup;
    private String caption;
    private Boolean modal;

    private View view;
    private HidePopupRequestEvent.Handler hideRequestHandler;
    private HidePopupEvent.Handler hideHandler;
    private List<Element> autoHidePartners;
    private DialogButtons dialogButtons;

    public PopupSupportImpl(final View view, final String caption, final Boolean modal,
                            final Element... autoHidePartners) {
        setView(view);
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

    public List<Element> getAutoHidePartners() {
        return autoHidePartners;
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
        // Hide the popup because we are going to position it before making it
        // visible.
        popupPanel.setVisible(false);
        popupPanel.setModal(false);

        // Way to set popups to be modal using glass.
        if (modal != null) {
            popupPanel.setGlassEnabled(modal);
        }

        // Add auto hide partners.
        if (autoHidePartners != null && autoHidePartners.size() > 0) {
            for (final Element element : autoHidePartners) {
                popupPanel.addAutoHidePartner(element);
            }
        }

        // Attach the popup to the DOM.
        CurrentFocus.push();
        popupPanel.show();
        // Defer the command to position and make visible because we need the
        // popup to size first.
        Scheduler.get().scheduleDeferred(() -> {
            if (popup != null) {
                // Now get the popup size.
                final int w = popupPanel.getOffsetWidth();
                final int h = popupPanel.getOffsetHeight();

                // Set the popup size.
                int newWidth = w;
                int newHeight = h;
                if (popupSize != null) {
                    newWidth = getSize(newWidth, popupSize.getWidth());
                    newHeight = getSize(newHeight, popupSize.getHeight());
                }
                if (newWidth != w) {
                    popupPanel.setWidth(newWidth + "px");
                }
                if (newHeight != h) {
                    popupPanel.setHeight(newHeight + "px");
                }

                if (popupPosition == null) {
                    // Center the popup in the client window.
                    final Position position = PositionUtil.center(newWidth, newHeight);
                    popup.setPopupPosition((int) position.getLeft(), (int) position.getTop());

                } else {
                    // Position the popup so it is as close as possible to
                    // the required location but is all on screen.
                    int shadowWidth = 0;
                    if (popupType != PopupType.POPUP) {
                        shadowWidth = POPUP_SHADOW_WIDTH;
                    }

                    final Position position = PositionUtil.getPosition(shadowWidth, popupPosition, newWidth, newHeight);
                    popup.setPopupPosition((int) position.getLeft(), (int) position.getTop());
                }

                // Make the popup visible.
                popupPanel.setVisible(true);
                popupPanel.getElement().getStyle().setOpacity(1);

                // Tell the handler that the popup is visible.
                if (event.getShowHandler() != null) {
                    Scheduler.get().scheduleDeferred(() ->
                            event.getShowHandler().onShow(event));
                } else if (dialogButtons != null) {
                    // If no custom handler is specified then focus the buttons
                    Scheduler.get().scheduleDeferred(() -> dialogButtons.focus());
                }
            }
        });
    }

    private int getSize(final int current, Size size) {
        int newSize = current;
        if (size != null) {
            if (size.getInitial() == null) {
                size.setInitial(current);
            }

            if (size.getMin() == null) {
                size.setMin(Math.min(current, size.getInitial()));
            }

            newSize = Math.max(size.getMin(), size.getInitial());
        }
        return newSize;
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

        if (popupSize != null) {
            switch (popupType) {
                case POPUP:
                    popup = new SimplePopup(uiHandlers);
                    popup.setContent(view.asWidget());
                    break;
                case DIALOG:
                    popup = new ResizableDialog(uiHandlers, popupSize, popupType);
                    popup.setContent(view.asWidget());
                    break;
                case CLOSE_DIALOG:
                    popup = new ResizableDialog(uiHandlers, popupSize, popupType);
                    final ResizableCloseContent closeContent = new ResizableCloseContent(uiHandlers);
                    dialogButtons = closeContent;
                    closeContent.setContent(view.asWidget());
                    popup.setContent(closeContent);
                    break;
                case OK_CANCEL_DIALOG:
                    popup = new ResizableDialog(uiHandlers, popupSize, popupType);
                    final ResizableOkCancelContent okCancelContent = new ResizableOkCancelContent(uiHandlers);
                    dialogButtons = okCancelContent;
                    okCancelContent.setContent(view.asWidget());
                    popup.setContent(okCancelContent);
                    break;
                case ACCEPT_REJECT_DIALOG:
                    popup = new ResizableDialog(uiHandlers, popupSize, popupType);
                    final ResizableAcceptRejectContent acceptRejectContent = new ResizableAcceptRejectContent(
                            uiHandlers);
                    dialogButtons = acceptRejectContent;
                    acceptRejectContent.setContent(view.asWidget());
                    popup.setContent(acceptRejectContent);
                    break;
            }
        } else {
            switch (popupType) {
                case POPUP:
                    popup = new SimplePopup(uiHandlers);
                    popup.setContent(view.asWidget());
                    break;
                case DIALOG:
                    popup = new Dialog(uiHandlers, popupType);
                    popup.setContent(view.asWidget());
                    break;
                case CLOSE_DIALOG:
                    popup = new Dialog(uiHandlers, popupType);
                    final CloseContent closeContent = new CloseContent(uiHandlers);
                    dialogButtons = closeContent;
                    closeContent.setContent(view.asWidget());
                    popup.setContent(closeContent);
                    break;
                case OK_CANCEL_DIALOG:
                    popup = new Dialog(uiHandlers, popupType);
                    final OkCancelContent okCancelContent = new OkCancelContent(uiHandlers);
                    dialogButtons = okCancelContent;
                    okCancelContent.setContent(view.asWidget());
                    popup.setContent(okCancelContent);
                    break;
                case ACCEPT_REJECT_DIALOG:
                    popup = new Dialog(uiHandlers, popupType);
                    final AcceptRejectContent acceptRejectContent = new AcceptRejectContent(uiHandlers);
                    dialogButtons = acceptRejectContent;
                    acceptRejectContent.setContent(view.asWidget());
                    popup.setContent(acceptRejectContent);
                    break;
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
}
