/*
 * Copyright 2026 Crown Copyright
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

import stroom.widget.util.client.DisabledState;

import com.google.gwt.dom.client.Document;
import com.google.gwt.user.client.ui.ButtonBase;

/// A text button drawn as the text around it is, e.g. underlined on hover like a link, for an
/// action that sits in a line or list of text: a time range's quick settings, the data viewer's
/// "Part 1 of 3". It is a real `<button>`, so the keyboard reaches it, Enter and Space press it and
/// screen readers announce it as a button, named by its text (or by [#setAriaLabel(String)]).
///
/// The `link-button` style takes away the browser's button look (border, background, padding and
/// font); the look of the text comes from the style names the caller adds, as a label's would:
///
/// ```
/// <btn:LinkButton ui:field="homeLink" addStyleNames="xsd-browser__link" text="Home" ariaLabel="Schema Home"/>
/// ```
///
/// A standalone action with an icon belongs in a [Toolbar] as a [ToolbarButton] instead.
public class LinkButton extends ButtonBase {

    private static final String STYLE_NAME = "link-button";
    private static final String ARIA_LABEL = "aria-label";
    private static final String ARIA_HASPOPUP = "aria-haspopup";
    private static final String ARIA_EXPANDED = "aria-expanded";
    private static final String ARIA_CONTROLS = "aria-controls";

    private Runnable onClick;

    /// A button with no text yet, e.g. for a template, which gives it its text.
    public LinkButton() {
        // A push button (type="button"), so it never submits a form
        super(Document.get().createPushButtonElement());
        setStyleName(STYLE_NAME);
        addClickHandler(event -> {
            // Disabled with aria-disabled, it is still pressed, but does nothing
            if (onClick != null && !DisabledState.isDisabled(getElement())) {
                onClick.run();
            }
        });
    }

    /// @param text The button's text, which is also its name.
    public LinkButton(final String text) {
        this();
        setText(text);
    }

    /// @param onClick Run when the button is pressed (unless it is disabled, see
    ///                [#setAriaDisabled(boolean)]), or null for nothing.
    public void setOnClick(final Runnable onClick) {
        this.onClick = onClick;
    }

    /// Names the button for screen readers in place of its text, e.g. "Schema Home" for a button
    /// that shows "Home". The name must contain the text shown, so that speech users can say what
    /// they see (WCAG 2.5.3).
    ///
    /// @param ariaLabel The name, or null or empty to be named by the text.
    public void setAriaLabel(final String ariaLabel) {
        if (ariaLabel == null || ariaLabel.isEmpty()) {
            getElement().removeAttribute(ARIA_LABEL);
        } else {
            getElement().setAttribute(ARIA_LABEL, ariaLabel);
        }
    }

    /// Marks the button as one that opens a dialog, which screen readers announce.
    ///
    /// @param opensDialog Whether pressing the button opens a dialog.
    public void setOpensDialog(final boolean opensDialog) {
        if (opensDialog) {
            getElement().setAttribute(ARIA_HASPOPUP, "dialog");
        } else {
            getElement().removeAttribute(ARIA_HASPOPUP);
        }
    }

    /// Says whether what the button shows and hides (see [#setAriaControls(String)]) is shown, for a
    /// button that discloses something, e.g. "Show Detail".
    ///
    /// @param ariaExpanded Whether it is shown.
    public void setAriaExpanded(final boolean ariaExpanded) {
        getElement().setAttribute(ARIA_EXPANDED, String.valueOf(ariaExpanded));
    }

    /// Names the element the button shows and hides, or otherwise controls.
    ///
    /// @param id The element's id, or null or empty for none.
    public void setAriaControls(final String id) {
        if (id == null || id.isEmpty()) {
            getElement().removeAttribute(ARIA_CONTROLS);
        } else {
            getElement().setAttribute(ARIA_CONTROLS, id);
        }
    }

    /// Disables the button, or enables it, leaving it focusable (`aria-disabled` and the shared
    /// disabled class, see [DisabledState]), so that the keyboard can still reach it and its tooltip,
    /// e.g. why it is disabled, can still be found. Pressing it then does nothing.
    ///
    /// @param ariaDisabled Whether it is disabled.
    public void setAriaDisabled(final boolean ariaDisabled) {
        DisabledState.set(getElement(), ariaDisabled);
    }
}
