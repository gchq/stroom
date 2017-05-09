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

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.PopupPanel;
import com.gwtplatform.mvp.client.View;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupPosition.HorizontalLocation;
import stroom.widget.popup.client.presenter.PopupPosition.VerticalLocation;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupSupport;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;

import java.util.ArrayList;
import java.util.List;

public class PopupSupportImpl implements PopupSupport {
    private static final int POPUP_SHADOW_WIDTH = 9;
    private Popup popup;
    private String caption;
    private Boolean modal;

    private View view;
    private PopupUiHandlers popupUiHandlers;
    private List<Element> autoHidePartners;
    private HasEnabled controls;

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
            autoHidePartners = new ArrayList<Element>();
        }
        autoHidePartners.add(element);
    }

    public List<Element> getAutoHidePartners() {
        return autoHidePartners;
    }

    @Override
    public void show(final PopupType popupType, final PopupPosition popupPosition, final PopupSize popupSize,
            final PopupUiHandlers popupUiHandlers) {
        this.popupUiHandlers = popupUiHandlers;

        if (popup == null) {
            popup = createPopup(popupType, popupSize, popupUiHandlers);
        }
        final PopupPanel popupPanel = (PopupPanel) popup;
        // Hide the popup because we are going to position it before making it
        // visible.
        popupPanel.setVisible(false);
        // Way to set popups to be non modal for now.
        if (modal != null) {
            popupPanel.setModal(modal);
        }

        // Set the popup size.
        if (popupSize != null) {
            popupPanel.setWidth(popupSize.getWidth() + "px");
            popupPanel.setHeight(popupSize.getHeight() + "px");
        }

        // Add auto hide partners.
        if (autoHidePartners != null && autoHidePartners.size() > 0) {
            for (final Element element : autoHidePartners) {
                popupPanel.addAutoHidePartner(element);
            }
        }

        // Attach the popup to the DOM.
        popupPanel.show();
        // Defer the command to position and make visible because we need the
        // popup to size first.
        Scheduler.get().scheduleDeferred(() -> {
            if (popup != null) {
                // Now get the popup size.
                final int w = popupPanel.getOffsetWidth();
                final int h = popupPanel.getOffsetHeight();

                if (popupPosition == null) {
                    // Center the popup in the client window.
                    centerPopup(popup, w, h);
                } else {
                    // Position the popup so it is as close as possible to
                    // the required location but is all on screen.
                    positionPopup(popup, popupType, popupPosition, w, h);
                }

                // Make the popup visible.
                popupPanel.setVisible(true);
                popupPanel.getElement().getStyle().setOpacity(1);

                // Tell the view that the popup is visible if necessary.
                onShow();
            }
        });
    }

    private void centerPopup(final Popup popup, final int width, final int height) {
        final int parentWidth = Window.getClientWidth();
        final int parentHeight = Window.getClientHeight();
        final int left = (parentWidth - width) / 2;
        final int top = (parentHeight - height) / 2;
        popup.setPopupPosition(left, top);
    }

    /**
     * Positions the popup, called after the offset width and height of the
     * popup are known.
     */
    private void positionPopup(final Popup popup, final PopupType popupType, final PopupPosition popupPosition,
            int offsetWidth, int offsetHeight) {
        int shadowWidth = 0;
        if (popupType != PopupType.POPUP) {
            shadowWidth = POPUP_SHADOW_WIDTH;
        }

        offsetWidth = offsetWidth - (2 * shadowWidth);
        offsetHeight = offsetHeight - (2 * shadowWidth);

        int left = -shadowWidth;
        if (HorizontalLocation.LEFT.equals(popupPosition.getHorizontalLocation())) {
            // Positioned to the left but aligned with the right edge of the
            // relative box.
            left = popupPosition.getRight() - offsetWidth + shadowWidth;
        } else {
            // Positioned to the right but aligned with the left edge of the
            // relative box.
            left = popupPosition.getLeft() - shadowWidth;
        }

        // Make sure scrolling is taken into account, since
        // box.getAbsoluteLeft() takes scrolling into account.
        final int windowRight = Window.getClientWidth() + Window.getScrollLeft();
        final int windowLeft = Window.getScrollLeft();

        // Distance from the left edge of the text box to the right edge of the
        // window
        final int distanceToWindowRight = windowRight - left;

        // Distance from the left edge of the text box to the left edge of the
        // window
        final int distanceFromWindowLeft = left - windowLeft;

        // If there is not enough space for the overflow of the popup's width to
        // the right, and there IS enough space
        // for the overflow to the left, then show the popup on the left. If
        // there is not enough space on either side, then
        // position at the far left.
        if (distanceToWindowRight < offsetWidth) {
            if (distanceFromWindowLeft >= offsetWidth) {
                // Positioned to the left but aligned with the right edge of the
                // relative box.
                left = popupPosition.getRight() - offsetWidth + shadowWidth;
            } else {
                left = -shadowWidth;
            }
        }

        // Calculate top position for the popup
        int top = -shadowWidth;
        if (VerticalLocation.ABOVE.equals(popupPosition.getVerticalLocation())) {
            // Positioned above.
            top = popupPosition.getTop() - offsetHeight + shadowWidth;
        } else {
            // Default position is below.
            top = popupPosition.getBottom() - shadowWidth;
        }

        // Make sure scrolling is taken into account, since box.getAbsoluteTop()
        // takes scrolling into account.
        final int windowTop = Window.getScrollTop();
        final int windowBottom = Window.getScrollTop() + Window.getClientHeight();

        // Distance from the top edge of the window to the top edge of the text
        // box.
        final int distanceFromWindowTop = top - windowTop;

        // Distance from the bottom edge of the window to the relative object.
        final int distanceToWindowBottom = windowBottom - top;

        // If there is not enough space for the popup's height below the text
        // box and there IS enough space for the
        // popup's height above the text box, then then position the popup above
        // the text box. If there is not enough
        // space above or below, then position at the very top.
        if (distanceToWindowBottom < offsetHeight) {
            if (distanceFromWindowTop >= offsetHeight) {
                // Positioned above.
                top = popupPosition.getTop() - offsetHeight + shadowWidth;
            } else {
                top = -shadowWidth;
            }
        }

        // Make sure the left and top positions are on screen.
        if (left + offsetWidth > windowRight) {
            left = windowRight - offsetWidth;
        }
        if (top + offsetHeight > windowBottom) {
            top = windowBottom - offsetHeight;
        }

        // Make sure left and top have not ended up less than 0.
        if (left < 0) {
            left = 0;
        }
        if (top < 0) {
            top = 0;
        }

        popup.setPopupPosition(left, top);
    }

    @Override
    public void hide() {
        hide(false, false);
    }

    @Override
    public void hide(final boolean autoClose, final boolean ok) {
        if (popup != null) {
            popup.forceHide(autoClose);
            popup = null;

            popupUiHandlers.onHide(autoClose, ok);
        }
    }

    @Override
    public void setEnabled(final boolean enabled) {
        if (controls != null) {
            controls.setEnabled(enabled);
        }
    }

    /**
     * Used by some views to focus widgets.
     */
    protected void onShow() {
    }

    private void setCaption(final String caption) {
        this.caption = caption;
        if (popup != null) {
            popup.setCaption(caption);
        }
    }

    private void setModal(final boolean modal) {
        this.modal = modal;
    }

    private Popup createPopup(final PopupType popupType, final PopupSize popupSize,
            final PopupUiHandlers popupUiHandlers) {
        Popup popup = null;

        if (popupSize != null) {
            switch (popupType) {
            case POPUP:
                popup = new SimplePopup(popupUiHandlers);
                popup.setContent(view.asWidget());
                break;
            case DIALOG:
                popup = new ResizableDialog(popupUiHandlers, popupSize);
                popup.setContent(view.asWidget());
                break;
            case CLOSE_DIALOG:
                popup = new ResizableDialog(popupUiHandlers, popupSize);
                final ResizableCloseContent closeContent = new ResizableCloseContent(popupUiHandlers);
                controls = closeContent;
                closeContent.setContent(view.asWidget());
                popup.setContent(closeContent);
                break;
            case OK_CANCEL_DIALOG:
                popup = new ResizableDialog(popupUiHandlers, popupSize);
                final ResizableOkCancelContent okCancelContent = new ResizableOkCancelContent(popupUiHandlers);
                controls = okCancelContent;
                okCancelContent.setContent(view.asWidget());
                popup.setContent(okCancelContent);
                break;
            }
        } else {
            switch (popupType) {
            case POPUP:
                popup = new SimplePopup(popupUiHandlers);
                popup.setContent(view.asWidget());
                break;
            case DIALOG:
                popup = new Dialog(popupUiHandlers);
                popup.setContent(view.asWidget());
                break;
            case CLOSE_DIALOG:
                popup = new Dialog(popupUiHandlers);
                final CloseContent closeContent = new CloseContent(popupUiHandlers);
                controls = closeContent;
                closeContent.setContent(view.asWidget());
                popup.setContent(closeContent);
                break;
            case OK_CANCEL_DIALOG:
                popup = new Dialog(popupUiHandlers);
                final OkCancelContent okCancelContent = new OkCancelContent(popupUiHandlers);
                controls = okCancelContent;
                okCancelContent.setContent(view.asWidget());
                popup.setContent(okCancelContent);
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
