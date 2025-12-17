/*
 * Copyright 2016-2025 Crown Copyright
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

import stroom.svg.client.Preset;
import stroom.widget.util.client.KeyBinding;
import stroom.widget.util.client.KeyBinding.Action;
import stroom.widget.util.client.MouseUtil;
import stroom.widget.util.client.SvgImageUtil;

import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.ui.ButtonBase;

abstract class BaseSvgButton extends ButtonBase implements ButtonView {

    private final Element face;
    /**
     * If <code>true</code>, this widget is capturing with the mouse held down.
     */
    private boolean isCapturing;
    /**
     * Used to decide whether to allow clicks to propagate up to the superclass
     * or container elements.
     */
    private boolean allowClickPropagation;

    BaseSvgButton(final Preset preset) {
        super(Document.get().createPushButtonElement());

        sinkEvents(Event.ONCLICK | Event.ONMOUSEDOWN | Event.ONMOUSEUP | Event.ONMOUSEMOVE | Event.KEYEVENTS);
        getElement().setClassName("icon-button");

        final Element background = Document.get().createDivElement();
        background.setClassName("background");

        face = Document.get().createDivElement();
        face.setClassName("face");

        getElement().appendChild(background);
        getElement().appendChild(face);

        setSvgPreset(preset);

        setEnabled(preset.isEnabled());
    }

    void toggleSvgPreset(final Preset newPreset) {
        setSvgPreset(newPreset);
    }

    void setSvgPreset(final Preset svgPreset) {
        SvgImageUtil.setSvgAsInnerHtml(face, svgPreset);
    }

    @Override
    public void setEnabled(final boolean enabled) {
        super.setEnabled(enabled);
        if (enabled) {
            getElement().removeClassName("disabled");
        } else {
            getElement().addClassName("disabled");
        }
    }

    @Override
    public void onBrowserEvent(final Event event) {
        // Should not act on button if disabled.
        if (!isEnabled()) {
            // This can happen when events are bubbled up from non-disabled
            // children
            isCapturing = false;
            return;
        }

        final int type = DOM.eventGetType(event);
        switch (type) {
            case Event.ONCLICK:
                // If clicks are currently disallowed, keep it from bubbling or
                // being passed to the superclass.
                if (!allowClickPropagation) {
                    event.stopPropagation();
                    return;
                }
                break;
            case Event.ONMOUSEDOWN:
                if (MouseUtil.isPrimary(event)) {
                    setFocus(true);
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
                }
                break;
            case Event.ONMOUSEMOVE:
                if (isCapturing) {
                    // Prevent dragging (on other browsers);
                    event.preventDefault();
                }
                break;
            default:
                // Ignore events we don't care about
        }

        super.onBrowserEvent(event);

        // Synthesize clicks based on keyboard events AFTER the normal key
        // handling.
        final Action action = KeyBinding.test(event);
        if (action == Action.SELECT || action == Action.EXECUTE) {
            onClick();
        }
    }

    private void onClick() {
        // Allow the click we're about to synthesize to pass through to the
        // superclass and containing elements. Element.dispatchEvent() is
        // synchronous, so we simply set and clear the flag within this method.
        allowClickPropagation = true;

        // Mouse coordinates are not always available (e.g., when the click is
        // caused by a keyboard event).
        final NativeEvent evt = Document.get().createClickEvent(
                1,
                0,
                0,
                0,
                0,
                false,
                false,
                false,
                false);
        getElement().dispatchEvent(evt);

        allowClickPropagation = false;
    }

    @Override
    public void setVisible(final boolean visible) {
        super.setVisible(visible);
        if (visible) {
            getElement().removeClassName("invisible");
        } else {
            getElement().addClassName("invisible");
        }
    }
}
