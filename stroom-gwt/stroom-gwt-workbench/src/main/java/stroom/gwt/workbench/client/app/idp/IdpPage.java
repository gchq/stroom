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

import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.security.identity.client.presenter.LoginPresenter;

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.dom.client.DivElement;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.ui.HTML;
import com.gwtplatform.mvp.client.Presenter;

import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Consumer;

/// What the internal identity provider's pages need from the page that hosts them, as Stroom's
/// sign in page (`stroom-core/.../servlet/app.html`, served for `/signIn` and `/resetPassword`)
/// provides it, for the stories of `App/IdP/*`:
///
/// * the host page's `#loading` and `#loadingText` elements, which `LoginPresenter`,
///   `AuthenticationErrorPresenter` and `ResetPasswordPresenter` look up when created (and fade
///   out when revealed);
/// * the URL's query parameters (`error`, `redirect_uri`, `token`), which they read with
///   `Window.Location.getParameter`: they are added to the story page's URL with
///   `history.replaceState` (GWT reads the parameters again when the query string changes), and
///   removed again when the story renders again;
/// * navigation: they leave the page with `Window.Location.replace(url)` (after signing in, or back
///   to the sign in page), which would navigate the story away. The story page's `navigate` events
///   (the browser's Navigation API) are cancelled and their URLs given to a listener, so a play can
///   check where Stroom would have gone (React's `onRedirect`/`onSignIn` seams);
/// * revealing: Stroom's `App.onModuleLoad` reveals the page's presenter with `forceReveal()`, which
///   needs a GWTP proxy and place manager; [#reveal] calls the presenter's own `revealInParent()`
///   instead, once its widget has been added to the story.
public final class IdpPage {

    /// The spy recording where Stroom would have navigated to, relative to the page's origin (e.g.
    /// `/dashboard`), or an empty string for `Window.Location.replace("")` (a reload of the page).
    public static final String ON_NAVIGATE = "onRedirect";

    private IdpPage() {
        // Static utility
    }

    /// Sets the page up as Stroom's sign in page: the host page's loading elements, the URL's query
    /// parameters, and navigations recorded by [#ON_NAVIGATE] (and given to the listener) instead of
    /// made. All of it is undone when the story renders again.
    ///
    /// @param harness    The story's harness.
    /// @param parameters The URL's query parameters (unencoded), in order.
    /// @param listener   Told the URL of each navigation, as recorded by [#ON_NAVIGATE].
    public static void setUp(final ScreenHarness harness,
                      final Map<String, String> parameters,
                      final Consumer<String> listener) {
        harness.fn(ON_NAVIGATE);
        addLoadingElements(harness);
        addQueryParameters(harness, parameters);
        interceptNavigation(harness, url -> {
            harness.spy(ON_NAVIGATE, url);
            if (!harness.isDisposed()) {
                listener.accept(url);
            }
        });
    }

    /// Reveals a page as `forceReveal()` would, by calling its presenter's `revealInParent()`
    /// (protected), which also does what the page does when it is shown (e.g. focusing the user
    /// name, or the password reset page asking for the new password).
    ///
    /// @param presenter The page's presenter, whose widget has been added to the story.
    public static native void reveal(Presenter<?, ?> presenter) /*-{
        presenter.@com.gwtplatform.mvp.client.Presenter::revealInParent()();
    }-*/;

    /// Stroom's check of a post sign in redirect, `LoginPresenter.isSameOrigin` (private).
    ///
    /// @param presenter A sign in page.
    /// @param uri       The `redirect_uri`, or null.
    /// @return Whether Stroom would navigate to it after signing in.
    public static native boolean isSameOrigin(LoginPresenter presenter, String uri) /*-{
        var p = presenter;
        return p.@stroom.security.identity.client.presenter.LoginPresenter::isSameOrigin(Ljava/lang/String;)(uri);
    }-*/;

    /// @return The story page's origin, e.g. `http://localhost:6008`, as `LoginPresenter.isSameOrigin`
    /// builds it (`Window.Location.getProtocol() + "//" + Window.Location.getHost()`).
    public static native String origin() /*-{
        return $wnd.location.protocol + "//" + $wnd.location.host;
    }-*/;

