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

import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.svg.shared.SvgImage;
import stroom.widget.button.client.InlineSvgButton;

import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.PasswordTextBox;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

/// Stories matching `Widgets/Inputs/PasswordInput` in the React Storybook.
///
/// Stroom has no password input widget: the React `PasswordInput` is a port of the markup that
/// Stroom's password views (`ChangePasswordViewImpl`, `LoginViewImpl`) build in their UiBinder
/// templates, a [PasswordTextBox] with an overlay holding an optional length badge and an
/// [InlineSvgButton] that shows or hides the password. These stories build the same widgets in
/// the same way, with the views' show/hide and badge code.
public final class PasswordInputStories {

    private static final String MAX_WIDTH = "360px";
    private static final String SHOW_PASSWORD = "Show Password";
    private static final String HIDE_PASSWORD = "Hide Password";
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
                // Show/hide toggle - click the eye button to reveal the value
                .story("Basic", context -> {
                    // React's autoComplete prop: Stroom's views don't set autocomplete
                    final PasswordTextBox password = passwordTextBox(context, "supersecret");
                    return InputWidgets.maxWidth(passwordInput(password, null), MAX_WIDTH);
                })
                // Invalid state - sets aria-invalid and the invalid class
                .story("Invalid", context -> {
                    final PasswordTextBox password = passwordTextBox(context, "123");
                    // Differs from React: Stroom's views mark an invalid password with the invalid
                    // class only; React also sets aria-invalid="true".
                    password.addStyleName("invalid");
                    return InputWidgets.maxWidth(passwordInput(password, null), MAX_WIDTH);
                })
                // With an overlay prefix badge (e.g. the GWT password-length badge)
                .story("WithOverlayPrefix", context -> {
                    final PasswordTextBox password = passwordTextBox(context, "hunter2");
                    // Differs from React: this is ChangePasswordViewImpl's length badge, a Bootstrap
                    // pill ("badge badge-pill badge-danger") showing up to "9+" and hidden when
                    // empty; React's story passes its own plain, faded passwordLengthBadge span.
                    // The badge's colour depends on the password policy in Stroom, which isn't
                    // part of this widget, so it stays badge-danger.
                    final SimplePanel badge = new SimplePanel();
                    badge.setStyleName("badge badge-pill badge-danger");
                    updateBadge(badge, password.getValue());
                    password.addValueChangeHandler(event -> updateBadge(badge, password.getValue()));
                    return InputWidgets.maxWidth(passwordInput(password, badge), MAX_WIDTH);
                });
    }

    private static PasswordTextBox passwordTextBox(final StoryContext context, final String value) {
        final PasswordTextBox password = new PasswordTextBox();
        password.setValue(value);
        // As ChangePasswordViewImpl sets it ("Enter Password" there)
        password.getElement().setAttribute("placeholder", "Enter password…");
        final Spy onChange = context.fn(InputWidgets.ON_CHANGE);
        password.addValueChangeHandler(event -> onChange.call(event.getValue()));
        return password;
    }

    /// Builds `ChangePasswordViewImpl.ui.xml`'s password markup around the text box.
    ///
    /// @param badge The length badge, or null for none.
    private static Widget passwordInput(final PasswordTextBox password, final Widget badge) {
        final InlineSvgButton showPassword = new InlineSvgButton();
        showPassword.addStyleName("showPassword");
        showPassword.setSvg(SvgImage.EYE);
        showPassword.setTitle(SHOW_PASSWORD);
        showPassword.setEnabled(true);
        // Differs from React: like Stroom's views, the toggle has no aria-label or aria-pressed
        // (React sets both); its accessible name is its title.
        showPassword.addClickHandler(event -> toggleShowPassword(password, showPassword));

        final FlowPanel overlay = new FlowPanel();
        overlay.addStyleName("passwordTextBoxOverlay");
        if (badge != null) {
            overlay.add(badge);
        }
        overlay.add(showPassword);

        final FlowPanel outer = new FlowPanel();
        outer.addStyleName("passwordTextBoxOuter");
        outer.add(password);
        outer.add(overlay);
        return outer;
    }

    /// `ChangePasswordViewImpl.onShowPassword`.
    private static void toggleShowPassword(final PasswordTextBox password, final InlineSvgButton showPassword) {
        final String type = password.getElement().getAttribute("type");
        if (type == null || "password".equals(type)) {
            password.getElement().setAttribute("type", "text");
            showPassword.setSvg(SvgImage.EYE_OFF);
            showPassword.setTitle(HIDE_PASSWORD);
        } else {
            password.getElement().setAttribute("type", "password");
            showPassword.setSvg(SvgImage.EYE);
            showPassword.setTitle(SHOW_PASSWORD);
        }
    }

    /// The length badge part of `ChangePasswordViewImpl.onPassword`.
    private static void updateBadge(final SimplePanel badge, final String password) {
        final int length = password.length();
        badge.getElement().setInnerText(length > MAX_BADGE_LENGTH
                ? MAX_BADGE_LENGTH + "+"
                : String.valueOf(length));
        badge.setVisible(length > 0);
    }
}
