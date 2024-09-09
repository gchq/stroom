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
import stroom.task.client.TaskHandler;
import stroom.task.client.TaskHandlerFactory;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.Size;
import stroom.widget.spinner.client.SpinnerLarge;
import stroom.widget.util.client.MouseUtil;
import stroom.widget.util.client.SvgImageUtil;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.EventTarget;
import com.google.gwt.dom.client.NativeEvent;
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
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.RequiresResize;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.web.bindery.event.shared.HandlerRegistration;

public class ResizableDialog extends AbstractPopupPanel implements TaskHandlerFactory {

    private static final Binder binder = GWT.create(Binder.class);

    private final PopupSize popupSize;

    @UiField
    SimplePanel icon;
    @UiField
    Label titleText;
    @UiField
    SimplePanel content;
    @UiField
    SimplePanel resizeHandle;
    @UiField
    SpinnerLarge spinner;

    private DragType dragType;
    private boolean dragging;
    private int dragStartX;
    private int dragStartY;
    private int windowWidth;
    private int windowHeight;
    private HandlerRegistration resizeHandlerRegistration;

    /**
     * Creates an empty dialog box. It should not be shown until its child
     * widget has been added using {@link #add(Widget)}.
     */
    ResizableDialog(final DialogActionUiHandlers dialogEventHandler,
                    final PopupSize popupSize) {
        this(dialogEventHandler, false, popupSize);
        spinner.setSoft(true);
        spinner.setVisible(false);
    }

    /**
     * Creates an empty dialog box specifying its "auto-hide" property. It
     * should not be shown until its child widget has been added using
     * {@link #add(Widget)}.
     *
     * @param autoHide <code>true</code> if the dialog should be automatically hidden
     *                 when the user clicks outside of it
     */
    private ResizableDialog(final DialogActionUiHandlers dialogEventHandler,
                            final boolean autoHide,
                            final PopupSize popupSize) {
        this(dialogEventHandler, autoHide, true, popupSize);
    }

    /**
     * Creates an empty dialog box specifying its "auto-hide" property. It
     * should not be shown until its child widget has been added using
     * {@link #add(Widget)}.
     *
     * @param autoHide <code>true</code> if the dialog should be automatically hidden
     *                 when the user clicks outside of it
     * @param modal    <code>true</code> if keyboard and mouse events for widgets not
     *                 contained by the dialog should be ignored
     */
    private ResizableDialog(final DialogActionUiHandlers dialogEventHandler,
                            final boolean autoHide,
                            final boolean modal,
                            final PopupSize popupSize) {
        super(dialogEventHandler, autoHide, modal);
        this.popupSize = popupSize;

        setStyleName("resizableDialog-popup");
        setWidget(binder.createAndBindUi(this));

        windowWidth = Window.getClientWidth();
        windowHeight = Window.getClientHeight();

        final MouseHandler mouseHandler = new MouseHandler();
        addDomHandler(mouseHandler, MouseDownEvent.getType());
        addDomHandler(mouseHandler, MouseUpEvent.getType());
        addDomHandler(mouseHandler, MouseMoveEvent.getType());

        setResizeEnabled((popupSize != null && popupSize.getWidth() != null && popupSize.getWidth().isResizable()) ||
                (popupSize != null && popupSize.getHeight() != null && popupSize.getHeight().isResizable()));

        SvgImageUtil.setSvgAsInnerHtml(resizeHandle, SvgImage.RESIZE_HANDLE);
    }