    /// A message shown in place of the page once Stroom has navigated away, as React's `FlowDemo`
    /// shows `✓ Signed in — redirected to: <strong>…</strong>`.
    ///
    /// @param url The URL, shown in bold, or an empty string for a reload of the page.
    /// @return The message.
    public static HTML redirectedMessage(final String url) {
        final HTML message = new HTML();
        message.setText("✓ Signed in — redirected to: ");
        final Element strong = Document.get().createElement("strong");
        strong.setInnerText(url.isEmpty()
                ? "(this page, reloaded)"
                : url);
        message.getElement().appendChild(strong);
        message.getElement().setAttribute("style",
                "padding: 40px; color: var(--text-color); font-size: 1.1rem");
        return message;
    }

    /// Checks that nothing went wrong: no alerts and no requests without a fixture.
    ///
    /// @param play The play.
    public static void expectNoProblems(final Play play) {
        play.expect(play.spy(ScreenHarness.ALERT_SPY)).not().toHaveBeenCalled();
        play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
    }

    // The host page's loading box (hidden), outside any widget, as RootPanel.get(id) requires
    private static void addLoadingElements(final ScreenHarness harness) {
        final Document document = Document.get();
        if (document.getElementById("loading") == null) {
            final DivElement loading = document.createDivElement();
            loading.setId("loading");
            loading.getStyle().setProperty("display", "none");
            final DivElement loadingText = document.createDivElement();
            loadingText.setId("loadingText");
            loading.appendChild(loadingText);
            document.getBody().appendChild(loading);
            harness.addCleanUp(loading::removeFromParent);
        }
    }

    private static void addQueryParameters(final ScreenHarness harness, final Map<String, String> parameters) {
        if (!parameters.isEmpty()) {
            final String original = currentUrl();
            final StringBuilder url = new StringBuilder(original);
            // The story page's own parameters (id, viewMode) come first
            for (final Entry<String, String> parameter : parameters.entrySet()) {
                url.append(url.indexOf("?") < 0
                                ? "?"
                                : "&")
                        .append(encode(parameter.getKey()))
                        .append('=')
                        .append(encode(parameter.getValue()));
            }
            replaceUrl(url.toString());
            harness.addCleanUp(() -> replaceUrl(original));
        }
    }

    private static void interceptNavigation(final ScreenHarness harness, final Consumer<String> listener) {
        final JavaScriptObject handler = addNavigateHandler(listener);
        if (handler == null) {
            throw new IllegalStateException("This story needs the browser's Navigation API (e.g. Chrome), "
                                            + "to stop Stroom's sign in page navigating away");
        }
        harness.addCleanUp(() -> removeNavigateHandler(handler));
    }

    private static native String currentUrl() /*-{
        return $wnd.location.href;
    }-*/;

    private static native void replaceUrl(String url) /*-{
        $wnd.history.replaceState($wnd.history.state, "", url);
    }-*/;

    private static native String encode(String value) /*-{
        return encodeURIComponent(value);
    }-*/;

    // Cancels cross-document navigations (Window.Location.replace) and tells the listener their
    // URL, relative to the origin; an empty string for a reload of the page (replace("")).
    private static native JavaScriptObject addNavigateHandler(Consumer<String> listener) /*-{
        var navigation = $wnd.navigation;
        if (!navigation) {
            return null;
        }
        var handler = $entry(function (event) {
            if (event.destination.sameDocument || !event.cancelable) {
                return;
            }
            event.preventDefault();
            var url = event.destination.url;
            var origin = $wnd.location.protocol + "//" + $wnd.location.host;
            if (url === $wnd.location.href) {
                url = "";
            } else if (url.indexOf(origin + "/") === 0) {
                url = url.substring(origin.length);
            }
            listener.@java.util.function.Consumer::accept(Ljava/lang/Object;)(url);
        });
        navigation.addEventListener("navigate", handler);
        return handler;
    }-*/;

    private static native void removeNavigateHandler(JavaScriptObject handler) /*-{
        $wnd.navigation.removeEventListener("navigate", handler);
    }-*/;
}
