/*
 * Copyright 2011 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package stroom.core.dispatch;

import com.google.gwt.user.client.rpc.XsrfToken;
import com.google.gwt.user.client.rpc.XsrfTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.util.guice.ResourcePaths;
import stroom.util.shared.IsServlet;

import java.util.Set;

public class XsrfTokenServiceServlet extends CustomRemoteServiceServlet implements XsrfTokenService, IsServlet {
    private static final Logger LOGGER = LoggerFactory.getLogger(XsrfTokenServiceServlet.class);

    private static final Set<String> PATH_SPECS = Set.of(ResourcePaths.XSRF_TOKEN_RPC_PATH);

    /**
     * Generates and returns new XSRF token.
     */
    public XsrfToken getNewXsrfToken() {
        return new XsrfToken(generateTokenValue());
    }

    /**
     * Generates new XSRF token.
     *
     * @return session cookie SHA-512 hash.
     */
    private String generateTokenValue() {
        try {
            LOGGER.debug("Generating XSRF token");
            return XsrfHashUtil.createSessionHash(getThreadLocalRequest());
        } catch (final RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
            throw e;
        }
    }

    /**
     * @return The part of the path that will be in addition to any base path,
     * e.g. "/datafeed".
     */
    @Override
    public Set<String> getPathSpecs() {
        return PATH_SPECS;
    }
}