    @Override
    public void setIcon(final SvgImage icon) {
        if (icon != null) {
            this.icon.getElement().setInnerHTML(icon.getSvg());
            if (icon.getClassName() != null) {
                this.icon.getElement().addClassName(icon.getClassName());
            }
        }
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
        if (resizeHandlerRegistration == null) {
            resizeHandlerRegistration = Window.addResizeHandler(event -> {
                windowWidth = event.getWidth();
                windowHeight = event.getHeight();
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
     * This is overridden as we don't want popups to hide without the presenter
     * to get a chance to do something about it. When hide occurs the presenter
     * will be notified via the event and will then choose whether or not to
     * force the popup to hide.
     */
    @Override
    public void hide(final boolean autoClosed) {
        if (dialogActionHandler != null) {
            if (autoClosed) {
                dialogActionHandler.onDialogAction(DialogAction.AUTO_CLOSE);
            } else {
                dialogActionHandler.onDialogAction(DialogAction.CLOSE);
            }
        }
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
        }

        super.onBrowserEvent(event);
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
        getDragGlass().show();

        dragging = true;
        if (isCaptionEvent(event.getNativeEvent())) {
            dragType = DragType.MOVE;
            dragStartX = event.getX();
            dragStartY = event.getY();
        } else {
            dragType = DragType.RESIZE;
            dragStartX = getOffsetWidth() - event.getX();
            dragStartY = getOffsetHeight() - event.getY();
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
        if (dragging) {
            final int x = event.getX();
            final int y = event.getY();

            if (dragType == DragType.MOVE) {
                final int absX = x + getAbsoluteLeft();
                final int absY = y + getAbsoluteTop();
                int left = absX - dragStartX;
                int top = absY - dragStartY;

                // Add some constraints to stop the dialog being moved off screen.
                left = Math.min(windowWidth - 22, Math.max(0, left));
                top = Math.min(windowHeight - 22, Math.max(0, top));

                setPopupPosition(left, top);

            } else {
                final Widget widget = getWidget();
                final Element elem = widget.getElement();
                final Size widthSize = popupSize.getWidth();
                final Size heightSize = popupSize.getHeight();

                if (widthSize != null && widthSize.isResizable()) {
                    int width = x + dragStartX;
                    // Constrain width.
                    if (widthSize.getMin() != null && width < widthSize.getMin()) {
                        width = widthSize.getMin();
                    } else if (widthSize.getMax() != null && width > widthSize.getMax()) {
                        width = widthSize.getMax();
                    }
                    // Add window based size constraint.
                    width = Math.min(windowWidth, width);

                    elem.getStyle().setPropertyPx("width", width);
                }
                if (heightSize != null && heightSize.isResizable()) {
                    int height = y + dragStartY;
                    // Constrain height.
                    if (heightSize.getMin() != null && height < heightSize.getMin()) {
                        height = heightSize.getMin();
                    } else if (heightSize.getMax() != null && height > heightSize.getMax()) {
                        height = heightSize.getMax();
                    }
                    // Add window based size constraint.
                    height = Math.min(windowHeight, height);

                    elem.getStyle().setPropertyPx("height", height);
                }

                if (widget instanceof RequiresResize) {
                    final RequiresResize requiresResize = (RequiresResize) widget;
                    requiresResize.onResize();
                }
            }
        }
    }

    /**
     * Called on mouse up in the caption area, ends dragging by ending event
     * capture.
     */
    private void endDragging(final MouseUpEvent event) {
        dragging = false;
        dragType = null;
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
        final EventTarget target = event.getEventTarget();
        if (Element.is(target)) {
            return titleText.getElement().isOrHasChild(Element.as(target));
        }
        return false;
    }

    private boolean isResizeHandleEvent(final NativeEvent event) {
        final EventTarget target = event.getEventTarget();
        if (Element.is(target)) {
            return resizeHandle.getElement().isOrHasChild(Element.as(target));
        }
        return false;
    }

    private void setResizeEnabled(final boolean enabled) {
        resizeHandle.setVisible(enabled);
    }

    @Override
    public TaskHandler createTaskHandler() {
        return spinner.createTaskHandler();
    }

    private enum DragType {
        MOVE,
        RESIZE
    }

    public interface Binder extends UiBinder<Widget, ResizableDialog> {

    }

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
