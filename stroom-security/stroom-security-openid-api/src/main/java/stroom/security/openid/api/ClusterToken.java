/*
 * Copyright 2016-2026 Crown Copyright
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

/**
 * Constants for the internally-signed inter-node cluster (processing-user) token. Both the minting side
 * (stroom-security-identity) and the verifying side (stroom-security-impl) reference these so the two cannot
 * drift.
 * <p>
 * The cluster token is a machine credential for inter-node authentication, signed with the cluster's internal
 * signing key (the shared DB JWK). It is deliberately independent of any OIDC client registration and of the
 * configured (possibly external) identity provider: the internal signing key is the sole trust anchor for the
 * processing user in every {@link IdpType} mode. It carries a fixed, cluster-internal issuer and audience so
 * it is unambiguously distinguishable from a token minted by a real identity provider.
 */
public final class ClusterToken {

    /**
     * The {@code iss} claim of the internal cluster token - a fixed, deployment-independent constant, distinct
     * from any real IDP's issuer.
     */
    public static final String CLUSTER_ISSUER = "stroom-cluster";

    /**
     * The {@code aud} claim of the internal cluster token.
     */
    public static final String CLUSTER_AUDIENCE = "stroom-cluster";

    /**
     * The {@code sub} claim of the internal cluster token - the processing user. Must match
     * {@code InternalIdpProcessingUserIdentity.INTERNAL_PROCESSING_USER}.
     */
    public static final String PROCESSING_USER_SUBJECT = "INTERNAL_PROCESSING_USER";

    private ClusterToken() {
        // Constants only.
    }
}
