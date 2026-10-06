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

package stroom.gwt.workbench.client.app.idp;

import stroom.gwt.workbench.client.app.gin.idp.IdpScreenGinjector;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.framework.client.story.StoryContext;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;
import com.gwtplatform.mvp.client.Presenter;

import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

/// Renders one of the internal identity provider's pages (sign in, authentication error, password
/// reset) as Stroom's sign in host page shows it, for the stories of `App/IdP/*`: the page's
/// presenter from an [IdpScreenGinjector], filling the canvas, revealed once the harness has
/// started up, with the URL's query parameters and navigation handled by [IdpPage].
///
/// When Stroom navigates away (e.g. after signing in), a message says where it went, as React's
/// stories do: in place of the page (React's `FlowDemo`) or beside it (the `ResetPassword` marker).
final class SignInPages {

    private SignInPages() {
        // Static utility
    }

    /// Renders a page.
    ///
    /// @param context    The story's context.
    /// @param fixtures   The story's REST fixtures.
    /// @param uiConfig   The UI config's JSON, or null for the default.
    /// @param parameters The URL's query parameters.
    /// @param message    Creates the message shown on navigating, given the URL (an empty string
    ///                   for a reload of the page).
    /// @param replacePage Whether the message replaces the page.
    /// @param page       Creates the page's presenter, given the injector and harness.
    /// @return The story's widget.
    static Widget render(final StoryContext context,
                         final RestFixtures fixtures,
                         final String uiConfig,
                         final Map<String, String> parameters,
                         final Function<String, Widget> message,
                         final boolean replacePage,
                         final BiFunction<IdpScreenGinjector, ScreenHarness, Presenter<?, ?>> page) {
        final IdpScreenGinjector injector = GWT.create(IdpScreenGinjector.class);
        final ScreenHarness.Builder builder = ScreenHarness.builder(context, fixtures)
                .injector(injector)
                .realAlerts();
        if (uiConfig != null) {
            builder.uiConfig(uiConfig);
        }
        final ScreenHarness harness = builder.build();
        final Presenter<?, ?>[] shown = new Presenter<?, ?>[1];
        IdpPage.setUp(harness, parameters, url -> {
            if (replacePage && shown[0] != null) {
                shown[0].getWidget().getParent().setVisible(false);
            }
            harness.add(message.apply(url));
        });
        // As Stroom's App.onModuleLoad does for '/signIn' and '/resetPassword'
        harness.afterStartUp(() -> {
            final Presenter<?, ?> presenter = page.apply(injector, harness);
            shown[0] = presenter;
            harness.addContent(presenter);
            IdpPage.reveal(presenter);
        });
        return harness.asWidget();
    }
}
