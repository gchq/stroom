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

package stroom.externaldatasource.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;
import java.util.Objects;

/// The settings an [ExternalDataSourceDoc] uses to search an LDAP directory.
///
/// Each query gives its own LDAP search filter; the document sets where the search starts and how far
/// it reaches. The bind DN and password are held in the Credentials store and referenced here by name
/// only, so this object is safe to send to the browser and to include in exports.
@JsonPropertyOrder({
        "url",
        "startTls",
        "credentialName",
        "baseDn",
        "scope",
        "attributes",
        "pageSize",
        "minRefreshMinutes"})
@JsonInclude(Include.NON_NULL)
public class LdapSettings {

    /// The entries fetched in each page of results when no page size is set.
    public static final int DEFAULT_PAGE_SIZE = 500;
    /// The largest page size a document may set.
    public static final int MAX_PAGE_SIZE = 10_000;

    @JsonProperty
    private final String url;
    @JsonProperty
    private final Boolean startTls;
    @JsonProperty
    private final String credentialName;
    @JsonProperty
    private final String baseDn;
    @JsonProperty
    private final LdapSearchScope scope;
    @JsonProperty
    private final List<DataColumn> attributes;
    @JsonProperty
    private final Integer pageSize;
    @JsonProperty
    private final Integer minRefreshMinutes;

    /// Creates LDAP settings.
    ///
    /// @param url               The directory's URL, `ldaps://host:port` or `ldap://host:port`.
    /// @param startTls          True to start TLS on an `ldap://` connection, `null` meaning false.
    /// @param credentialName    The name of a username and password credential holding the bind DN and
    ///                          its password.
    /// @param baseDn            The DN searches start from.
    /// @param scope             How far below the base DN searches reach, or `null` for
    ///                          [LdapSearchScope#SUBTREE].
    /// @param attributes        The attributes read from each entry, each becoming a field of the same
    ///                          name, or `null` for none.
    /// @param pageSize          The entries fetched in each page, or `null` for [#DEFAULT_PAGE_SIZE].
    /// @param minRefreshMinutes The shortest automatic rerun interval of a dashboard in minutes, or
    ///                          `null` for none.
    @JsonCreator
    public LdapSettings(@JsonProperty("url") final String url,
                        @JsonProperty("startTls") final Boolean startTls,
                        @JsonProperty("credentialName") final String credentialName,
                        @JsonProperty("baseDn") final String baseDn,
                        @JsonProperty("scope") final LdapSearchScope scope,
                        @JsonProperty("attributes") final List<DataColumn> attributes,
                        @JsonProperty("pageSize") final Integer pageSize,
                        @JsonProperty("minRefreshMinutes") final Integer minRefreshMinutes) {
        this.url = url;
        this.startTls = startTls;
        this.credentialName = credentialName;
        this.baseDn = baseDn;
        this.scope = scope;
        this.attributes = attributes;
        this.pageSize = pageSize;
        this.minRefreshMinutes = minRefreshMinutes;
    }

    /// @return The directory's URL, or `null` if not set.
    public String getUrl() {
        return url;
    }

    /// @return True to start TLS on an `ldap://` connection, or `null` meaning false.
    public Boolean getStartTls() {
        return startTls;
    }

    /// @return The name of the credential holding the bind DN and password, or `null` if not set.
    public String getCredentialName() {
        return credentialName;
    }

    /// @return The DN searches start from, or `null` if not set.
    public String getBaseDn() {
        return baseDn;
    }

    /// @return How far below the base DN searches reach, or `null` meaning [LdapSearchScope#SUBTREE].
    public LdapSearchScope getScope() {
        return scope;
    }

    /// @return The attributes read from each entry, in field order, or `null` for none.
    public List<DataColumn> getAttributes() {
        return attributes;
    }

    /// @return The entries fetched in each page, or `null` for [#DEFAULT_PAGE_SIZE].
    public Integer getPageSize() {
        return pageSize;
    }

