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

package stroom.widget.button.client;

import stroom.svg.shared.SvgImage;
import stroom.widget.tooltip.client.FocusTooltip;
import stroom.widget.util.client.KeyBinding;
import stroom.widget.util.client.KeyBinding.Action;
import stroom.widget.util.client.MouseUtil;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.ButtonElement;
import com.google.gwt.dom.client.DivElement;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiTemplate;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.ui.ButtonBase;

/// A round floating action button with an icon, e.g. a Text component's Enter Stepping Mode. The
/// button that takes the focus and is announced is the face inside its element.
public class FabButton extends ButtonBase {

    private static final Binder BINDER = GWT.create(Binder.class);

    private final Parts parts;
    private final Element face;
    private Runnable onClick;
    /**
     * If <code>true</code>, this widget is capturing with the mouse held down.
     */
    private boolean isCapturing;
    /**
     * Used to decide whether to allow clicks to propagate up to the superclass
     * or container elements.
     */
    private boolean allowClick;

    public FabButton() {
        this(new Parts());
    }

    private FabButton(final Parts parts) {
        super(BINDER.createAndBindUi(parts));
        this.parts = parts;
        face = parts.face;
        sinkEvents(Event.ONCLICK | Event.MOUSEEVENTS | Event.KEYEVENTS);
        addClickHandler(event -> {
            if (onClick != null) {
                onClick.run();
            }
        });
        // Shows the button's name when it has the keyboard focus
        FocusTooltip.install();
    }

    /// The button is the face inside this element, so only the face takes the focus: GWT makes a
    /// focus widget's element a tab stop when it is attached, which would make this two.
    @Override
    protected void onAttach() {
        super.onAttach();
        getElement().removeAttribute("tabindex");
    }

    /// @param onClick Run when the button is pressed, or null for nothing.
    public void setOnClick(final Runnable onClick) {
        this.onClick = onClick;
    }

    /// Puts the focus on the button (its face).
    public void focus() {
        face.focus();
    }

    @Override
    public void setFocus(final boolean focused) {
        if (focused) {
            face.focus();
        } else {
            face.blur();
        }
    }

    @Override
    public void setEnabled(final boolean enabled) {
        super.setEnabled(enabled);
        // The face is the real button: disabled, it stays focusable so its name and tooltip can
        // still be reached, and says it is disabled (onBrowserEvent ignores it while disabled)
        if (enabled) {
            face.removeClassName("fab-button__face--disabled");
            face.removeAttribute("aria-disabled");
        } else {
            face.addClassName("fab-button__face--disabled");
            face.setAttribute("aria-disabled", "true");
        }
    }

    /// Sets the tooltip, which also names the button for screen readers (it has only an icon).
    ///
    /// @param title The tooltip and name.
    @Override
    public void setTitle(final String title) {
        super.setTitle(title);
        if (title == null || title.isEmpty()) {
            face.removeAttribute("aria-label");
        } else {
            face.setAttribute("aria-label", title);
        }
    }

    public void setIcon(final SvgImage svgImage) {
        // The icon is decoration: the button is named by its aria-label
        parts.icon.setClassName("icon-wrap " + svgImage.getClassName());
        parts.icon.setInnerHTML(svgImage.getSvg());
    }

    @Override
    public void onBrowserEvent(final Event event) {
        // Should not act on button if disabled.
        if (!isEnabled()) {
            // This can happen when events are bubbled up from non-disabled
            // children
            isCapturing = false;
            onClickCancel();
            return;
        }

        final int type = DOM.eventGetType(event);
        switch (type) {
            case Event.ONCLICK:
                // If clicks are currently disallowed, keep it from bubbling or
                // being passed to the superclass.
                if (!allowClick) {
                    event.stopPropagation();
                    return;
                }
                break;
            case Event.ONMOUSEDOWN:
                if (MouseUtil.isPrimary(event)) {
                    setFocus(true);
                    onClickStart();
                    isCapturing = true;
                    // Prevent dragging (on some browsers);
                    event.preventDefault();
                }
                break;
            case Event.ONMOUSEUP:
                if (isCapturing) {
                    isCapturing = false;
                    if (MouseUtil.isPrimary(event)) {
                        onClick();
                    }
                    onClickCancel();
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
                }
                break;
            case Event.ONMOUSEOVER:
                if (getElement().isOrHasChild(DOM.eventGetTarget(event))) {
                    if (isCapturing) {
                        onClickStart();
                    }
                }
                break;
        }

        super.onBrowserEvent(event);

        // Synthesize clicks based on keyboard events AFTER the normal key
        // handling.
        final Action action = KeyBinding.test(event);
        if (action == Action.SELECT || action == Action.EXECUTE) {
            onClick();
        }
    }

    private void onClickStart() {
        face.addClassName("fab-button__face--down");
    }

    private void onClickCancel() {
        face.removeClassName("fab-button__face--down");
    }

    private void onClick() {
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


    // --------------------------------------------------------------------------------


    // The parts of the template the button changes; its outer element is the template's root
    static final class Parts {

        @UiField
        ButtonElement face;
        @UiField
        DivElement icon;
    }


    // --------------------------------------------------------------------------------


    @UiTemplate("FabButton.ui.xml")
    interface Binder extends UiBinder<DivElement, Parts> {

    }
}
