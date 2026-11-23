/*
 * Copyright 2018 Crown Copyright
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

package stroom.pipeline.filter;

import stroom.util.cache.CacheConfig;
import stroom.util.config.annotations.ReadOnly;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.shared.NullSafe;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;
import java.util.Objects;


@JsonPropertyOrder(alphabetic = true)
public class XsltConfig extends AbstractConfig implements IsStroomConfig {

    private static final int DEFAULT_MAX_ELEMENTS = 1000000;
    static final int DEFAULT_MAX_EMAILS_PER_RUN = 100;

    private final CacheConfig cacheConfig;
    private final int maxElements;
    private final boolean secureMode;
    private final List<String> allowedUriPrefixes;
    private final List<String> allowedEmailRecipients;
    private final int maxEmailsPerRun;

    public XsltConfig() {
        cacheConfig = CacheConfig.builder()
                .maximumSize(1000L)
                .expireAfterAccess(StroomDuration.ofMinutes(10))
                .build();
        maxElements = DEFAULT_MAX_ELEMENTS;
        secureMode = true;
        allowedUriPrefixes = List.of();
        allowedEmailRecipients = List.of();
        maxEmailsPerRun = DEFAULT_MAX_EMAILS_PER_RUN;
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public XsltConfig(@JsonProperty("cache") final CacheConfig cacheConfig,
                      @JsonProperty("maxElements") final Integer maxElements,
                      @JsonProperty("secureMode") final Boolean secureMode,
                      @JsonProperty("allowedUriPrefixes") final List<String> allowedUriPrefixes,
                      @JsonProperty("allowedEmailRecipients") final List<String> allowedEmailRecipients,
                      @JsonProperty("maxEmailsPerRun") final Integer maxEmailsPerRun) {
        this.cacheConfig = cacheConfig;
        this.maxElements = Objects.requireNonNullElse(maxElements, DEFAULT_MAX_ELEMENTS);
        this.secureMode = Objects.requireNonNullElse(secureMode, true);
        // Leave out empty YAML list items rather than fail.
        this.allowedUriPrefixes = NullSafe.stream(allowedUriPrefixes)
                .filter(Objects::nonNull)
                .toList();
        this.allowedEmailRecipients = NullSafe.stream(allowedEmailRecipients)
                .filter(Objects::nonNull)
                .toList();
        this.maxEmailsPerRun = Objects.requireNonNullElse(maxEmailsPerRun, DEFAULT_MAX_EMAILS_PER_RUN);
    }

    @JsonProperty("cache")
    @JsonPropertyDescription("The cache config for the XSLT pool.")
    public CacheConfig getCacheConfig() {
        return cacheConfig;
    }

    @JsonPropertyDescription("The maximum number of elements that the XSLT filter will expect to receive before " +
            "it errors. This protects Stroom from running out of memory in cases where an appropriate XML splitter " +
            "has not been used in a pipeline.")
    public int getMaxElements() {
        return maxElements;
    }

    /// @return True if pipeline XSLT is stopped from reading files, URLs and environment variables, and its
    /// HTTP functions from connecting to local addresses.
    @ReadOnly
    @JsonPropertyDescription("If true (the default), pipeline XSLT cannot read files or URLs (with doc(), " +
            "document(), unparsed-text(), collection() or uri-collection()), write files with " +
            "xsl:result-document, or read environment variables or Java system properties, apart from local files " +
            "below allowedUriPrefixes. The http-call() and fetch-json() functions cannot connect to an address on " +
            "this host, a link-local address or a cloud instance metadata address (other private network " +
            "addresses are allowed), and their client configuration cannot set a proxy. Setting this to false " +
            "removes these restrictions for pipeline XSLT only, so is not recommended. XPath in dashboards, " +
            "queries, stepping filters and search extraction is " +
            "always restricted. Can only be set in the configuration file.")
    public boolean isSecureMode() {
        return secureMode;
    }

    /// @return The local directories that pipeline XSLT may read from when secure mode is on.
    @ReadOnly
    @JsonPropertyDescription("The local directories that pipeline XSLT may read from when secureMode is true, " +
            "as absolute paths or file: URIs, e.g. '/data/lookups' or 'file:///data/lookups/'. A file can be read " +
            "if it is at or below one of these directories once symbolic links are resolved. A collection() " +
            "must be one of these directories or below one. Paths containing '..' are refused. Other schemes, " +
            "such as http:, are ignored; use the http-call() or fetch-json() functions instead. " +
            "Empty by default, so nothing can be read. Can only be set in the configuration file.")
    public List<String> getAllowedUriPrefixes() {
        return allowedUriPrefixes;
    }

    /// @return The addresses and domains that send-email() may send to, or empty for any.
    @ReadOnly
    @JsonPropertyDescription("The email addresses that the XSLT function send-email() may send to, " +
            "e.g. 'alerts@example.com', or whole domains starting with '@', e.g. '@example.com'. A domain does " +
            "not include its subdomains. When set, every To, Cc and Bcc address must be a plain address " +
            "(no display name) that matches one of these. Empty by default, so any address can be used. " +
            "Can only be set in the configuration file.")
    public List<String> getAllowedEmailRecipients() {
        return allowedEmailRecipients;
    }

    /// @return The most emails send-email() may send in one run of an XSLT.
    @ReadOnly
    @JsonPropertyDescription("The most emails that the XSLT function send-email() may send in one run of an " +
            "XSLT, e.g. while processing one stream. Calls after that are reported as errors and send nothing. " +
            "Zero stops send-email() sending any email. Can only be set in the configuration file.")
    public int getMaxEmailsPerRun() {
        return maxEmailsPerRun;
    }

    @Override
    public String toString() {
        return "XsltConfig{" +
                "cacheConfig=" + cacheConfig +
                ", maxElements=" + maxElements +
                ", secureMode=" + secureMode +
                ", allowedUriPrefixes=" + allowedUriPrefixes +
                ", allowedEmailRecipients=" + allowedEmailRecipients +
                ", maxEmailsPerRun=" + maxEmailsPerRun +
                '}';
    }
}