    /// @return The shortest interval, in minutes, at which a dashboard may rerun queries automatically,
    ///         or `null` for none.
    public Integer getMinRefreshMinutes() {
        return minRefreshMinutes;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof final LdapSettings that)) {
            return false;
        }
        return Objects.equals(url, that.url) &&
               Objects.equals(startTls, that.startTls) &&
               Objects.equals(credentialName, that.credentialName) &&
               Objects.equals(baseDn, that.baseDn) &&
               scope == that.scope &&
               Objects.equals(attributes, that.attributes) &&
               Objects.equals(pageSize, that.pageSize) &&
               Objects.equals(minRefreshMinutes, that.minRefreshMinutes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(url, startTls, credentialName, baseDn, scope, attributes, pageSize, minRefreshMinutes);
    }

    @Override
    public String toString() {
        return "LdapSettings{" +
               "url='" + url + '\'' +
               ", startTls=" + startTls +
               ", credentialName='" + credentialName + '\'' +
               ", baseDn='" + baseDn + '\'' +
               ", scope=" + scope +
               ", attributes=" + attributes +
               ", pageSize=" + pageSize +
               ", minRefreshMinutes=" + minRefreshMinutes +
               '}';
    }

    /// @return A builder initialised with the values of these settings.
    public Builder copy() {
        return new Builder(this);
    }

    /// @return A new, empty builder.
    public static Builder builder() {
        return new Builder();
    }

    /// Builds [LdapSettings] instances.
    public static final class Builder {

        private String url;
        private Boolean startTls;
        private String credentialName;
        private String baseDn;
        private LdapSearchScope scope;
        private List<DataColumn> attributes;
        private Integer pageSize;
        private Integer minRefreshMinutes;

        private Builder() {
        }

        private Builder(final LdapSettings settings) {
            this.url = settings.url;
            this.startTls = settings.startTls;
            this.credentialName = settings.credentialName;
            this.baseDn = settings.baseDn;
            this.scope = settings.scope;
            this.attributes = settings.attributes;
            this.pageSize = settings.pageSize;
            this.minRefreshMinutes = settings.minRefreshMinutes;
        }

        /// @param url The directory's URL, `ldaps://host:port` or `ldap://host:port`.
        /// @return This builder.
        public Builder url(final String url) {
            this.url = url;
            return this;
        }

        /// @param startTls True to start TLS on an `ldap://` connection.
        /// @return This builder.
        public Builder startTls(final Boolean startTls) {
            this.startTls = startTls;
            return this;
        }

        /// @param credentialName The name of the credential holding the bind DN and password.
        /// @return This builder.
        public Builder credentialName(final String credentialName) {
            this.credentialName = credentialName;
            return this;
        }

        /// @param baseDn The DN searches start from.
        /// @return This builder.
        public Builder baseDn(final String baseDn) {
            this.baseDn = baseDn;
            return this;
        }

        /// @param scope How far below the base DN searches reach, or `null` for the whole subtree.
        /// @return This builder.
        public Builder scope(final LdapSearchScope scope) {
            this.scope = scope;
            return this;
        }

        /// @param attributes The attributes read from each entry, in field order.
        /// @return This builder.
        public Builder attributes(final List<DataColumn> attributes) {
            this.attributes = attributes;
            return this;
        }

        /// @param pageSize The entries fetched in each page, or `null` for the default.
        /// @return This builder.
        public Builder pageSize(final Integer pageSize) {
            this.pageSize = pageSize;
            return this;
        }

        /// @param minRefreshMinutes The shortest automatic rerun interval of a dashboard in minutes, or
        ///                          `null` for none.
        /// @return This builder.
        public Builder minRefreshMinutes(final Integer minRefreshMinutes) {
            this.minRefreshMinutes = minRefreshMinutes;
            return this;
        }

        /// @return New settings holding the values set on this builder.
        public LdapSettings build() {
            return new LdapSettings(
                    url,
                    startTls,
                    credentialName,
                    baseDn,
                    scope,
                    attributes,
                    pageSize,
                    minRefreshMinutes);
        }
    }
}
