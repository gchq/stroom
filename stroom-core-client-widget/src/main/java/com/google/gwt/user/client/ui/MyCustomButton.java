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

package com.google.gwt.user.client.ui;

import com.google.gwt.aria.client.PressedValue;
import com.google.gwt.aria.client.Roles;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.safehtml.client.HasSafeHtml;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Event;

public abstract class MyCustomButton extends ButtonBase {

    private static final String STYLENAME_DEFAULT = "gwt-CustomButton";
    /**
     * Pressed Attribute bit.
     */
    private static final int DOWN_ATTRIBUTE = 1;
    /**
     * Hovering Attribute bit.
     */
    private static final int HOVERING_ATTRIBUTE = 2;
    /**
     * Disabled Attribute bit.
     */
    private static final int DISABLED_ATTRIBUTE = 4;
    /**
     * ID for up face.
     */
    private static final int UP = 0;
    /**
     * ID for down face.
     */
    private static final int DOWN = DOWN_ATTRIBUTE;
    /**
     * ID for upHovering face.
     */
    private static final int UP_HOVERING = HOVERING_ATTRIBUTE;
    /**
     * ID for downHovering face.
     */
    private static final int DOWN_HOVERING = DOWN_ATTRIBUTE | HOVERING_ATTRIBUTE;
    /**
     * ID for upDisabled face.
     */
    private static final int UP_DISABLED = DISABLED_ATTRIBUTE;
    /**
     * ID for downDisabled face.
     */
    private static final int DOWN_DISABLED = DOWN | DISABLED_ATTRIBUTE;
    /**
     * The button's current face element.
     */
    private Element curFaceElement;
    /**
     * The button's current face.
     */
    private Face curFace;
    /**
     * Face for up.
     */
    private Face up;
    /**
     * Face for down.
     */
    private Face down;
    /**
     * Face for downHover.
     */
    private Face downHovering;
    /**
     * Face for upHover.
     */
    private Face upHovering;
    /**
     * Face for upDisabled.
     */
    private Face upDisabled;
    /**
     * Face for downDisabled.
     */
    private Face downDisabled;
    /**
     * If <code>true</code>, this widget is capturing with the mouse held down.
     */
    private boolean isCapturing;
    /**
     * If <code>true</code>, this widget has focus with the space bar down.
     */
    private boolean isFocusing;
    /**
     * Used to decide whether to allow clicks to propagate up to the superclass
     * or container elements.
     */
    private boolean allowClick;

    /**
     * Constructor for <code>CustomButton</code>.
     *
     * @param upImage image for the default (up) face of the button
     */
    public MyCustomButton(final Image upImage) {
        this();
        getUpFace().setImage(upImage);
    }

    /**
     * Constructor for <code>CustomButton</code>.
     *
     * @param upImage image for the default (up) face of the button
     * @param handler the click handler
     */
    public MyCustomButton(final Image upImage, final ClickHandler handler) {
        this(upImage);
        addClickHandler(handler);
    }

    /**
     * Constructor for <code>CustomButton</code>.
     *
     * @param upImage   image for the default (up) face of the button
     * @param downImage image for the down face of the button
     */
    public MyCustomButton(final Image upImage, final Image downImage) {
        this(upImage);
        getDownFace().setImage(downImage);
    }

    /**
     * Constructor for <code>CustomButton</code>.
     *
     * @param upImage   image for the default (up) face of the button
     * @param downImage image for the down face of the button
     * @param handler   clickListener
     */
    public MyCustomButton(final Image upImage, final Image downImage, final ClickHandler handler) {
        this(upImage, handler);
        getDownFace().setImage(downImage);
    }

    /**
     * Constructor for <code>CustomButton</code>.
     *
     * @param upText the text for the default (up) face of the button
     */
    public MyCustomButton(final String upText) {
        this();
        getUpFace().setText(upText);
    }

    /**
     * Constructor for <code>CustomButton</code>.
     *
     * @param upText  the text for the default (up) face of the button
     * @param handler the click handler
     */
    public MyCustomButton(final String upText, final ClickHandler handler) {
        this(upText);
        addClickHandler(handler);
    }

    /**
     * Constructor for <code>CustomButton</code>.
     *
     * @param upText   the text for the default (up) face of the button
     * @param downText the text for the down face of the button
     */
    public MyCustomButton(final String upText, final String downText) {
        this(upText);
        getDownFace().setText(downText);
    }

