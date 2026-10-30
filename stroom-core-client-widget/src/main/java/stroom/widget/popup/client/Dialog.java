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

package stroom.widget.popup.client;

import stroom.svg.client.Icon;
import stroom.svg.shared.SvgImage;
import stroom.widget.button.client.Toolbar;
import stroom.widget.button.client.ToolbarButton;
import stroom.widget.spinner.client.LargeBusySpinner;
import stroom.widget.task.client.TaskMonitor;
import stroom.widget.task.client.TaskMonitorFactory;
import stroom.widget.text.client.Label;
import stroom.widget.util.client.ElementUtil;
import stroom.widget.util.client.MouseUtil;
import stroom.widget.util.client.Rect;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.DivElement;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.EventTarget;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.dom.client.DoubleClickEvent;
import com.google.gwt.event.dom.client.MouseDownEvent;
import com.google.gwt.event.dom.client.MouseDownHandler;
import com.google.gwt.event.dom.client.MouseMoveEvent;
import com.google.gwt.event.dom.client.MouseMoveHandler;
import com.google.gwt.event.dom.client.MouseUpEvent;
import com.google.gwt.event.dom.client.MouseUpHandler;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.Event.NativePreviewEvent;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.RequiresResize;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.web.bindery.event.shared.HandlerRegistration;

public class Dialog extends AbstractPopupPanel implements TaskMonitorFactory {

    private static final Binder BINDER = GWT.create(Binder.class);
    // The ways it can be resized, which its handles show (dialog.css)
    private static final String RESIZE_WIDTH = "dialog--resize-width";
    private static final String RESIZE_HEIGHT = "dialog--resize-height";

    @UiField
    Icon icon;
    @UiField
    Label titleText;
    @UiField
    SimplePanel content;
    @UiField
    SimplePanel resizeN;
    @UiField
    SimplePanel resizeE;
    @UiField
    SimplePanel resizeS;
    @UiField
    SimplePanel resizeW;
    @UiField
    SimplePanel resizeNE;
    @UiField
    SimplePanel resizeNW;
    @UiField
    SimplePanel resizeSE;
    @UiField
    SimplePanel resizeSW;
    @UiField
    LargeBusySpinner spinner;
    @UiField
    FlowPanel titleBar;
    @UiField
    DivElement hint;
    @UiField
    Toolbar windowToolbar;
    @UiField
    ToolbarButton windowButton;

    // Moves the dialog without dragging it, and resizes it if it can be, from its window options
    // button
    private final DialogWindow window;

    // Its size and how it can be resized, or null if it is of a fixed size
    private final PopupSize popupSize;
    private boolean dragging;
    private boolean resizeWidth;
    private boolean resizeHeight;
    private boolean moveX;
    private boolean moveY;
    private int dragStartX;
    private int dragStartY;
    private Rect dragStartWindow;
    private int initialWindowWidth;
    private int initialWindowHeight;
    private HandlerRegistration resizeHandlerRegistration;

    private Element dialogContent;
    private Element dialogButtons;

    /// Makes a modal dialog of a fixed size. It should not be shown until its content has been set
    /// with [#setContent(Widget)]; [#setOnAction] tells its owner when it is asked to close or for
    /// OK. Its markup is `Dialog.ui.xml`.
    public Dialog() {
        this(null);
    }

    /// Makes a modal dialog that can be resized as its size says, or of a fixed size if it has
    /// none. It should not be shown until its content has been set with [#setContent(Widget)];
    /// [#setOnAction] tells its owner when it is asked to close or for OK. Its markup is
    /// `Dialog.ui.xml`.
    ///
    /// @param popupSize Its size, and whether and how far each way it can be resized, or null for
    ///                  a dialog of a fixed size (no resize handles, and no Resize or Maximise).
    public Dialog(final PopupSize popupSize) {
        this.popupSize = popupSize;

        setModal(true);
        setStyleName("dialog");
        if (popupSize != null) {
            addStyleName("dialog--resizable");
        }
        setWidget(BINDER.createAndBindUi(this));
        PopupUtil.setDialogRole(getElement(), titleText.getElement());
        window = new DialogWindow(this, popupSize, windowButton, hint);
        if (popupSize != null) {
            // A double click on the title bar maximises the dialog, or restores it
            titleBar.addDomHandler(event -> {
                final EventTarget target = event.getNativeEvent().getEventTarget();
                if (!Element.is(target) || !windowToolbar.getElement().isOrHasChild(Element.as(target))) {
                    window.toggleMaximise();
                }
            }, DoubleClickEvent.getType());
        }

        initialWindowWidth = Window.getClientWidth();
        initialWindowHeight = Window.getClientHeight();

        final MouseHandler mouseHandler = new MouseHandler();
        addDomHandler(mouseHandler, MouseDownEvent.getType());
        addDomHandler(mouseHandler, MouseUpEvent.getType());
        addDomHandler(mouseHandler, MouseMoveEvent.getType());

        setResizeEnabled(popupSize != null && popupSize.getWidth() != null && popupSize.getWidth().isResizable(),
                popupSize != null && popupSize.getHeight() != null && popupSize.getHeight().isResizable());
    }

