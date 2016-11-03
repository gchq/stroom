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
import com.google.gwt.event.logical.shared.ResizeEvent;
import com.google.gwt.event.logical.shared.ResizeHandler;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.Event.NativePreviewEvent;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.web.bindery.event.shared.HandlerRegistration;

import stroom.widget.popup.client.presenter.PopupUiHandlers;

public class Dialog extends AbstractPopupPanel {
    private static Binder binder = GWT.create(Binder.class);

    public interface Binder extends UiBinder<Widget, Dialog> {
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
        String DEFAULT_STYLE = "Dialog.css";

        String popup();

        String container();

        String background();

        String content();

        String titleBar();

        String titleText();
    }

    public interface Resources extends ClientBundle {
        @Source(Style.DEFAULT_STYLE)
        Style style();
    }

    private static final Resources RESOURCES = GWT.create(Resources.class);

    private boolean dragging;
    private int dragStartX, dragStartY;
    private int windowWidth;
    private final int clientLeft;
    private final int clientTop;

    private HandlerRegistration resizeHandlerRegistration;
    private final PopupUiHandlers popupUiHandlers;

    @UiField
    Label titleText;
    @UiField
    SimplePanel content;

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
    public Dialog(final PopupUiHandlers popupUiHandlers) {
        this(popupUiHandlers, false);
    }

    /**
     * Creates an empty dialog box specifying its "auto-hide" property. It
     * should not be shown until its child widget has been added using
     * {@link #add(Widget)}.
     *
     * @param autoHide
     *            <code>true</code> if the dialog should be automatically hidden
     *            when the user clicks outside of it
     */
    public Dialog(final PopupUiHandlers popupUiHandlers, final boolean autoHide) {
        this(popupUiHandlers, autoHide, true);
    }

    /**
     * Creates an empty dialog box specifying its "auto-hide" property. It
     * should not be shown until its child widget has been added using
     * {@link #add(Widget)}.
     *
     * @param autoHide
     *            <code>true</code> if the dialog should be automatically hidden
     *            when the user clicks outside of it
     * @param modal
     *            <code>true</code> if keyboard and mouse events for widgets not
     *            contained by the dialog should be ignored
     */
    public Dialog(final PopupUiHandlers popupUiHandlers, final boolean autoHide, final boolean modal) {
        super(autoHide, modal);
        RESOURCES.style().ensureInjected();
        this.popupUiHandlers = popupUiHandlers;

        setStyleName(RESOURCES.style().popup());
        setWidget(binder.createAndBindUi(this));

        windowWidth = Window.getClientWidth();
        clientLeft = Document.get().getBodyOffsetLeft();
        clientTop = Document.get().getBodyOffsetTop();

        final MouseHandler mouseHandler = new MouseHandler();
        addDomHandler(mouseHandler, MouseDownEvent.getType());
        addDomHandler(mouseHandler, MouseUpEvent.getType());
        addDomHandler(mouseHandler, MouseMoveEvent.getType());
    }

    @Override
    public void show() {
        setGlassEnabled(isModal());

        if (resizeHandlerRegistration == null) {
            resizeHandlerRegistration = Window.addResizeHandler(new ResizeHandler() {
                @Override
                public void onResize(final ResizeEvent event) {
                    windowWidth = event.getWidth();
                }
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
        // occurs in the caption wrapper
        switch (event.getTypeInt()) {
        case Event.ONMOUSEDOWN:
        case Event.ONMOUSEUP:
        case Event.ONMOUSEMOVE:
        case Event.ONMOUSEOVER:
        case Event.ONMOUSEOUT:
            if (!dragging && !isCaptionEvent(event)) {
                return;
            }
        }

        super.onBrowserEvent(event);
    }

    /**
     * Called on mouse down in the caption area, begins the dragging loop by
     * turning on event capture.
     *
     * @see DOM#setCapture
     * @see #continueDragging
     * @param event
     *            the mouse down event that triggered dragging
     */
    protected void beginDragging(final MouseDownEvent event) {
        dragging = true;
        DOM.setCapture(getElement());
        dragStartX = event.getX();
        dragStartY = event.getY();
    }

    /**
     * Called on mouse move in the caption area, continues dragging if it was
     * started by {@link #beginDragging}.
     *
     * @see #beginDragging
     * @see #endDragging
     * @param event
     *            the mouse move event that continues dragging
     */
    protected void continueDragging(final MouseMoveEvent event) {
        if (dragging) {
            final int absX = event.getX() + getAbsoluteLeft();
            final int absY = event.getY() + getAbsoluteTop();

            // If the mouse is off the screen to the left, right, or top, don't
            // move the dialog box. This would let users
            // lose dialog boxes, which would be bad for modal popups.
            if (absX < clientLeft || absX >= windowWidth || absY < clientTop) {
                return;
            }

            setPopupPosition(absX - dragStartX, absY - dragStartY);
        }
    }

    /**
     * Called on mouse up in the caption area, ends dragging by ending event
     * capture.
     *
     * @param event
     *            the mouse up event that ended dragging
     *
     * @see DOM#releaseCapture
     * @see #beginDragging
     * @see #endDragging
     */
    protected void endDragging(final MouseUpEvent event) {
        dragging = false;
        DOM.releaseCapture(getElement());
    }

    @Override
    protected void onPreviewNativeEvent(final NativePreviewEvent event) {
        // We need to preventDefault() on mouseDown events (outside of the
        // DialogBox content) to keep text from being
        // selected when it is dragged.
        final NativeEvent nativeEvent = event.getNativeEvent();

        if (!event.isCanceled() && (event.getTypeInt() == Event.ONMOUSEDOWN) && isCaptionEvent(nativeEvent)) {
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
}
