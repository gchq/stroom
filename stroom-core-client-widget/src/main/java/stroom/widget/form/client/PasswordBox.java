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

package stroom.widget.form.client;

import stroom.svg.shared.SvgImage;
import stroom.widget.button.client.Toolbar;
import stroom.widget.button.client.ToolbarButton;

import com.google.gwt.event.dom.client.HasKeyDownHandlers;
import com.google.gwt.event.dom.client.HasKeyUpHandlers;
import com.google.gwt.event.dom.client.KeyDownHandler;
import com.google.gwt.event.dom.client.KeyUpHandler;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HasEnabled;
import com.google.gwt.user.client.ui.HasText;
import com.google.gwt.user.client.ui.HasValue;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.PasswordTextBox;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

/// A password box: a text box whose value is hidden, with a button over its end that shows and hides
/// it. The button is named for what pressing it does ("Show password", then "Hide password"), and a
/// screen reader is told when the password is shown or hidden, as the focus stays on the button.
///
/// Declared in a template as `<form:PasswordBox ui:field="password" placeholder="Enter Password"/>`.
/// It is used as a text box would be (its value, key handlers, focus, enabled and read only); its
/// input is [#getInput()], e.g. to mark it invalid with [FieldValidity]. Something shown beside the
/// button (e.g. the change password screen's length badge) is added with [#setBadge(Widget)].
///
/// Styled by password-box.css: `password-box`, `password-box__input`, `password-box__end`,
/// `password-box__badge`, `password-box__toolbar` and `password-box__status`.
public class PasswordBox extends Composite
        implements HasValue<String>, HasText, HasEnabled, HasKeyDownHandlers, HasKeyUpHandlers {

    private static final String BLOCK = "password-box";
    private static final String WITH_BADGE = BLOCK + "--with-badge";
    private static final String TYPE = "type";

    private final PasswordTextBox input = new PasswordTextBox();
    private final FlowPanel end = new FlowPanel();
    private final ToolbarButton revealButton;
    private final Label status = new Label();
    // What the box holds, as its button and announcements name it, e.g. "password" or "pass phrase"
    private String secretName = "password";
    private boolean shown;

    public PasswordBox() {
        input.addStyleName(BLOCK + "__input");

        final Toolbar toolbar = new Toolbar("Password");
        toolbar.addStyleName(BLOCK + "__toolbar");
        revealButton = toolbar.button(SvgImage.EYE, "", this::toggleShown);

        end.setStyleName(BLOCK + "__end");
        end.add(toolbar);

        // Says that the password is shown or hidden, which the button's new name alone may not
        status.setStyleName(BLOCK + "__status");
        status.getElement().setAttribute("aria-live", "polite");

        final FlowPanel panel = new FlowPanel();
        panel.setStyleName(BLOCK);
        panel.add(input);
        panel.add(end);
        panel.add(status);
        initWidget(panel);

        update();
    }

    /// @return The input, e.g. to mark it invalid or focus it with [FieldValidity].
    public PasswordTextBox getInput() {
        return input;
    }

    /// @param placeholder The hint shown in the box while it is empty.
    public void setPlaceholder(final String placeholder) {
        input.getElement().setAttribute("placeholder", placeholder);
    }

    /// @param secretName What the box holds, for its button's name and announcements, e.g. "pass
    ///                   phrase" ("Show pass phrase"). It is "password" unless set.
    public void setSecretName(final String secretName) {
        this.secretName = secretName;
        update();
    }

    /// Shows something beside the button, e.g. a length badge, and leaves room for it in the box.
    ///
    /// @param badge The widget to show.
    public void setBadge(final Widget badge) {
        // In a holder of its own, as the badge's own classes may be replaced (e.g. its colour)
        final SimplePanel holder = new SimplePanel(badge);
        holder.setStyleName(BLOCK + "__badge");
        end.insert(holder, 0);
        addStyleName(WITH_BADGE);
    }

    /// @return Whether the password is shown.
    public boolean isShown() {
        return shown;
    }

    /// Shows or hides the password.
    ///
    /// @param shown Whether the password is shown.
    public void setShown(final boolean shown) {
        if (this.shown != shown) {
            this.shown = shown;
            update();
            status.setText("Your " + secretName + " is " + (shown
                    ? "shown"
                    : "hidden"));
        }
    }

    private void toggleShown() {
        setShown(!shown);
    }

    private void update() {
        input.getElement().setAttribute(TYPE, shown
                ? "text"
                : "password");
        revealButton.setSvg(shown
                ? SvgImage.EYE_OFF
                : SvgImage.EYE);
        revealButton.setTitle((shown
                ? "Hide "
                : "Show ") + secretName);
    }

    @Override
    public String getValue() {
        return input.getValue();
    }

    @Override
    public void setValue(final String value) {
        input.setValue(value);
    }

    @Override
    public void setValue(final String value, final boolean fireEvents) {
        input.setValue(value, fireEvents);
    }

    @Override
    public HandlerRegistration addValueChangeHandler(final ValueChangeHandler<String> handler) {
        return input.addValueChangeHandler(handler);
    }

    @Override
    public String getText() {
        return input.getText();
    }

    @Override
    public void setText(final String text) {
        input.setText(text);
    }

    @Override
    public boolean isEnabled() {
        return input.isEnabled();
    }

    /// Enables or disables the box. Its button stays available, so a value that can't be changed
    /// can still be read.
    ///
    /// @param enabled Whether the box can be typed in.
    @Override
    public void setEnabled(final boolean enabled) {
        input.setEnabled(enabled);
    }

    /// @param readOnly Whether the box's value can be read but not changed.
    public void setReadOnly(final boolean readOnly) {
        input.setReadOnly(readOnly);
    }

    /// @param focused Whether the box's input has the focus.
    public void setFocus(final boolean focused) {
        input.setFocus(focused);
    }

    @Override
    public HandlerRegistration addKeyDownHandler(final KeyDownHandler handler) {
        return input.addKeyDownHandler(handler);
    }

    @Override
    public HandlerRegistration addKeyUpHandler(final KeyUpHandler handler) {
        return input.addKeyUpHandler(handler);
    }
}
