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
import stroom.widget.button.client.ToolbarButton;
import stroom.widget.util.client.FieldEnd;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.DivElement;
import com.google.gwt.event.dom.client.HasKeyDownHandlers;
import com.google.gwt.event.dom.client.HasKeyUpHandlers;
import com.google.gwt.event.dom.client.KeyDownHandler;
import com.google.gwt.event.dom.client.KeyUpHandler;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiChild;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HasEnabled;
import com.google.gwt.user.client.ui.HasText;
import com.google.gwt.user.client.ui.HasValue;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

import java.util.function.Consumer;

/// A password box: a text box whose value is hidden, with a button over its end that shows and hides
/// it. The button is named for what pressing it does ("Show password", then "Hide password"), and a
/// screen reader is told when the password is shown or hidden, as the focus stays on the button.
///
/// Declared in a template as `<form:PasswordBox ui:field="password" placeholder="Enter Password"/>`.
/// It is used as a text box would be (its value, key handlers, focus, enabled and read only); its
/// input is [#getInput()], e.g. to mark it invalid with [FieldValidity]. Something shown beside the
/// button (e.g. the change password screen's length badge) is added with [#setBadge(Widget)].
///
/// Its markup is `PasswordBox.ui.xml`, styled by password-box.css: `password-box`,
/// `password-box__input`, `password-box__end`, `password-box__badge`, `password-box__toolbar` and
/// `password-box__status`.
public class PasswordBox extends Composite
        implements HasValue<String>, HasText, HasEnabled, HasKeyDownHandlers, HasKeyUpHandlers {

    private static final Binder BINDER = GWT.create(Binder.class);
    private static final String BLOCK = "password-box";
    private static final String WITH_BADGE = BLOCK + "--with-badge";
    private static final String TYPE = "type";

    @UiField
    com.google.gwt.user.client.ui.PasswordTextBox input;
    @UiField
    FlowPanel end;
    @UiField
    ToolbarButton revealButton;
    // Says that the password is shown or hidden, which the button's new name alone may not
    @UiField
    DivElement status;
    // Keeps the text clear of the button and any badge, whose width depends on its text
    private final FieldEnd fieldEnd;
    // What the box holds, as its button and announcements name it, e.g. "password" or "pass phrase"
    private String secretName = "password";
    private boolean shown;
    private HandlerRegistration onChangeRegistration;

    public PasswordBox() {
        initWidget(BINDER.createAndBindUi(this));
        revealButton.setOnClick(this::toggleShown);
        fieldEnd = new FieldEnd(input.getElement(), end.getElement());

        update();
    }

    @Override
    protected void onLoad() {
        super.onLoad();
        fieldEnd.start();
    }

    @Override
    protected void onUnload() {
        fieldEnd.stop();
        super.onUnload();
    }

    /// @return The input, e.g. to mark it invalid or focus it with [FieldValidity].
    public com.google.gwt.user.client.ui.PasswordTextBox getInput() {
        return input;
    }

    /// @param placeholder The hint shown in the box while it is empty.
    public void setPlaceholder(final String placeholder) {
        input.getElement().setAttribute("placeholder", placeholder);
    }

    /// Says what the box holds, so that browsers and password managers can fill it in.
    ///
    /// @param autocomplete What it holds, e.g. `current-password` or `new-password`, or null or
    ///                     empty to leave it to the browser.
    public void setAutocomplete(final String autocomplete) {
        if (autocomplete == null || autocomplete.isEmpty()) {
            input.getElement().removeAttribute("autocomplete");
        } else {
            input.getElement().setAttribute("autocomplete", autocomplete);
        }
    }

    /// @param secretName What the box holds, for its button's name and announcements, e.g. "pass
    ///                   phrase" ("Show pass phrase"). It is "password" unless set.
    public void setSecretName(final String secretName) {
        this.secretName = secretName;
        update();
    }

    /// Shows something beside the button, e.g. a length badge, and leaves room for it in the box. In
    /// a template it is the box's `badge` child:
    /// `<form:PasswordBox><form:badge><c:AriaPanel .../></form:badge></form:PasswordBox>`.
    ///
    /// @param badge The widget to show.
    @UiChild(tagname = "badge", limit = 1)
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
            status.setInnerText("Your " + secretName + " is " + (shown
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

    /// @param onChange Told the new value when the user changes it (as a value change event is), or
    ///                 null for nothing.
    public void setOnChange(final Consumer<String> onChange) {
        if (onChangeRegistration != null) {
            onChangeRegistration.removeHandler();
            onChangeRegistration = null;
        }
        if (onChange != null) {
            onChangeRegistration = input.addValueChangeHandler(event -> onChange.accept(event.getValue()));
        }
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

    /// Puts the focus in the box.
    public void focus() {
        input.setFocus(true);
    }

    @Override
    public HandlerRegistration addKeyDownHandler(final KeyDownHandler handler) {
        return input.addKeyDownHandler(handler);
    }

    @Override
    public HandlerRegistration addKeyUpHandler(final KeyUpHandler handler) {
        return input.addKeyUpHandler(handler);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, PasswordBox> {

    }
}
