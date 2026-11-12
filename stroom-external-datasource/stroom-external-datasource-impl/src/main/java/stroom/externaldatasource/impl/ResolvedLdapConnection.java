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

package stroom.externaldatasource.impl;

import stroom.externaldatasource.shared.LdapSearchScope;

import java.util.Objects;

/// The LDAP settings of an External Data Source with its credential resolved and its URL checked:
/// everything needed to connect, bind and search.
///
/// @param host     The directory's host.
/// @param port     The directory's port.
/// @param ldaps    True to connect with TLS from the start, as `ldaps://` does.
/// @param startTls True to start TLS on a plain connection before binding.
/// @param bindDn   The DN to bind as.
/// @param password The bind password.
/// @param baseDn   The DN searches start from.
/// @param scope    How far below the base DN searches reach.
/// @param pageSize The entries fetched in each page.
record ResolvedLdapConnection(String host,
                              int port,
                              boolean ldaps,
                              boolean startTls,
                              String bindDn,
                              String password,
                              String baseDn,
                              LdapSearchScope scope,
                              int pageSize) {

    /// Checks the values that must be set.
    ResolvedLdapConnection {
        Objects.requireNonNull(host);
        Objects.requireNonNull(bindDn);
        Objects.requireNonNull(password);
        Objects.requireNonNull(baseDn);
        Objects.requireNonNull(scope);
    }

    /// @return The URL connected to, for messages.
    String url() {
        return (ldaps
                ? LdapConnectionValidator.LDAPS_SCHEME
                : LdapConnectionValidator.LDAP_SCHEME) + "://" + host + ":" + port;
    }

    @Override
    public String toString() {
        // The password is left out, as this may be logged.
        return "ResolvedLdapConnection{" +
               "url='" + url() + '\'' +
               ", startTls=" + startTls +
               ", bindDn='" + bindDn + '\'' +
               ", baseDn='" + baseDn + '\'' +
               ", scope=" + scope +
               ", pageSize=" + pageSize +
               '}';
    }
}
