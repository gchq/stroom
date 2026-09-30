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

package stroom.security.impl;

/**
 * The kind of credential that proved a request's identity. Used by {@link SecurityFilter} to
 * decide whether the credential was 'ambient' - attached without the initiating page's
 * involvement - and therefore whether the request needs CSRF verification.
 */
public enum CredentialSource {
    /** A {@code UserIdentity} held in the HTTP session, keyed by the session cookie. */
    SESSION,
    /** A stroom API key. */
    API_KEY,
    /** The internally signed inter-node processing-user token (and its run-as header). */
    CLUSTER_TOKEN,
    /**
     * A verified JWT from the request headers: a bearer token, an AWS ALB-signed
     * {@code x-amzn-oidc-data} token, or a user token relayed by another stroom node.
     */
    REQUEST_TOKEN,
    /** The insecure test/demo credential (only usable when the environment explicitly opts in). */
    TEST_CREDENTIAL
}
