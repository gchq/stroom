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

import stroom.svg.shared.SvgImage;
import stroom.task.client.Task;
import stroom.task.client.TaskMonitor;
import stroom.task.client.TaskMonitorFactory;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.ui.ButtonBase;

public class Button extends ButtonBase implements ButtonView, TaskMonitorFactory {

    private final Element rippleContainer;
    private final Element buttonContent;
    private final Element buttonSpinner;
    private final Element spinnerBorder;
    private final Element icon;
    private final Element margin;
    private final Element text;

    private int taskCount;

    /**
     * Creates a button with no caption.
     */
    public Button() {

        //  <button class="Button Button--base Button Button--contained Button--contained-primary has-text ">
        //     <div class="ripple-container"></div>
        //     <div class="Button__content">
        //        <span class="Button__spinner">
        //           <span role="status" aria-hidden="true" class="spinner-border spinner-border-sm"></span>
        //        </span>
        //        <svg aria-hidden="true" focusable="false" data-prefix="fas" data-icon="check" class="svg-inline--fa fa-check fa-w-16 fa-lg " role="img" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512"><path fill="currentColor" d="M173.898 439.404l-166.4-166.4c-9.997-9.997-9.997-26.206 0-36.204l36.203-36.204c9.997-9.998 26.207-9.998 36.204 0L192 312.69 432.095 72.596c9.997-9.997 26.207-9.997 36.204 0l36.203 36.204c9.997 9.997 9.997 26.206 0 36.204l-294.4 294.401c-9.998 9.997-26.207 9.997-36.204-.001z"></path>
        //        </svg>
        //        <span class="Button__margin"></span>
        //        <span class="Button__text">Close</span>
        //     </div>
        //  </button>

        super(Document.get().createPushButtonElement());
        DOM.sinkEvents(getElement(),
                Event.ONMOUSEDOWN |
                        Event.ONMOUSEUP |
                        Event.ONMOUSEOUT |
                        Event.ONCLICK |
                        Event.ONKEYUP);

        setStyleName("Button Button--base Button Button--contained has-text");

        rippleContainer = DOM.createDiv();
        rippleContainer.setClassName("ripple-container");
        getElement().appendChild(rippleContainer);

        buttonContent = DOM.createDiv();
        buttonContent.setClassName("Button__content");
        getElement().appendChild(buttonContent);

        buttonSpinner = DOM.createSpan();
        buttonSpinner.setClassName("Button__spinner");
        buttonContent.appendChild(buttonSpinner);

        spinnerBorder = DOM.createSpan();
        spinnerBorder.setClassName("spinner-border spinner-border-sm");
        spinnerBorder.setAttribute("role", "status");
        spinnerBorder.setAttribute("aria-hidden", "true");
        buttonSpinner.appendChild(spinnerBorder);

        icon = DOM.createSpan();
        icon.setClassName("svgIcon");
        icon.setAttribute("aria-hidden", "true");
        icon.setAttribute("focusable", "false");
        buttonContent.appendChild(icon);

        margin = DOM.createSpan();
        margin.setClassName("Button__margin");
        buttonContent.appendChild(margin);

        text = DOM.createSpan();
        text.setClassName("Button__text");
        buttonContent.appendChild(text);

        text.setInnerHTML("Close");
    }

    private int getRelativeX(final Event e, final Element target) {
        return e.getClientX() - target.getAbsoluteLeft() + target.getScrollLeft() +
                target.getOwnerDocument().getScrollLeft();
    }

    /**
     * Gets the mouse y-position relative to a given element.
     *
     * @param target the element whose coordinate system is to be used
     * @return the relative y-position
     */
    private int getRelativeY(final Event e, final Element target) {
        return e.getClientY() - target.getAbsoluteTop() + target.getScrollTop() +
                target.getOwnerDocument().getScrollTop();
    }

    @Override
    public void onBrowserEvent(final Event event) {
        if (event.getTypeInt() == Event.ONCLICK) {
            if (event.getClientX() == 0 || event.getClientY() == 0) {
                // If this is a click via a keypress then simulate mousedown in the center.
                getElement().addClassName("Button--down");
                final int x = rippleContainer.getClientWidth() / 2;
                final int y = rippleContainer.getClientHeight() / 2;
                ripple(x, y);
            }
        } else if (event.getTypeInt() == Event.ONMOUSEDOWN) {
            getElement().addClassName("Button--down");
            final int x = getRelativeX(event, rippleContainer);
            final int y = getRelativeY(event, rippleContainer);
            ripple(x, y);
        } else if (event.getTypeInt() == Event.ONMOUSEUP ||
                event.getTypeInt() == Event.ONMOUSEOUT ||
                event.getTypeInt() == Event.ONKEYUP) {
            getElement().removeClassName("Button--down");
        }

        super.onBrowserEvent(event);
    }

    private void ripple(final int x, final int y) {
        final Element ripple = DOM.createDiv();
        ripple.setClassName("ripple");
        ripple.getStyle().setLeft(x, Unit.PX);
        ripple.getStyle().setTop(y, Unit.PX);
        rippleContainer.appendChild(ripple);
        new Timer() {
            @Override
            public void run() {
                rippleContainer.removeChild(ripple);
            }
        }.schedule(1000);
    }

    public void setLoading(final boolean loading) {
        if (loading) {
            getElement().addClassName("Button--loading");
        } else {
            getElement().removeClassName("Button--loading");
        }
    }

    @Override
    public void setEnabled(final boolean enabled) {
        super.setEnabled(enabled);
        if (enabled) {
            getElement().removeClassName("Button--disabled");
        } else {
            getElement().addClassName("Button--disabled");
        }
    }

    @Override
    public void setText(final String text) {
        this.text.setInnerHTML(text);
    }

    public void setIcon(final SvgImage svgImage) {
        icon.setInnerHTML(svgImage.getSvg());
    }

    @Override
    public void focus() {
        getElement().focus();
    }

    @Override
    public TaskMonitor createTaskMonitor() {
        return new TaskMonitor() {
            @Override
            public void onStart(final Task task) {
                taskCount++;
                setLoading(taskCount > 0);
            }

            @Override
            public void onEnd(final Task task) {
                taskCount--;

                if (taskCount < 0) {
                    GWT.log("Negative task count");
                }

                setLoading(taskCount > 0);
            }
        };
    }
}
