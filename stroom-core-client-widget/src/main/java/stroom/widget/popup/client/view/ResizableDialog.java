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

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.EventTarget;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.dom.client.MouseDownEvent;
import com.google.gwt.event.dom.client.MouseDownHandler;
import com.google.gwt.event.dom.client.MouseMoveEvent;
import com.google.gwt.event.dom.client.MouseMoveHandler;
import com.google.gwt.event.dom.client.MouseUpEvent;
import com.google.gwt.event.dom.client.MouseUpHandler;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.resources.client.ImageResource;
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
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupUiHandlers;

public class ResizableDialog extends AbstractPopupPanel {
    private static Binder binder = GWT.create(Binder.class);

    public interface Binder extends UiBinder<Widget, ResizableDialog> {
    }

    private enum DragType {
        MOVE, RESIZE
    }

    private class MouseHandler implements MouseDownHandler, MouseUpHandler, MouseMoveHandler {
        @Override
        public void onMouseDown(final MouseDownEvent event) {
            if ((Event.BUTTON_LEFT & event.getNativeButton()) != 0) {
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

    public interface Style extends CssResource {
        String DEFAULT_STYLE = "ResizableDialog.css";

        String popup();

        String container();

        String background();

        String content();

        String titleBar();

        String titleText();

        String resizeHandle();
    }

    public interface Resources extends ClientBundle {
        @Source("resizeBottomRight.png")
        ImageResource resizeBottomRight();

        @Source(Style.DEFAULT_STYLE)
        Style style();
    }

    private static final Resources RESOURCES = GWT.create(Resources.class);

    private DragType dragType;
    private boolean dragging;
    private int dragStartX, dragStartY;
    private int windowWidth;
    private final int clientLeft;
    private final int clientTop;

    private HandlerRegistration resizeHandlerRegistration;
    private final PopupUiHandlers popupUiHandlers;
    private final PopupSize popupSize;

    @UiField
    Label titleText;
    @UiField
    SimplePanel content;
    @UiField
    SimplePanel resizeHandle;

    @Override
    public void setCaption(final String text) {
        titleText.setText(text);
    }

    @Override
    public void setContent(final Widget widget) {
        content.setWidget(widget);
    }

    /**
     * Creates an empty dialog box. It should not be shown until its child
     * widget has been added using {@link #add(Widget)}.
     */
    public ResizableDialog(final PopupUiHandlers popupUiHandlers, final PopupSize popupSize) {
        this(popupUiHandlers, false, popupSize);
    }

    /**
     * Creates an empty dialog box specifying its "auto-hide" property. It
     * should not be shown until its child widget has been added using
     * {@link #add(Widget)}.
     *
     * @param autoHide <code>true</code> if the dialog should be automatically hidden
     *                 when the user clicks outside of it
     */
    public ResizableDialog(final PopupUiHandlers popupUiHandlers, final boolean autoHide, final PopupSize popupSize) {
        this(popupUiHandlers, autoHide, true, popupSize);
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
    public ResizableDialog(final PopupUiHandlers popupUiHandlers, final boolean autoHide, final boolean modal,
                           final PopupSize popupSize) {
        super(autoHide, modal);
        RESOURCES.style().ensureInjected();
        this.popupUiHandlers = popupUiHandlers;
        this.popupSize = popupSize;

        setStyleName(RESOURCES.style().popup());

        setWidget(binder.createAndBindUi(this));

        windowWidth = Window.getClientWidth();
        clientLeft = Document.get().getBodyOffsetLeft();
        clientTop = Document.get().getBodyOffsetTop();

        final MouseHandler mouseHandler = new MouseHandler();
        addDomHandler(mouseHandler, MouseDownEvent.getType());
        addDomHandler(mouseHandler, MouseUpEvent.getType());
        addDomHandler(mouseHandler, MouseMoveEvent.getType());

        setResizeEnabled(popupSize.isResizable());
    }

    @Override
    public void show() {
        setGlassEnabled(isModal());

        if (resizeHandlerRegistration == null) {
            resizeHandlerRegistration = Window.addResizeHandler(event -> windowWidth = event.getWidth());
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
     *
     * @param autoClosed
     * @see com.google.gwt.user.client.ui.PopupPanel#hide(boolean)
     */
    @Override
    public void hide(final boolean autoClosed) {
        popupUiHandlers.onHideRequest(autoClosed, false);
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
    protected void beginDragging(final MouseDownEvent event) {
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
    protected void continueDragging(final MouseMoveEvent event) {
        if (dragging) {
            if (dragType == DragType.MOVE) {
                final int absX = event.getX() + getAbsoluteLeft();
                final int absY = event.getY() + getAbsoluteTop();

                // If the mouse is off the screen to the left, right, or top,
                // don't move the dialog box. This would let users
                // lose dialog boxes, which would be bad for modal popups.
                if (absX < clientLeft || absX >= windowWidth || absY < clientTop) {
                    return;
                }

                setPopupPosition(absX - dragStartX, absY - dragStartY);

            } else {
                int width = event.getX() + dragStartX;
                int height = event.getY() + dragStartY;

                // Constrain width.
                if (popupSize.getMinWidth() != null && width < popupSize.getMinWidth()) {
                    width = popupSize.getMinWidth();
                } else if (popupSize.getMaxWidth() != null && width > popupSize.getMaxWidth()) {
                    width = popupSize.getMaxWidth();
                }

                // Constrain height.
                if (popupSize.getMinHeight() != null && height < popupSize.getMinHeight()) {
                    height = popupSize.getMinHeight();
                } else if (popupSize.getMaxHeight() != null && height > popupSize.getMaxHeight()) {
                    height = popupSize.getMaxHeight();
                }

                final Widget widget = getWidget();
                final Element elem = widget.getElement();
                elem.getStyle().setPropertyPx("width", width);
                elem.getStyle().setPropertyPx("height", height);

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
    protected void endDragging(final MouseUpEvent event) {
        dragging = false;
        dragType = null;
        DOM.releaseCapture(getElement());
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

    public void setResizeEnabled(final boolean enabled) {
        resizeHandle.setVisible(enabled);
    }
}