    /**
     * Constructor for <code>CustomButton</code>.
     *
     * @param upText   the text for the default (up) face of the button
     * @param downText the text for the down face of the button
     * @param handler  the click handler
     */
    public MyCustomButton(final String upText, final String downText, final ClickHandler handler) {
        this(upText, downText);
        addClickHandler(handler);
    }

    /**
     * Constructor for <code>CustomButton</code>.
     */
    protected MyCustomButton() {
        // Use FocusPanel.impl rather than FocusWidget because only
        // FocusPanel.impl
        // works across browsers to create a focusable element.
        super(FocusPanel.impl.createFocusable());
        sinkEvents(Event.ONCLICK | Event.MOUSEEVENTS | Event.FOCUSEVENTS | Event.KEYEVENTS);
        setUpFace(createFace(null, "up", UP));
        setStyleName(STYLENAME_DEFAULT);

        // Add a11y role "button"
        Roles.getButtonRole().set(getElement());
    }

    /**
     * Gets the downDisabled face of the button.
     *
     * @return the downDisabled face
     */
    public final Face getDownDisabledFace() {
        if (downDisabled == null) {
            setDownDisabledFace(createFace(getDownFace(), "down-disabled", DOWN_DISABLED));
        }
        return downDisabled;
    }

    /**
     * Sets the downDisabled face of the button.
     *
     * @param downDisabled downDisabled face
     */
    private void setDownDisabledFace(final Face downDisabled) {
        this.downDisabled = downDisabled;
    }

    /**
     * Gets the down face of the button.
     *
     * @return the down face
     */
    public final Face getDownFace() {
        if (down == null) {
            setDownFace(createFace(getUpFace(), "down", DOWN));
        }
        return down;
    }

    /**
     * Sets the down face of the button.
     *
     * @param down the down face
     */
    private void setDownFace(final Face down) {
        this.down = down;
    }

    /**
     * Gets the downHovering face of the button.
     *
     * @return the downHovering face
     */
    public final Face getDownHoveringFace() {
        if (downHovering == null) {
            setDownHoveringFace(createFace(getDownFace(), "down-hovering", DOWN_HOVERING));
        }
        return downHovering;
    }

    /**
     * Sets the downHovering face of the button.
     *
     * @param downHovering hoverDown face
     */
    private void setDownHoveringFace(final Face downHovering) {
        this.downHovering = downHovering;
    }

    /**
     * Gets the current face's html.
     *
     * @return current face's html
     */
    @Override
    public String getHTML() {
        return getCurrentFace().getHTML();
    }

    /**
     * Sets the current face's html.
     *
     * @param html html to set
     */
    @Override
    public void setHTML(final String html) {
        getCurrentFace().setHTML(html);
    }

    @Override
    public int getTabIndex() {
        return FocusPanel.impl.getTabIndex(getElement());
    }

    @Override
    public void setTabIndex(final int index) {
        FocusPanel.impl.setTabIndex(getElement(), index);
    }

    /**
     * Gets the current face's text.
     *
     * @return current face's text
     */
    @Override
    public String getText() {
        return getCurrentFace().getText();
    }

    /**
     * Sets the current face's text.
     *
     * @param text text to set
     */
    @Override
    public void setText(final String text) {
        getCurrentFace().setText(text);
    }

    /**
     * Gets the upDisabled face of the button.
     *
     * @return the upDisabled face
     */
    public final Face getUpDisabledFace() {
        if (upDisabled == null) {
            setUpDisabledFace(createFace(getUpFace(), "up-disabled", UP_DISABLED));
        }
        return upDisabled;
    }

    /**
     * Sets the upDisabled face of the button.
     *
     * @param upDisabled upDisabled face
     */
    private void setUpDisabledFace(final Face upDisabled) {
        this.upDisabled = upDisabled;
    }

    /**
     * Gets the up face of the button.
     *
     * @return the up face
     */
    public final Face getUpFace() {
        return up;
    }

    /**
     * Sets the up face of the button.
     *
     * @param up up face
     */
    private void setUpFace(final Face up) {
        this.up = up;
    }

    /**
     * Gets the upHovering face of the button.
     *
     * @return the upHovering face
     */
    public final Face getUpHoveringFace() {
        if (upHovering == null) {
            setUpHoveringFace(createFace(getUpFace(), "up-hovering", UP_HOVERING));
        }
        return upHovering;
    }

    /**
     * Sets the upHovering face of the button.
     *
     * @param upHovering upHovering face
     */
    private void setUpHoveringFace(final Face upHovering) {
        this.upHovering = upHovering;
    }

