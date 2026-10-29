/*
 * Copyright 2024 Crown Copyright
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

package stroom.widget.popup.client;


import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.PopupPanel;

public class PopupUtil {

    private static final int POPUP_SHADOW_WIDTH = 9;
    private static final String ROLE = "role";
    private static final String DIALOG_ROLE = "dialog";
    private static final String ALERT_DIALOG_ROLE = "alertdialog";
    private static final String ARIA_DESCRIBED_BY = "aria-describedby";
    private static final String ARIA_MODAL = "aria-modal";
    // The glass behind a modal popup
    private static final String GLASS_CLASS = "popup-glass";
    // The least room left between a dialog and each edge of the window
    private static final int WINDOW_MARGIN = 10;

    /// Makes a popup a dialog for screen readers, named by its title.
    ///
    /// @param popup The popup's element.
    /// @param title The element holding the dialog's title.
    public static void setDialogRole(final Element popup, final Element title) {
        if (title.getId() == null || title.getId().isEmpty()) {
            title.setId(DOM.createUniqueId());
        }
        popup.setAttribute(ROLE, DIALOG_ROLE);
        popup.setAttribute("aria-labelledby", title.getId());
    }

    /// Makes the dialog holding some content an alert dialog for screen readers, described by its
    /// message, so that a screen reader reads the message as soon as the dialog opens, even though
    /// the focus goes to its buttons. Call it once the content is in the dialog (e.g. when it is
    /// attached).
    ///
    /// @param content The element of the dialog's content.
    /// @param message The element holding the message, or null to describe the dialog by nothing.
    /// @return True if the content is in a dialog, which is now an alert dialog.
    public static boolean setAlertDialogRole(final Element content, final Element message) {
        final Element dialog = findDialog(content);
        if (dialog == null) {
            return false;
        }
        dialog.setAttribute(ROLE, ALERT_DIALOG_ROLE);
        return setDialogDescription(content, message);
    }

    /// Describes the dialog holding some content by a message in it, so that a screen reader reads
    /// the message as the dialog opens, wherever the focus goes. Call it once the content is in the
    /// dialog (e.g. when it is attached).
    ///
    /// @param content The element of the dialog's content.
    /// @param message The element holding the message, or null to describe the dialog by nothing.
    /// @return True if the content is in a dialog.
    public static boolean setDialogDescription(final Element content, final Element message) {
        final Element dialog = findDialog(content);
        if (dialog == null) {
            return false;
        }
        if (message == null) {
            dialog.removeAttribute(ARIA_DESCRIBED_BY);
        } else {
            if (message.getId() == null || message.getId().isEmpty()) {
                message.setId(DOM.createUniqueId());
            }
            dialog.setAttribute(ARIA_DESCRIBED_BY, message.getId());
        }
        return true;
    }

    // The dialog (or alert dialog) the element is in, or null if it isn't in one
    private static Element findDialog(final Element element) {
        Element current = element;
        while (current != null) {
            final String role = current.getAttribute(ROLE);
            if (DIALOG_ROLE.equals(role) || ALERT_DIALOG_ROLE.equals(role)) {
                return current;
            }
            current = current.getParentElement();
        }
        return null;
    }

    public static void showPopup(final Popup popup,
                                 final boolean isModal,
                                 final PopupPosition popupPosition,
                                 final PopupSize popupSize,
                                 final PopupType popupType,
                                 final Runnable onShow) {
        final PopupPanel popupPanel = (PopupPanel) popup;
        // Hide the popup because we are going to position it before making it
        // visible.
        popupPanel.setVisible(false);
        popupPanel.setModal(false);

        // Way to set popups to be modal using glass.
        if (isModal) {
            // Stroom's glass (popup.css), not GWT's gwt-PopupPanelGlass
            popupPanel.setGlassStyleName(GLASS_CLASS);
            popupPanel.setGlassEnabled(isModal);
        }
        // A dialog shown modal tells screen readers that the rest of the page can't be used
        final Element element = popupPanel.getElement();
        final String role = element.getAttribute(ROLE);
        if (DIALOG_ROLE.equals(role) || ALERT_DIALOG_ROLE.equals(role)) {
            if (isModal) {
                element.setAttribute(ARIA_MODAL, "true");
                // And keeps the focus in it, as the rest of the page can't be used
                DialogFocusTrap.install(element);
            } else {
                element.removeAttribute(ARIA_MODAL);
            }
        }

        // Attach the popup to the DOM, returning the focus to where it is now when it closes
        CurrentFocus.remember(popupPanel);
        popupPanel.show();
        // Defer the command to position and make visible because we need the
        // popup to size first.
        Scheduler.get().scheduleDeferred(() -> {
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
            if (popupType != PopupType.POPUP) {
                // A dialog is no bigger than the window, so none of it (e.g. its title or its buttons)
                // is off the screen; its content scrolls instead
                newWidth = Math.min(newWidth, Window.getClientWidth() - (2 * WINDOW_MARGIN));
                newHeight = Math.min(newHeight, Window.getClientHeight() - (2 * WINDOW_MARGIN));
            }
            if (popupType == PopupType.POPUP) {
                // A popup's content is its own (a menu, a list), so it is sized as GWT sizes it
                if (newWidth != w) {
                    popupPanel.setWidth(newWidth + "px");
                }
                if (newHeight != h) {
                    popupPanel.setHeight(newHeight + "px");
                }
            } else {
                // A dialog's container, sized by dialog.css from its custom properties
                final Element container = popupPanel.getWidget().getElement();
                if (newWidth != w) {
                    DialogWindow.setWidth(container, newWidth);
                }
                if (newHeight != h) {
                    DialogWindow.setHeight(container, newHeight);
                }
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

                final Position position = PositionUtil.getPosition(shadowWidth,
                        popupPosition,
                        newWidth,
                        newHeight);
                popup.setPopupPosition((int) position.getLeft(), (int) position.getTop());
            }

            // Make the popup visible.
            popupPanel.setVisible(true);
            // A floating popup is unseen until it is placed (popup.css)
            if (popupPanel.getElement().hasClassName("popup--floating")) {
                popupPanel.addStyleName("popup--placed");
            }

            if (onShow != null) {
                Scheduler.get().scheduleDeferred(onShow::run);
            }
        });
    }

    private static int getSize(final int current, final Size size) {
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
}
