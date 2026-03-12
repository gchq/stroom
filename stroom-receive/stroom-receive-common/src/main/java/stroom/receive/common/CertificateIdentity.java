/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.receive.common;


import stroom.util.cert.DNFormat;
import stroom.util.shared.NullSafe;
import stroom.util.shared.string.CIKey;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Represents an X509 certificate (using the DN of the certificate) and the meta
 * attributes that will be applied to any data receipt using that identity.
 */
public final class CertificateIdentity implements DataFeedIdentity {

    public static final DNFormat DEFAULT_X509_CERT_DN_FORMAT = DNFormat.LDAP;

    @JsonProperty
    @JsonPropertyDescription("The DN from the X509 certificate. The format of the DN is defined by dnFormat.")
    private final String certificateDn;

    @JsonProperty
    @JsonPropertyDescription("The format of the DN String, either OPEN_SSL for '/' delimited, " +
                             "or LDAP for ',' delimited. LDAP is the default if not specified.")
    private final DNFormat dnFormat;

    @JsonProperty
    @JsonPropertyDescription("A map of stream attribute key/value pairs. These will trump any entries " +
                             "in the stream headers.")
    private final Map<CIKey, String> streamMetaData;

    @JsonProperty
    @JsonPropertyDescription("The date/time the key expires, expressed as milliseconds since the unix epoch.")
    private final long expiryDateEpochMs;

    @JsonIgnore
    private final int hashCode;

    @JsonCreator
    public CertificateIdentity(@JsonProperty("certificateDn") final String certificateDn,
                               @JsonProperty("dnFormat") final DNFormat dnFormat,
                               @JsonProperty("streamMetaData") final Map<String, String> streamMetaData,
                               @JsonProperty("expiryDateEpochMs") final long expiryDateEpochMs) {
        this.certificateDn = Objects.requireNonNull(certificateDn);
        this.dnFormat = Objects.requireNonNullElse(dnFormat, DEFAULT_X509_CERT_DN_FORMAT);
        // No point holding blank keys or null values
        this.streamMetaData = NullSafe.map(streamMetaData)
                .entrySet()
                .stream()
                .filter(entry -> NullSafe.isNonBlankString(entry.getKey()))
                .filter(entry -> entry.getValue() != null)
                .collect(Collectors.toMap(
                        entry -> CIKey.of(entry.getKey()),
                        Entry::getValue));
        this.expiryDateEpochMs = expiryDateEpochMs;
        // Pre-compute the hash as we know we are putting each identity into a map
        this.hashCode = Objects.hash(certificateDn, dnFormat, streamMetaData, expiryDateEpochMs);
    }

    public String getCertificateDn() {
        return certificateDn;
    }

    public DNFormat getDnFormat() {
        return dnFormat;
    }

    @Override
    public Map<String, String> getStreamMetaData() {
        return CIKey.convertToStringMap(streamMetaData);
    }

    @Override
    public Map<CIKey, String> getCIStreamMetaData() {
        return streamMetaData;
    }

    @Override
    public long getExpiryDateEpochMs() {
        return expiryDateEpochMs;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final CertificateIdentity that = (CertificateIdentity) o;
        return expiryDateEpochMs == that.expiryDateEpochMs
               && Objects.equals(certificateDn, that.certificateDn)
               && dnFormat == that.dnFormat
               && Objects.equals(streamMetaData, that.streamMetaData);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public String toString() {
        return "CertificateIdentity{" +
               "certificateDn='" + certificateDn + '\'' +
               ", dnFormat=" + dnFormat +
               ", streamMetaData=" + streamMetaData +
               ", expiryDateEpochMs=" + expiryDateEpochMs +
               '}';
    }
}