    @Override
    public void onBrowserEvent(final Event event) {
        // Should not act on button if disabled.
        if (isEnabled() == false) {
            // This can happen when events are bubbled up from non-disabled
            // children
            return;
        }

        final int type = DOM.eventGetType(event);
        switch (type) {
            case Event.ONCLICK:
                // If clicks are currently disallowed, keep it from bubbling or
                // being
                // passed to the superclass.
                if (!allowClick) {
                    event.stopPropagation();
                    return;
                }
                break;
            case Event.ONMOUSEDOWN:
                if (event.getButton() == Event.BUTTON_LEFT) {
                    setFocus(true);
                    onClickStart();
                    DOM.setCapture(getElement());
                    isCapturing = true;
                    // Prevent dragging (on some browsers);
                    event.preventDefault();
                }
                break;
            case Event.ONMOUSEUP:
                if (isCapturing) {
                    isCapturing = false;
                    DOM.releaseCapture(getElement());
                    if (isHovering() && event.getButton() == Event.BUTTON_LEFT) {
                        onClick();
                    }
                }
                break;
            case Event.ONMOUSEMOVE:
                if (isCapturing) {
                    // Prevent dragging (on other browsers);
                    event.preventDefault();
                }
                break;
            case Event.ONMOUSEOUT:
                final Element to = DOM.eventGetToElement(event);
                if (getElement().isOrHasChild(DOM.eventGetTarget(event))
                        && (to == null || !getElement().isOrHasChild(to))) {
                    if (isCapturing) {
                        onClickCancel();
                    }
                    setHovering(false);
                }
                break;
            case Event.ONMOUSEOVER:
                if (getElement().isOrHasChild(DOM.eventGetTarget(event))) {
                    setHovering(true);
                    if (isCapturing) {
                        onClickStart();
                    }
                }
                break;
            case Event.ONBLUR:
                if (isFocusing) {
                    isFocusing = false;
                    onClickCancel();
                }
                break;
            case Event.ONLOSECAPTURE:
                if (isCapturing) {
                    isCapturing = false;
                    onClickCancel();
                }
                break;
        }

        super.onBrowserEvent(event);

        // Synthesize clicks based on keyboard events AFTER the normal key
        // handling.
        if ((event.getTypeInt() & Event.KEYEVENTS) != 0) {
            final char keyCode = (char) event.getKeyCode();
            switch (type) {
                case Event.ONKEYDOWN:
                    if (keyCode == ' ') {
                        isFocusing = true;
                        onClickStart();
                    }
                    break;
                case Event.ONKEYUP:
                    if (isFocusing && keyCode == ' ') {
                        isFocusing = false;
                        onClick();
                    }
                    break;
                case Event.ONKEYPRESS:
                    if (keyCode == '\n' || keyCode == '\r') {
                        onClickStart();
                        onClick();
                    }
                    break;
            }
        }
    }

    @Override
    public void setAccessKey(final char key) {
        FocusPanel.impl.setAccessKey(getElement(), key);
    }

    /**
     * Sets whether this button is enabled.
     *
     * @param enabled <code>true</code> to enable the button, <code>false</code> to
     *                disable it
     */
    @Override
    public final void setEnabled(final boolean enabled) {
        if (isEnabled() != enabled) {
            toggleDisabled();
            super.setEnabled(enabled);
            if (!enabled) {
                cleanupCaptureState();
                Roles.getButtonRole().removeAriaPressedState(getElement());
            } else {
                setAriaPressed(getCurrentFace());
            }
        }
    }

    @Override
    public void setFocus(final boolean focused) {
        if (focused) {
            FocusPanel.impl.focus(getElement());
        } else {
            FocusPanel.impl.blur(getElement());
        }
    }

    @Override
    public void setHTML(final SafeHtml html) {
        setHTML(html.asString());
    }

    /**
     * Is this button down?
     *
     * @return <code>true</code> if the button is down
     */
    protected boolean isDown() {
        return (DOWN_ATTRIBUTE & getCurrentFace().getFaceID()) > 0;
    }

    /**
     * Sets whether this button is down.
     *
     * @param down <code>true</code> to press the button, <code>false</code>
     *             otherwise
     */
    protected void setDown(final boolean down) {
        if (down != isDown()) {
            toggleDown();
        }
    }

