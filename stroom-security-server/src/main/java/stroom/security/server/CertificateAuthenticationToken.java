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

package stroom.security.server;

import org.apache.shiro.authc.HostAuthenticationToken;
import org.apache.shiro.authc.RememberMeAuthenticationToken;
import stroom.util.cert.CertificateUtil;

public class CertificateAuthenticationToken implements HostAuthenticationToken, RememberMeAuthenticationToken {
    private static final long serialVersionUID = 1L;

    private final String certificateDn;

    /**
     * Whether or not 'rememberMe' should be enabled for the corresponding login
     * attempt; default is <code>false</code>
     */
    private boolean rememberMe = false;

    /**
     * The location from where the login attempt occurs, or <code>null</code> if
     * not known or explicitly omitted.
     */
    private String host;

    public CertificateAuthenticationToken(final String certificateDn) {
        this(certificateDn, false, null);
    }

    public CertificateAuthenticationToken(final String certificateDn, final boolean rememberMe, final String host) {
        this.certificateDn = certificateDn;
        this.rememberMe = rememberMe;
        this.host = host;
    }

    @Override
    public Object getCredentials() {
        return certificateDn;
    }

    @Override
    public Object getPrincipal() {
        return CertificateUtil.extractCNFromDN(certificateDn);
    }

    /**
     * Returns the host name or IP string from where the authentication attempt
     * occurs. May be <tt>null</tt> if the host name/IP is unknown or explicitly
     * omitted. It is up to the Authenticator implementation processing this
     * token if an authentication attempt without a host is valid or not.
     * <p/>
     * <p>
     * (Shiro's default Authenticator allows <tt>null</tt> hosts to support
     * localhost and proxy server environments).
     * </p>
     *
     * @return the host from where the authentication attempt occurs, or
     * <tt>null</tt> if it is unknown or explicitly omitted.
     * @since 1.0
     */
    @Override
    public String getHost() {
        return host;
    }

    /**
     * Sets the host name or IP string from where the authentication attempt
     * occurs. It is up to the Authenticator implementation processing this
     * token if an authentication attempt without a host is valid or not.
     * <p/>
     * <p>
     * (Shiro's default Authenticator allows <tt>null</tt> hosts to allow
     * localhost and proxy server environments).
     * </p>
     *
     * @param host the host name or IP string from where the attempt is occuring
     * @since 1.0
     */
    public void setHost(final String host) {
        this.host = host;
    }

    /**
     * Returns <tt>true</tt> if the submitting user wishes their identity
     * (principal(s)) to be remembered across sessions, <tt>false</tt>
     * otherwise. Unless overridden, this value is <tt>false</tt> by default.
     *
     * @return <tt>true</tt> if the submitting user wishes their identity
     * (principal(s)) to be remembered across sessions, <tt>false</tt>
     * otherwise (<tt>false</tt> by default).
     * @since 0.9
     */
    @Override
    public boolean isRememberMe() {
        return rememberMe;
    }

    /**
     * Sets if the submitting user wishes their identity (pricipal(s)) to be
     * remembered across sessions. Unless overridden, the default value is
     * <tt>false</tt>, indicating <em>not</em> to be remembered across sessions.
     *
     * @param rememberMe value inidicating if the user wishes their identity
     *                   (principal(s)) to be remembered across sessions.
     * @since 0.9
     */
    public void setRememberMe(final boolean rememberMe) {
        this.rememberMe = rememberMe;
    }
}