    @Override
    public void setIcon(final SvgImage icon) {
        this.icon.setImage(icon);
    }

    @Override
    public void setCaption(final String text) {
        titleText.setText(text);
    }

    @Override
    public void setContent(final Widget widget) {
        content.setWidget(widget);
    }

    @Override
    public void show() {
        if (popupSize != null && resizeHandlerRegistration == null) {
            resizeHandlerRegistration = Window.addResizeHandler(event -> {
                initialWindowWidth = event.getWidth();
                initialWindowHeight = event.getHeight();
            });
        }
        super.show();
    }

    @Override
    public void forceHide(final boolean autoClosed) {
        if (resizeHandlerRegistration != null) {
            resizeHandlerRegistration.removeHandler();
            resizeHandlerRegistration = null;
        }
        super.hide(autoClosed);
    }

    /**
     * This is overridden as we don't want popups to hide without the controller
     * to get a chance to do something about it. When hide occurs the controller
     * will be notified via the event and will then choose whether or not to
     * force the popup to hide.
     */
    @Override
    public void hide(final boolean autoClosed) {
        fireAction(autoClosed
                ? DialogAction.AUTO_CLOSE
                : DialogAction.CLOSE);
    }

    @Override
    public void onBrowserEvent(final Event event) {
        // If we're not yet dragging, only trigger mouse events if the event
        // occurs in the caption wrapper.
        switch (event.getTypeInt()) {
            case Event.ONMOUSEDOWN:
            case Event.ONMOUSEUP:
            case Event.ONMOUSEMOVE:
            case Event.ONMOUSEOVER:
            case Event.ONMOUSEOUT:
                if (!dragging && !isCaptionEvent(event) && !isResizeHandleEvent(event)) {
                    return;
                }
                break;
            default:
                // Don't care about all mouse events
        }

        super.onBrowserEvent(event);
    }

    // Recovers a drag whose mouse-up was never delivered, which otherwise leaves a full-viewport
    // drag glass swallowing every click. See AbstractPopupPanel.abandonDrag.

    /**
     * {@inheritDoc}
     *
     * <p>Also clears this dialog's own drag state and releases the mouse capture, so an abandoned
     * drag cannot leave the dialog believing it is still being dragged — which would keep
     * {@code onBrowserEvent} forwarding every mouse event in the document to it.</p>
     */
    @Override
    protected void abandonDrag() {
        if (dragging) {
            dragging = false;
            DOM.releaseCapture(getElement());
        }
        super.abandonDrag();
    }

    /**
     * Called on mouse down in the caption area, begins the dragging loop by
     * turning on event capture.
     *
     * @param event the mouse down event that triggered dragging
     * @see DOM#setCapture
     * @see #continueDragging
     */
    private void beginDragging(final MouseDownEvent event) {
        window.beforeMouseChange();
        getDragGlass().show();

        dragging = true;
        dragStartX = event.getClientX();
        dragStartY = event.getClientY();
        dragStartWindow = new Rect(getElement());
        resizeWidth = false;
        resizeHeight = false;
        moveX = false;
        moveY = false;

        final EventTarget target = event.getNativeEvent().getEventTarget();
        if (popupSize != null && Element.is(target)) {
            final Element element = Element.as(target);
            if (resizeN.getElement().isOrHasChild(element)) {
                resizeHeight = true;
                moveY = true;
            } else if (resizeE.getElement().isOrHasChild(element)) {
                resizeWidth = true;
            } else if (resizeS.getElement().isOrHasChild(element)) {
                resizeHeight = true;
            } else if (resizeW.getElement().isOrHasChild(element)) {
                resizeWidth = true;
                moveX = true;
            } else if (resizeNE.getElement().isOrHasChild(element)) {
                resizeWidth = true;
                resizeHeight = true;
                moveY = true;
            } else if (resizeNW.getElement().isOrHasChild(element)) {
                resizeWidth = true;
                resizeHeight = true;
                moveX = true;
                moveY = true;
            } else if (resizeSE.getElement().isOrHasChild(element)) {
                resizeWidth = true;
                resizeHeight = true;
            } else if (resizeSW.getElement().isOrHasChild(element)) {
                resizeWidth = true;
                resizeHeight = true;
                moveX = true;
            }
        }

        // If we aren't resizing we are moving.
        if (!resizeWidth && !resizeHeight) {
            moveX = true;
            moveY = true;
        }

        DOM.setCapture(getElement());
    }

