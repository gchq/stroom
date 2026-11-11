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

package stroom.security.openid.api;

import java.time.Duration;
import java.util.Optional;

/// Issues short-lived access tokens, signed by Stroom's internal identity provider, that let another service act
/// as a Stroom user, e.g. an MCP server that calls Stroom's API back on the user's behalf.
///
/// The service checks a token against Stroom's issuer and published keys, and against its own audience. Stroom
/// accepts the token on its API only if that audience is Stroom's client id or one of its allowed audiences.
/// Tokens are recorded in the internal identity provider's token inventory, so they show to administrators. A
/// token issued under a sign-in belongs to that sign-in's grant, so it is revoked with it, e.g. when the user signs
/// out or their session ends.
public interface UserTokenIssuer {

    /// @return True if tokens can be issued: only when Stroom is its own identity provider.
    boolean isAvailable();

    /// Gives an access token for a user and an audience, reusing one issued earlier while it has long enough
    /// left to run.
    ///
    /// @param subjectId    The user's subject id.
    /// @param audience     Who the token is for.
    /// @param signInJwt    The token the user signed in with, whose grant the issued token joins, or null if there
    ///                     is none (e.g. a user of an API key), when the token simply runs out.
    /// @param minRemaining How long the token must still have to run, e.g. as long as a request made with it may
    ///                     take, as a server keeps using the token a request came with until it answers. The
    ///                     issuer's own least time applies if this is shorter, and its greatest lifetime if
    ///                     longer.
    /// @return The token, or empty if tokens cannot be issued (see [#isAvailable()]).
    Optional<String> getAccessToken(String subjectId, String audience, String signInJwt, Duration minRemaining);
}
