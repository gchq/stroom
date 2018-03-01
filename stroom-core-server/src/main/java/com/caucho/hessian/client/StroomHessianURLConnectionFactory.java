/*
 * Copyright 2016 Crown Copyright
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

package com.caucho.hessian.client;

import javax.net.ssl.HttpsURLConnection;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.util.logging.Level;
import java.util.logging.Logger;

public class StroomHessianURLConnectionFactory implements HessianConnectionFactory {
    private static final Logger log = Logger.getLogger(HessianURLConnectionFactory.class.getName());
    private final boolean ignoreSSLHostnameVerifier;
    private HessianProxyFactory _proxyFactory;

    public StroomHessianURLConnectionFactory(final boolean ignoreSSLHostnameVerifier) {
        this.ignoreSSLHostnameVerifier = ignoreSSLHostnameVerifier;
    }

    @Override
    public void setHessianProxyFactory(final HessianProxyFactory factory) {
        _proxyFactory = factory;
    }

    /**
     * Opens a new or recycled connection to the HTTP server.
     */
    @Override
    public HessianConnection open(final URL url) throws IOException {
        if (log.isLoggable(Level.FINER))
            log.finer(this + " open(" + url + ")");

        final URLConnection conn = url.openConnection();

        if (ignoreSSLHostnameVerifier) {
            if (conn instanceof HttpsURLConnection) {
                ((HttpsURLConnection) conn).setHostnameVerifier((hostname, session) -> true);
            }
        }

        final long connectTimeout = _proxyFactory.getConnectTimeout();

        if (connectTimeout >= 0)
            conn.setConnectTimeout((int) connectTimeout);

        conn.setDoOutput(true);

        final long readTimeout = _proxyFactory.getReadTimeout();

        if (readTimeout > 0) {
            try {
                conn.setReadTimeout((int) readTimeout);
            } catch (final Throwable e) {
            }
        }

        return new HessianURLConnection(url, conn);
    }
}