    /**
     * Called on mouse move in the caption area, continues dragging if it was
     * started by {@link #beginDragging}.
     *
     * @param event the mouse move event that continues dragging
     * @see #beginDragging
     * @see #endDragging
     */
    private void continueDragging(final MouseMoveEvent event) {
        // The release may have happened where nothing could see it: the browser delivers no event
        // at all for a mouse-up outside its own window. A move with no button held is the first
        // evidence, and without acting on it the glass stays up for the life of the page.
        if (dragging && !isButtonHeld(event.getNativeEvent())) {
            abandonDrag();
            return;
        }
        if (dragging) {
            final int x = event.getClientX();
            final int y = event.getClientY();
            double dx = x - dragStartX;
            double dy = y - dragStartY;

            if (resizeWidth || resizeHeight) {
                final Widget widget = getWidget();
                final Element elem = widget.getElement();
                final Size widthSize = popupSize.getWidth();
                final Size heightSize = popupSize.getHeight();

                if (resizeWidth && widthSize != null && widthSize.isResizable()) {
                    final double newWidth = moveX
                            ? dragStartWindow.getWidth() - dx
                            : dragStartWindow.getWidth() + dx;
                    double constrainedWidth = newWidth;

                    // Constrain width.
                    if (widthSize.getMin() != null) {
                        constrainedWidth = Math.max(constrainedWidth, widthSize.getMin());
                    }
                    if (widthSize.getMax() != null) {
                        constrainedWidth = Math.min(constrainedWidth, widthSize.getMax());
                    }
                    // Add window based size constraint.
                    constrainedWidth = Math.min(initialWindowWidth, constrainedWidth);
                    // The edge being dragged stops at the window's edge
                    constrainedWidth = Math.min(maxWidthInWindow(moveX), constrainedWidth);

                    // If we were constrained then reduce the move distance.
                    final double constraintDiff = newWidth - constrainedWidth;
                    dx = dx + constraintDiff;

                    DialogWindow.setWidth(elem, (int) constrainedWidth);
                }
                if (resizeHeight && heightSize != null && heightSize.isResizable()) {
                    final double newHeight = moveY
                            ? dragStartWindow.getHeight() - dy
                            : dragStartWindow.getHeight() + dy;
                    double constrainedHeight = newHeight;

                    // Constrain height.
                    if (heightSize.getMin() != null) {
                        constrainedHeight = Math.max(constrainedHeight, heightSize.getMin());
                    }
                    if (heightSize.getMax() != null) {
                        constrainedHeight = Math.min(constrainedHeight, heightSize.getMax());
                    }
                    // Add window based size constraint.
                    constrainedHeight = Math.min(initialWindowHeight, constrainedHeight);
                    // The edge being dragged stops at the window's edge
                    constrainedHeight = Math.min(maxHeightInWindow(moveY), constrainedHeight);

                    // If we were constrained then reduce the move distance.
                    final double constraintDiff = newHeight - constrainedHeight;
                    dy = dy + constraintDiff;

                    DialogWindow.setHeight(elem, (int) constrainedHeight);
                }

                // Resize popup contents.
                if (widget instanceof RequiresResize) {
                    final RequiresResize requiresResize = (RequiresResize) widget;
                    requiresResize.onResize();
                }
                window.afterMouseChange();
            }

            if (moveX || moveY) {
                double left = dragStartWindow.getLeft();
                double top = dragStartWindow.getTop();
                if (moveX) {
                    left = left + dx;
                }
                if (moveY) {
                    top = top + dy;
                }

                // Don't constrain if we are resizing.
                if (!resizeWidth && !resizeHeight) {
                    left = Math.max(0, Math.min(Window.getClientWidth() - 22, left));
                    top = Math.max(0, Math.min(Window.getClientHeight() - 22, top));
                }

                setPopupPosition((int) left, (int) top);
                window.afterMouseChange();
            }
        }
    }

    /**
     * Called on mouse up in the caption area, ends dragging by ending event
     * capture.
     *
     * @param event the mouse up event that ended dragging
     * @see DOM#releaseCapture
     * @see #beginDragging
     * @see #endDragging
     */
    private void endDragging(final MouseUpEvent event) {
        dragging = false;
        DOM.releaseCapture(getElement());

        getDragGlass().hide();
    }

