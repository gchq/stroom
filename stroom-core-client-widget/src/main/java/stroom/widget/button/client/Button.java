/*
 * Copyright 2023 Crown Copyright
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
import stroom.widget.util.client.CssProperties;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.ButtonElement;
import com.google.gwt.dom.client.DivElement;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.SpanElement;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiTemplate;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.ui.ButtonBase;

/// A button (`button.css`) with an optional icon and a busy spinner. Its markup is `Button.ui.xml`.
public class Button extends ButtonBase implements IsButton, TaskMonitorFactory {

    private static final Binder BINDER = GWT.create(Binder.class);
    // While the busy spinner spins (button.css), until it has shrunk away (as long as that takes)
    private static final String SPINNING = "button--spinning";
    private static final int SPINNER_FADE_MILLIS = 500;
    // Showing content of its own in place of its caption, icon and spinner (button.css)
    private static final String OWN_CONTENT = "button--own-content";

    private final Parts parts;

    private int taskCount;
    private Timer stopSpinning;
    private Runnable onClick;

    /// Makes a button captioned "Close", which callers usually change with `text="..."`.
    public Button() {
        this(new Parts());
    }

    /// Makes a button with its caption.
    ///
    /// @param text The caption, which is also its name.
    public Button(final String text) {
        this(new Parts());
        setText(text);
    }

    private Button(final Parts parts) {
        super(BINDER.createAndBindUi(parts));
        this.parts = parts;
        DOM.sinkEvents(getElement(),
                Event.ONMOUSEDOWN |
                        Event.ONMOUSEUP |
                        Event.ONMOUSEOUT |
                        Event.ONCLICK |
                        Event.ONKEYUP);
        addClickHandler(event -> {
            if (onClick != null) {
                onClick.run();
            }
        });
    }

    /// @param onClick Run when the button is clicked, or null for nothing.
    public void setOnClick(final Runnable onClick) {
        this.onClick = onClick;
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
                getElement().addClassName("button--down");
                final int x = parts.rippleContainer.getClientWidth() / 2;
                final int y = parts.rippleContainer.getClientHeight() / 2;
                ripple(x, y);
            }
        } else if (event.getTypeInt() == Event.ONMOUSEDOWN) {
            getElement().addClassName("button--down");
            final int x = getRelativeX(event, parts.rippleContainer);
            final int y = getRelativeY(event, parts.rippleContainer);
            ripple(x, y);
        } else if (event.getTypeInt() == Event.ONMOUSEUP ||
                event.getTypeInt() == Event.ONMOUSEOUT ||
                event.getTypeInt() == Event.ONKEYUP) {
            getElement().removeClassName("button--down");
        }

        super.onBrowserEvent(event);
    }

    private void ripple(final int x, final int y) {
        final Element ripple = DOM.createDiv();
        ripple.setClassName("button__ripple");
        // Where the press was, which button.css's .button__ripple uses
        CssProperties.set(ripple, "--button__ripple-left", x + "px");
        CssProperties.set(ripple, "--button__ripple-top", y + "px");
        parts.rippleContainer.appendChild(ripple);
        new Timer() {
            @Override
            public void run() {
                parts.rippleContainer.removeChild(ripple);
            }
        }.schedule(1000);
    }

    /// Shows the button's busy spinner, which grows in, or shrinks it away. The spinner spins only
    /// while it shows or shrinks away (`button--spinning`), so an idle button's spinner costs nothing.
    ///
    /// @param loading Whether the button's action is running.
    public void setLoading(final boolean loading) {
        if (stopSpinning != null) {
            stopSpinning.cancel();
            stopSpinning = null;
        }
        if (loading) {
            getElement().addClassName(SPINNING);
            getElement().addClassName("button--loading");
            getElement().setAttribute("aria-busy", "true");
        } else {
            getElement().removeClassName("button--loading");
            getElement().removeAttribute("aria-busy");
            if (getElement().hasClassName(SPINNING)) {
                stopSpinning = new Timer() {
                    @Override
                    public void run() {
                        stopSpinning = null;
                        getElement().removeClassName(SPINNING);
                    }
                };
                stopSpinning.schedule(SPINNER_FADE_MILLIS);
            }
        }
    }

    /// Sets the button's ARIA role, e.g. `button` on a picker's button, which a form group
    /// ([stroom.widget.form.client.FormGroup]) then names by its label and the button's own text.
    ///
    /// @param role The role.
    public void setRole(final String role) {
        getElement().setAttribute("role", role);
    }

    /// Names the button for assistive technology, where its text alone doesn't say enough (e.g.
    /// what "Upload" uploads).
    ///
    /// @param ariaLabel The button's accessible name.
    public void setAriaLabel(final String ariaLabel) {
        getElement().setAttribute("aria-label", ariaLabel);
    }

    /// Hides the button from assistive technology, e.g. where another button beside it does the same
    /// and is named (bound to the `ariaHidden` attribute in ui.xml).
    ///
    /// @param ariaHidden Whether to hide it.
    public void setAriaHidden(final boolean ariaHidden) {
        if (ariaHidden) {
            getElement().setAttribute("aria-hidden", "true");
        } else {
            getElement().removeAttribute("aria-hidden");
        }
    }

    /// Marks the button as one that opens a dialog, which screen readers announce.
    ///
    /// @param opensDialog Whether pressing the button opens a dialog.
    public void setOpensDialog(final boolean opensDialog) {
        if (opensDialog) {
            getElement().setAttribute("aria-haspopup", "dialog");
        } else {
            getElement().removeAttribute("aria-haspopup");
        }
    }

    @Override
    public void setEnabled(final boolean enabled) {
        super.setEnabled(enabled);
        if (enabled) {
            getElement().removeClassName("button--disabled");
        } else {
            getElement().addClassName("button--disabled");
        }
    }

    @Override
    public void setText(final String text) {
        parts.backgroundText.setInnerHTML(text);
        parts.text.setInnerHTML(text);
    }

    /// @return The caption, as shown (not with the hidden copy that sizes the button, which the
    ///         button's own text would include).
    @Override
    public String getText() {
        return parts.text.getInnerText();
    }

    /// Shows content of its own (e.g. a logo, or a summary) in place of its caption, icon, spinner
    /// and the ripple of a press: for a button given a class of its own (`styleName`), which gives it
    /// its look in place of the button's. Its accessible name is then the content's text, unless
    /// it has an ARIA label.
    ///
    /// @param content The content.
    public void setContent(final SafeHtml content) {
        getElement().addClassName(OWN_CONTENT);
        parts.ownContent.setInnerSafeHtml(content);
    }

    public void setIcon(final SvgImage svgImage) {
        parts.icon.addClassName("button__icon--visible");
        parts.icon.setInnerHTML(svgImage.getSvg());
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


    // --------------------------------------------------------------------------------


    // The parts of the template the button changes; the button element itself is the template's root
    static final class Parts {

        @UiField
        SpanElement backgroundText;
        @UiField
        DivElement rippleContainer;
        @UiField
        SpanElement icon;
        @UiField
        SpanElement text;
        @UiField
        SpanElement ownContent;
    }


    // --------------------------------------------------------------------------------


    @UiTemplate("Button.ui.xml")
    interface Binder extends UiBinder<ButtonElement, Parts> {

    }
}