    /**
     * Overridden on attach to ensure that a button face has been chosen before
     * the button is displayed.
     */
    @Override
    protected void onAttach() {
        finishSetup();
        super.onAttach();
    }

    /**
     * Called when the user finishes clicking on this button. The default
     * behavior is to fire the click event to listeners. Subclasses that
     * override {@link #onClickStart()} should override this method to restore
     * the normal widget display.
     */
    protected void onClick() {
        // Allow the click we're about to synthesize to pass through to the
        // superclass and containing elements. Element.dispatchEvent() is
        // synchronous, so we simply set and clear the flag within this method.
        allowClick = true;

        // Mouse coordinates are not always available (e.g., when the click is
        // caused by a keyboard event).
        final NativeEvent evt = Document.get().createClickEvent(1, 0, 0, 0, 0, false, false, false, false);
        getElement().dispatchEvent(evt);

        allowClick = false;
    }

    /**
     * Called when the user aborts a click in progress; for example, by dragging
     * the mouse outside of the button before releasing the mouse button.
     * Subclasses that override {@link #onClickStart()} should override this
     * method to restore the normal widget display.
     */
    protected void onClickCancel() {
    }

    /**
     * Called when the user begins to click on this button. Subclasses may
     * override this method to display the start of the click visually; such
     * subclasses should also override {@link #onClick()} and
     * {@link #onClickCancel()} to restore normal visual state. Each
     * <code>onClickStart</code> will eventually be followed by either
     * <code>onClick</code> or <code>onClickCancel</code>, depending on whether
     * the click is completed.
     */
    protected void onClickStart() {
    }

    @Override
    protected void onDetach() {
        super.onDetach();
        cleanupCaptureState();
        setHovering(false);
    }

    /**
     * Common setup between constructors.
     */
    void finishSetup() {
        if (curFace == null) {
            setCurrentFace(getUpFace());
        }
    }

    void fireClickListeners(final Event nativeEvent) {
        // TODO Once event triggering is committed, should fire a native
        // click event instead.
        fireEvent(new ClickEvent() {
        });
    }

    /**
     * Gets the current face of the button.
     *
     * @return the current face
     */

    Face getCurrentFace() {
        // Implementation note: Package protected so we can use it when testing
        // the button.
        finishSetup();
        return curFace;
    }

    /**
     * Sets the current face based on the faceID.
     *
     * @param faceID sets the new face of the button
     */
    private void setCurrentFace(final int faceID) {
        final Face newFace = getFaceFromID(faceID);
        setCurrentFace(newFace);
    }

    /**
     * Is the mouse hovering over this button?
     *
     * @return <code>true</code> if the mouse is hovering
     */
    final boolean isHovering() {
        return (HOVERING_ATTRIBUTE & getCurrentFace().getFaceID()) > 0;
    }

    /**
     * Sets whether this button is hovering.
     *
     * @param hovering is this button hovering?
     */
    final void setHovering(final boolean hovering) {
        if (hovering != isHovering()) {
            toggleHover();
        }
    }

    void setCurrentFace(final Face newFace) {
        // Implementation note: default access for testing.
        if (curFace != newFace) {
            if (curFace != null) {
                removeStyleDependentName(curFace.getName());
            }
            curFace = newFace;
            setCurrentFaceElement(newFace.getFace());
            addStyleDependentName(curFace.getName());

            if (isEnabled()) {
                setAriaPressed(newFace);
            }
        }
    }

    /**
     * Toggle the up/down attribute.
     */
    void toggleDown() {
        final int newFaceID = getCurrentFace().getFaceID() ^ DOWN_ATTRIBUTE;
        setCurrentFace(newFaceID);
    }

    /**
     * Resets internal state if this button can no longer service events. This
     * can occur when the widget becomes detached or disabled.
     */
    private void cleanupCaptureState() {
        if (isCapturing || isFocusing) {
            DOM.releaseCapture(getElement());
            isCapturing = false;
            isFocusing = false;
            onClickCancel();
        }
    }

    private Face createFace(final Face delegateTo, final String name, final int faceID) {
        return new Face(delegateTo) {
            @Override
            public String getName() {
                return name;
            }

            @Override
            int getFaceID() {
                return faceID;
            }
        };
    }

    private Face getFaceFromID(final int id) {
        switch (id) {
            case DOWN:
                return getDownFace();
            case UP:
                return getUpFace();
            case DOWN_HOVERING:
                return getDownHoveringFace();
            case UP_HOVERING:
                return getUpHoveringFace();
            case UP_DISABLED:
                return getUpDisabledFace();
            case DOWN_DISABLED:
                return getDownDisabledFace();
            default:
                throw new IllegalStateException(id + " is not a known face id.");
        }
    }

