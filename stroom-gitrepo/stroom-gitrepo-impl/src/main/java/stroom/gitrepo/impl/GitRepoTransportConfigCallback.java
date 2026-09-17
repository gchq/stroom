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

package stroom.gitrepo.impl;

import stroom.util.http.HttpClientConfiguration;
import stroom.util.jersey.HttpClientProviderCache;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.transport.SshTransport;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.transport.TransportHttp;

/**
 * The single transport callback for a Git command.
 * <p>
 * A {@link org.eclipse.jgit.api.TransportCommand} holds exactly one transport config callback, so SSH and
 * HTTP cannot each have their own - hence this, which dispatches on the transport JGit actually chose for
 * the URL. The two are mutually exclusive in practice: a URL is either {@code ssh://} or {@code https://}.
 * </p>
 * <p>
 * Note that the HTTP half applies whether or not the repository needs credentials. A public repository on a
 * server with a private CA presents no credentials and still has to trust the certificate, so the TLS
 * configuration cannot be attached inside the credentials handling.
 * </p>
 */
class GitRepoTransportConfigCallback implements TransportConfigCallback {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(GitRepoTransportConfigCallback.class);

    private final TransportConfigCallback sshCallback;
    private final HttpClientProviderCache httpClientProviderCache;
    private final HttpClientConfiguration httpClientConfiguration;

    /**
     * @param sshCallback             Applied to SSH transports, or null if the repository has no SSH key.
     * @param httpClientConfiguration Applied to HTTP transports, or null to leave JGit's default HTTP stack
     *                                in place.
     */
    GitRepoTransportConfigCallback(final TransportConfigCallback sshCallback,
                                   final HttpClientProviderCache httpClientProviderCache,
                                   final HttpClientConfiguration httpClientConfiguration) {
        this.sshCallback = sshCallback;
        this.httpClientProviderCache = httpClientProviderCache;
        this.httpClientConfiguration = httpClientConfiguration;
    }

    @Override
    public void configure(final Transport transport) {
        if (transport instanceof SshTransport) {
            if (sshCallback != null) {
                sshCallback.configure(transport);
            }
        } else if (transport instanceof final TransportHttp transportHttp) {
            if (httpClientConfiguration != null) {
                LOGGER.debug("Configuring HTTP transport to use the configured client");
                transportHttp.setHttpConnectionFactory(new StroomHttpConnectionFactory(
                        httpClientProviderCache,
                        httpClientConfiguration));
            }
        }
    }
}
