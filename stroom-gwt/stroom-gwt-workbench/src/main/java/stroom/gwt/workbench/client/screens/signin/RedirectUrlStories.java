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

package stroom.gwt.workbench.client.screens.signin;

import stroom.gwt.workbench.client.app.gin.idp.IdpScreenGinjector;
import stroom.gwt.workbench.client.app.idp.IdpPage;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.security.identity.client.presenter.LoginPresenter;
import stroom.util.client.RedirectUrlUtil;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;

import java.util.Map;

/// The `Screens/SignIn/redirectUrl` stories, which check Stroom's post sign in redirect guard:
///
/// * `isSafeRootRelativePath` is Stroom's `RedirectUrlUtil.isSafeRootRelativePath` (GWT client code,
///   `stroom-core-client`), called directly;
/// * `isSameOrigin` is Stroom's `LoginPresenter.isSameOrigin` (private, called with JSNI by
///   `IdpPage.isSameOrigin`) on a real sign in page, created (not shown) as the story renders. It
///   compares with the page's own origin (`Window.Location`), which a story can't change, so the
///   story page's origin stands for Stroom's in these checks.
public final class RedirectUrlStories {

    // The rendering's sign in page, for isSameOrigin (a play's checks run after it renders)
    private static LoginPresenter loginPresenter;

    private RedirectUrlStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("Screens/SignIn/redirectUrl", RedirectUrlStories.class)
                .story("AllowsPlainRootRelativePaths", context -> new Label("See the play function."))
                .withPlay(play -> {
                    expectSafe(play, "/", true);
                    expectSafe(play, "/stroom", true);
                    expectSafe(play, "/stroom/ui?name=a&back=/x#frag", true);
                })
                // '//host' is protocol-relative: an absolute URL to another origin
                .story("RejectsProtocolRelative", context -> new Label("See the play function."))
                .withPlay(play -> {
                    expectSafe(play, "//evil.example", false);
                    expectSafe(play, "//evil.example/path", false);
                })
                // A browser reads '\' as '/', so these would be '//evil.example'
                .story("RejectsBackslashAuthorityBypass", context -> new Label("See the play function."))
                .withPlay(play -> {
                    expectSafe(play, "/\\evil.example", false);
                    expectSafe(play, "/\\/evil.example", false);
                    expectSafe(play, "/path\\evil.example", false);
                })
                // Control characters can be stripped while parsing the URL
                .story("RejectsControlCharacters", context -> new Label("See the play function."))
                .withPlay(play -> {
                    expectSafe(play, "/\tevil.example", false);
                    expectSafe(play, "/\nevil.example", false);
                    expectSafe(play, "/\revil.example", false);
                    expectSafe(play, "/\u007fevil.example", false);
                })
                .story("RejectsNonRootRelativeAndEmpty", context -> new Label("See the play function."))
                .withPlay(play -> {
                    expectSafe(play, "https://evil.example", false);
                    expectSafe(play, "javascript:alert(1)", false);
                    expectSafe(play, "relative/path", false);
                    expectSafe(play, "", false);
                    // A null redirect
                    expectSafe(play, null, false);
                })
                // An absolute URL back into this origin: the ordinary round trip
                .story("SameOriginAbsoluteUrlAllowed", RedirectUrlStories::renderSignInPage)
                .withPlay(play -> {
                    // The page's origin stands for Stroom's
                    expectSameOrigin(play, "${ORIGIN}", true);
                    expectSameOrigin(play, "${ORIGIN}/", true);
                    expectSameOrigin(play, "${ORIGIN}/stroom/ui", true);
                    expectSameOrigin(play, "/stroom/ui", true);
                    expectNoProblems(play);
                })
                // A prefix of the origin isn't the origin
                .story("OffOriginRejected", RedirectUrlStories::renderSignInPage)
                .withPlay(play -> {
                    expectSameOrigin(play, "https://evil.example/path", false);
                    expectSameOrigin(play, "${ORIGIN}.evil.example/path", false);
                    expectSameOrigin(play, "//evil.example", false);
                    // Same host, other scheme: a downgrade (here, or an upgrade) isn't the same origin
                    expectSameOrigin(play, "${OTHER_SCHEME_ORIGIN}/x", false);
                    expectSameOrigin(play, "", false);
                    // Stroom's isSameOrigin isn't null safe, as afterLogin() reloads the page for a
                    // missing redirect_uri without calling it
                    play.expect("isSameOrigin(null) throws a NullPointerException", () -> {
                        try {
                            IdpPage.isSameOrigin(loginPresenter, null);
                            return false;
                        } catch (final NullPointerException e) {
                            return true;
                        }
                    }).toBe(true);
                    expectNoProblems(play);
                });
    }

    private static void expectSafe(final Play play, final String uri, final boolean expected) {
        play.expect("isSafeRootRelativePath(" + describe(uri) + ")",
                () -> RedirectUrlUtil.isSafeRootRelativePath(uri)).toBe(expected);
    }

    // ${ORIGIN} is the page's origin, ${OTHER_SCHEME_ORIGIN} the same host with the other scheme
    private static void expectSameOrigin(final Play play, final String uri, final boolean expected) {
        play.expect("isSameOrigin(" + describe(uri) + ")",
                () -> IdpPage.isSameOrigin(loginPresenter, withOrigin(uri))).toBe(expected);
    }

    private static String withOrigin(final String uri) {
        if (uri == null) {
            return null;
        }
        final String origin = IdpPage.origin();
        final String otherSchemeOrigin = origin.startsWith("https:")
                ? "http:" + origin.substring("https:".length())
                : "https:" + origin.substring("http:".length());
        return uri.replace("${ORIGIN}", origin).replace("${OTHER_SCHEME_ORIGIN}", otherSchemeOrigin);
    }

    private static String describe(final String uri) {
        if (uri == null) {
            return "null";
        }
        final StringBuilder text = new StringBuilder("'");
        for (int i = 0; i < uri.length(); i++) {
            final char c = uri.charAt(i);
            if (c < 0x20 || c == 0x7f) {
                text.append("\\x").append(Integer.toHexString(0x100 | c).substring(1));
            } else {
                text.append(c);
            }
        }
        return text.append("'").toString();
    }

    private static void expectNoProblems(final Play play) {
        play.expect(play.spy(ScreenHarness.ALERT_SPY)).not().toHaveBeenCalled();
        play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
    }

    // A sign in page (not shown), for its isSameOrigin
    private static Widget renderSignInPage(final StoryContext context) {
        final RestFixtures fixtures = RestFixtures.builder()
                .get("/authentication/v1/fetchPasswordPolicy", RestReply.json("{\"allowPasswordResets\": false}"))
                .build();
        final IdpScreenGinjector injector = GWT.create(IdpScreenGinjector.class);
        final ScreenHarness harness = ScreenHarness.builder(context, fixtures)
                .injector(injector)
                .build();
        IdpPage.setUp(harness, Map.of(), url -> {
        });
        final LoginPresenter presenter = injector.getLoginPresenter();
        loginPresenter = presenter;
        harness.addCleanUp(() -> {
            if (loginPresenter == presenter) {
                loginPresenter = null;
            }
        });
        harness.add(new Label("See the play function."));
        return harness.asWidget();
    }
}