    private void setAriaPressed(final Face newFace) {
        final boolean pressed = (newFace.getFaceID() & DOWN_ATTRIBUTE) == 1;
        Roles.getButtonRole().setAriaPressedState(getElement(), PressedValue.of(pressed));
    }

    private void setCurrentFaceElement(final Element newFaceElement) {
        if (curFaceElement != newFaceElement) {
            if (curFaceElement != null) {
                getElement().removeChild(curFaceElement);
            }
            curFaceElement = newFaceElement;
            DOM.appendChild(getElement(), curFaceElement);
        }
    }

    /**
     * Toggle the disabled attribute.
     */
    private void toggleDisabled() {
        // Toggle disabled.
        int newFaceID = getCurrentFace().getFaceID() ^ DISABLED_ATTRIBUTE;

        // Remove hovering.
        newFaceID &= ~HOVERING_ATTRIBUTE;

        // Sets the current face.
        setCurrentFace(newFaceID);
    }

    /**
     * Toggle the hovering attribute.
     */
    private void toggleHover() {
        // Toggle hovering.
        int newFaceID = getCurrentFace().getFaceID() ^ HOVERING_ATTRIBUTE;

        // Remove disabled.
        newFaceID &= ~DISABLED_ATTRIBUTE;
        setCurrentFace(newFaceID);
    }

    /**
     * Represents a button's face. Each face is associated with its own style
     * modifier and, optionally, its own contents html, text, or image.
     */
    public abstract class Face implements HasHTML, HasSafeHtml {

        private static final String STYLENAME_HTML_FACE = "html-face";
        private final Face delegateTo;
        private Element face;

        /**
         * Constructor for <code>Face</code>. Creates a new face that delegates
         * to the supplied face.
         *
         * @param delegateTo default content provider
         */
        private Face(final Face delegateTo) {
            this.delegateTo = delegateTo;
        }

        /**
         * Gets the face's contents as html.
         *
         * @return face's contents as html
         */
        @Override
        public String getHTML() {
            return getFace().getInnerHTML();
        }

        /**
         * Set the face's contents as html.
         *
         * @param html html to set as face's contents html
         */
        @Override
        public void setHTML(final String html) {
            face = DOM.createDiv();
            UIObject.setStyleName(face, STYLENAME_HTML_FACE, true);
            face.setInnerHTML(html);
            updateButtonFace();
        }

        /**
         * Gets the face's contents as text.
         *
         * @return face's contents as text
         */
        @Override
        public String getText() {
            return getFace().getInnerText();
        }

        /**
         * Sets the face's contents as text.
         *
         * @param text text to set as face's contents
         */
        @Override
        public final void setText(final String text) {
            face = DOM.createDiv();
            UIObject.setStyleName(face, STYLENAME_HTML_FACE, true);
            face.setInnerText(text);
            updateButtonFace();
        }

        /**
         * Set the face's contents as html.
         *
         * @param html html to set as face's contents html
         */
        @Override
        public void setHTML(final SafeHtml html) {
            setHTML(html.asString());
        }

        /**
         * Set the face's contents as an image.
         *
         * @param image image to set as face contents
         */
        public final void setImage(final Image image) {
            face = image.getElement();
            updateButtonFace();
        }

        @Override
        public final String toString() {
            return this.getName();
        }

        /**
         * Gets the ID associated with this face. This will be a bitwise and of
         * all of the attributes that comprise this face.
         */
        abstract int getFaceID();

        /**
         * Get the name of the face. This property is also used as a modifier on
         * the <code>CustomButton</code> style.
         * <p/>
         * For instance, if the <code>CustomButton</code> style is
         * "gwt-PushButton" and the face name is "up", then the CSS class name
         * will be "gwt-PushButton-up".
         *
         * @return the face's name
         */
        abstract String getName();

        /**
         * Gets the contents associated with this face.
         */
        private Element getFace() {
            if (face == null) {
                if (delegateTo == null) {
                    // provide a default face as none was supplied.
                    face = DOM.createDiv();
                    return face;
                } else {
                    return delegateTo.getFace();
                }
            } else {
                return face;
            }
        }

        private void updateButtonFace() {
            if (curFace != null && curFace.getFace() == this.getFace()) {
                setCurrentFaceElement(face);
            }
        }
    }
}
