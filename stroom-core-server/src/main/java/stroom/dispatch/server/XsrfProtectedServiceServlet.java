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
package stroom.dispatch.server;

import com.google.gwt.user.client.rpc.RpcToken;
import com.google.gwt.user.client.rpc.RpcTokenException;
import com.google.gwt.user.client.rpc.XsrfToken;
import com.google.gwt.user.server.rpc.AbstractXsrfProtectedServiceServlet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

public class XsrfProtectedServiceServlet extends AbstractXsrfProtectedServiceServlet {
    private static final Logger LOGGER = LoggerFactory.getLogger(XsrfProtectedServiceServlet.class);

    /**
     * Validates {@link XsrfToken} included with {@link RPCRequest} against XSRF
     * cookie.
     */
    @Override
    protected void validateXsrfToken(final RpcToken token, final Method method) throws RpcTokenException {
        try {
            LOGGER.debug("Validating XSRF token");
            if (token == null) {
                throw new RpcTokenException("XSRF token missing");
            }

            final String expectedToken = SessionHashUtil.createSessionHash(getThreadLocalRequest());
            final XsrfToken xsrfToken = (XsrfToken) token;

            if (!expectedToken.equals(xsrfToken.getToken())) {
                throw new RpcTokenException("Invalid XSRF token");
            }
        } catch (final RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
            throw e;
        }
    }
}
