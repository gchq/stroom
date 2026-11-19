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

package stroom.gwt.workbench.client.widgets.inputs;

import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.widget.form.client.FieldValidity;
import stroom.widget.form.client.PasswordBox;

import com.google.gwt.user.client.ui.SimplePanel;

/// Stories for Stroom's [PasswordBox], the password field of its password views (e.g.
/// `Login`, `ChangePassword`): a box whose value is hidden, with a button over its
/// end that shows and hides it.
public final class PasswordInputStories {

    private static final String MAX_WIDTH = "360px";
    private static final int MAX_BADGE_LENGTH = 9;

    private PasswordInputStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("Widgets/Inputs/PasswordInput", PasswordInputStories.class)
                .layout(StoryLayout.CENTERED)
                // The button shows the password, and is then named for hiding it; a screen reader is
                // told each time
                .story("Basic", context -> InputWidgets.maxWidth(passwordBox(context, "supersecret"), MAX_WIDTH))
                .withPlay(play -> {
                    final Query input = play.querySelector(".password-box__input");
                    play.expect(input).toHaveAttribute("type", "password");
                    play.click(play.getByRole("button", "Show password"));
                    play.expect(input).toHaveAttribute("type", "text");
                    play.expect(play.getByRole("button", "Hide password")).toBeInTheDocument();
                    play.expect(play.querySelector(".password-box__status")).toHaveTextContent(
                            "Your password is shown");
                    play.click(play.getByRole("button", "Hide password"));
                    play.expect(input).toHaveAttribute("type", "password");
                    play.expect(play.querySelector(".password-box__status")).toHaveTextContent(
                            "Your password is hidden");
                })
                // Invalid state - sets aria-invalid and the invalid class on the input
                .story("Invalid", context -> {
                    final PasswordBox password = passwordBox(context, "123");
                    // As Stroom's views (e.g. ChangePassword) mark an invalid password
                    FieldValidity.setInvalid(password.getInput().getElement(), true);
                    return InputWidgets.maxWidth(password, MAX_WIDTH);
                })
                .withPlay(play -> {
                    final Query password = play.querySelector("input[type='password']");
                    play.expect(password).toHaveAttribute("aria-invalid", "true");
                    play.expect(password).not().toHaveClass("invalid");
                    // A text field (text-field.css), not GWT's gwt-PasswordTextBox
                    play.expect(password).toHaveClass("text-field");
                    play.expect(password).not().toHaveClass("gwt-PasswordTextBox");
                })
                // With a badge beside the button (the change password screen's length badge)
                .story("WithOverlayPrefix", context -> {
                    final PasswordBox password = passwordBox(context, "hunter2");
                    // ChangePassword's length badge, a pill (change-password__length) showing up to "9+"
                    // and hidden when empty. Its colour depends on the password policy in Stroom,
                    // which isn't part of this widget, so it stays --short.
                    final SimplePanel badge = new SimplePanel();
                    badge.setStyleName("change-password__length change-password__length--short");
                    updateBadge(badge, password.getValue());
                    password.addValueChangeHandler(event -> updateBadge(badge, password.getValue()));
                    password.setBadge(badge);
                    return InputWidgets.maxWidth(password, MAX_WIDTH);
                });
    }

    private static PasswordBox passwordBox(final StoryContext context, final String value) {
        final PasswordBox password = new PasswordBox();
        password.setValue(value);
        // As ChangePassword sets it ("Enter Password" there)
        password.setPlaceholder("Enter password…");
        final Spy onChange = context.fn(InputWidgets.ON_CHANGE);
        password.addValueChangeHandler(event -> onChange.call(event.getValue()));
        return password;
    }

    /// The length badge part of `ChangePassword.updateStrength`.
    private static void updateBadge(final SimplePanel badge, final String password) {
        final int length = password.length();
        badge.getElement().setInnerText(length > MAX_BADGE_LENGTH
                ? MAX_BADGE_LENGTH + "+"
                : String.valueOf(length));
        badge.setVisible(length > 0);
    }
}