    @Override
    protected void onPreviewNativeEvent(final NativePreviewEvent event) {
        // We need to preventDefault() on mouseDown events (outside of the
        // DialogBox content) to keep text from being selected when it
        // is dragged.
        final NativeEvent nativeEvent = event.getNativeEvent();

        if (!event.isCanceled() && (event.getTypeInt() == Event.ONMOUSEDOWN)
            && (isCaptionEvent(nativeEvent) || isResizeHandleEvent(nativeEvent))) {
            nativeEvent.preventDefault();
        }

        super.onPreviewNativeEvent(event);
    }

    private boolean isCaptionEvent(final NativeEvent event) {
        // Lazy init content elements.
        if (dialogContent == null) {
            dialogContent = ElementUtil.findChild(content.getElement(), "dialog__content");
            dialogButtons = ElementUtil.findChild(content.getElement(), "dialog__buttons");
        }

        final EventTarget target = event.getEventTarget();
        if (Element.is(target)) {
            final Element element = Element.as(target);
            // Check the event targets the dialog.
            if (getElement().isOrHasChild(element)) {
                // The window options button is pressed, not dragged
                if (windowToolbar.getElement().isOrHasChild(element)) {
                    return false;
                }
                // If the target is dialog content then ensure the event does not target child elements.
                if (dialogContent != null && dialogContent.isOrHasChild(element)) {
                    return dialogContent.equals(element);
                }
                // If the target is dialog buttons then ensure the event does not target child elements.
                if (dialogButtons != null && dialogButtons.isOrHasChild(element)) {
                    return dialogButtons.equals(element);
                }
                return true;
            }
        }
        return false;
    }

    // (A dialog of a fixed size has its handles, but they are never shown, so never pressed)
    private boolean isResizeHandleEvent(final NativeEvent event) {
        final EventTarget target = event.getEventTarget();
        if (popupSize != null && Element.is(target)) {
            final Element element = Element.as(target);
            return resizeN.getElement().isOrHasChild(element) ||
                   resizeE.getElement().isOrHasChild(element) ||
                   resizeS.getElement().isOrHasChild(element) ||
                   resizeW.getElement().isOrHasChild(element) ||
                   resizeNE.getElement().isOrHasChild(element) ||
                   resizeNW.getElement().isOrHasChild(element) ||
                   resizeSE.getElement().isOrHasChild(element) ||
                   resizeSW.getElement().isOrHasChild(element);
        }
        return false;
    }

    // The widest the dialog can be made by dragging an edge without that edge going past the window's
    // (the left edge if `fromLeft`, else the right). A dialog can be moved partly off the window, but
    // not resized off it: growing past the window shows nothing more, and puts the handle needed to
    // shrink it again out of reach. A dialog already wider than that isn't made smaller by it.
    private double maxWidthInWindow(final boolean fromLeft) {
        final double windowLeft = Window.getScrollLeft();
        final double room = fromLeft
                ? dragStartWindow.getRight() - windowLeft
                : windowLeft + Window.getClientWidth() - dragStartWindow.getLeft();
        return Math.max(dragStartWindow.getWidth(), room);
    }

    // The tallest the dialog can be made by dragging an edge without that edge going past the window's
    // (the top edge if `fromTop`, else the bottom); see maxWidthInWindow
    private double maxHeightInWindow(final boolean fromTop) {
        final double windowTop = Window.getScrollTop();
        final double room = fromTop
                ? dragStartWindow.getBottom() - windowTop
                : windowTop + Window.getClientHeight() - dragStartWindow.getTop();
        return Math.max(dragStartWindow.getHeight(), room);
    }

    // Only the handles for the ways the dialog can be resized are there, so none offers a resize it
    // can't do (a top edge on a dialog that only widens once moved it up and down instead)
    // (dialog.css: a corner resizes both ways, so is there only if both can be)
    private void setResizeEnabled(final boolean width, final boolean height) {
        setStyleName(RESIZE_WIDTH, width);
        setStyleName(RESIZE_HEIGHT, height);
    }

    @Override
    public TaskMonitor createTaskMonitor() {
        return spinner.createTaskMonitor();
    }

    interface Binder extends UiBinder<Widget, Dialog> {

    }


    // --------------------------------------------------------------------------------


    private class MouseHandler implements MouseDownHandler, MouseUpHandler, MouseMoveHandler {

        @Override
        public void onMouseDown(final MouseDownEvent event) {
            if (MouseUtil.isPrimary(event)) {
                beginDragging(event);
            }
        }

        @Override
        public void onMouseMove(final MouseMoveEvent event) {
            continueDragging(event);
        }

        @Override
        public void onMouseUp(final MouseUpEvent event) {
            endDragging(event);
        }
    }
}
