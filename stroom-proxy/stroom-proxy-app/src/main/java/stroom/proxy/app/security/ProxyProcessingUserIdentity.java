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

package stroom.proxy.app.security;

import stroom.security.api.UserIdentity;
import stroom.security.openid.api.ClusterToken;

/// The identity that stroom-proxy runs its own internal work as, e.g. receiving data or
/// refreshing cached receipt rules.
///
/// It only exists so that internal work passes the permission checks in the proxy's
/// services. It holds no token as all calls made by the proxy to a downstream host are
/// authenticated using the configured API key or, if there is no API key, the service user
/// token from the IDP.
public final class ProxyProcessingUserIdentity implements UserIdentity {

    /// The single instance of the proxy processing user identity.
    public static final ProxyProcessingUserIdentity INSTANCE = new ProxyProcessingUserIdentity();

    private ProxyProcessingUserIdentity() {
    }

    /// @return The subject ID of the processing user.
    @Override
    public String subjectId() {
        return ClusterToken.PROCESSING_USER_SUBJECT;
    }

    @Override
    public String toString() {
        return subjectId();
    }
}
