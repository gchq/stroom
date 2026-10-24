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

import com.google.gwt.dom.client.Document;
import com.google.gwt.user.client.ui.ButtonBase;

/// A text button drawn as the text around it is, e.g. underlined on hover like a link, for an
/// action that sits in a line or list of text: a time range's quick settings, the data viewer's
/// "Part 1 of 3". It is a real `<button>`, so the keyboard reaches it, Enter and Space press it and
/// screen readers announce it as a button, named by its text (or by [#setAriaLabel(String)]).
///
/// The `linkButton` style takes away the browser's button look (border, background, padding and
/// font); the look of the text comes from the style names the caller adds, as a label's would:
///
/// ```
/// <b:LinkButton ui:field="home" addStyleNames="link" text="Home" ariaLabel="Schema Home"/>
/// ```
///
/// A standalone action with an icon belongs in a [Toolbar] as a [ToolbarButton] instead.
public class LinkButton extends ButtonBase {

    private static final String STYLE_NAME = "linkButton";
    private static final String ARIA_LABEL = "aria-label";
    private static final String ARIA_HASPOPUP = "aria-haspopup";

    private Runnable onClick;

    /// A button with no text yet, e.g. for a template, which gives it its text.
    public LinkButton() {
        // A push button (type="button"), so it never submits a form
        super(Document.get().createPushButtonElement());
        setStyleName(STYLE_NAME);
        addClickHandler(event -> {
            if (onClick != null) {
                onClick.run();
            }
        });
    }

    /// @param text The button's text, which is also its name.
    public LinkButton(final String text) {
        this();
        setText(text);
    }

    /// @param onClick Run when the button is pressed, or null for nothing.
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
}
