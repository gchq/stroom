/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.util.jersey;

/**
 * A set of names for distinct jersey clients. Each one should map to a server (or cluster of servers backed
 * by a DNS name) such that the connection to each 'server' can be configured separately depending on the
 * nature of the communication or the capabilities of the 'server'. The {@link JerseyClientName#DEFAULT}
 * name provides the means for clients to use a common default config if the named one does not exist.
 */
public enum JerseyClientName {

    /**
     * Client for getting AWS public keys from <pre>https://public-keys.auth.elb.{}.amazonaws.com/{}</pre>
     */
    AWS_PUBLIC_KEYS,

    /**
     * Client for Proxy to communicate with a downstream (in datafeed flow terms) stroom
     * or stroom-proxy instance. This client is used for obtaining receipt policy rules and
     * verifying API keys.
     */
    DOWNSTREAM,

    /**
     * The default client. Used if a named client has not been configured or where
     * you want multiple named clients to share similar config.
     */
    DEFAULT,

    /**
     * Client for the HttpPostFilter.
     */
    @Deprecated
    HTTP_POST_FILTER, // HttpPostFilter is deprecated, use HttpAppender which has content controlled config

    /**
     * Client for communications with an external Open ID Connect provider,
     * e.g. Cognito, KeyCloak, Azure AD, etc.
     */
    OPEN_ID,

    /**
     * Client for inter-node stroom communications
     */
    STROOM,
}
