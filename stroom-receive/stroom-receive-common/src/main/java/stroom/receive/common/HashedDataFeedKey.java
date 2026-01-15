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

package stroom.receive.common;

import stroom.meta.api.AttributeMap;
import stroom.util.shared.NullSafe;
import stroom.util.shared.string.CIKey;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Represents the hashed form of a Data Feed Key, i.e. where we only have the
 * hash of the key and not the key itself.
 */
@JsonPropertyOrder(alphabetic = true)
public class HashedDataFeedKey {

    @JsonProperty
    @JsonPropertyDescription("The hash of the datafeed key. Hashed using hashAlgorithm.")
    private final String hash;

    @JsonProperty
    @JsonPropertyDescription("The salt used to hash the datafeed key. May be null if there is no salt or the " +
                             "algorithm encodes the salt in the hash, like BCrypt does.")
    private final String salt;

    @JsonProperty
    @JsonPropertyDescription("The hash algorithm used to hash the datafeed key. Currently one of " +
                             "(ARGON2|BCRYPT_2A).")
    private final DataFeedKeyHashAlgorithm hashAlgorithm;

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
    public HashedDataFeedKey(@JsonProperty("hash") final String hash,
                             @JsonProperty("salt") final String salt,
                             @JsonProperty("hashAlgorithmId") final DataFeedKeyHashAlgorithm hashAlgorithm,
                             @JsonProperty("streamMetaData") final Map<String, String> streamMetaData,
                             @JsonProperty("expiryDateEpochMs") final long expiryDateEpochMs) {
        this.hash = hash;
        this.salt = salt;
        this.hashAlgorithm = hashAlgorithm;
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
        // Cache the hashCode as we know we will use it
        this.hashCode = Objects.hash(
                hash,
                salt,
                hashAlgorithm,
                streamMetaData,
                expiryDateEpochMs);
    }

    @NotBlank
    public String getHash() {
        return hash;
    }

    public String getSalt() {
        return salt;
    }

    @NotBlank
    public DataFeedKeyHashAlgorithm getHashAlgorithm() {
        return hashAlgorithm;
    }

    public Map<String, String> getStreamMetaData() {
        return CIKey.convertToStringMap(streamMetaData);
    }

    @JsonIgnore
    public Map<CIKey, String> getCIStreamMetaData() {
        return streamMetaData;
    }

    @JsonIgnore
    public AttributeMap getAttributeMap() {
        return new AttributeMap(getStreamMetaData());
    }

    @JsonIgnore
    public String getStreamMetaValue(final String metaKey) {
        return NullSafe.isNonBlankString(metaKey)
                ? streamMetaData.get(CIKey.of(metaKey))
                : null;
    }

    @JsonIgnore
    public String getStreamMetaValue(final CIKey metaKey) {
        return NullSafe.get(metaKey, streamMetaData::get);
    }

    @Min(0)
    public long getExpiryDateEpochMs() {
        return expiryDateEpochMs;
    }

    @JsonIgnore
    public Instant getExpiryDate() {
        return Instant.ofEpochMilli(expiryDateEpochMs);
    }

    @JsonIgnore
    public boolean isExpired() {
        return Instant.now().isAfter(getExpiryDate());
    }

    @Override
    public boolean equals(final Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }
        final HashedDataFeedKey that = (HashedDataFeedKey) object;
        return expiryDateEpochMs == that.expiryDateEpochMs
               && Objects.equals(hash, that.hash)
               && Objects.equals(salt, that.salt)
               && Objects.equals(hashAlgorithm, that.hashAlgorithm)
               && Objects.equals(streamMetaData, that.streamMetaData);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public String toString() {
        return "DataFeedKey{" +
               "hash='" + hash + '\'' +
               ", salt='" + salt + '\'' +
               ", hashAlgorithmId='" + hashAlgorithm + '\'' +
               ", streamMetaData=" + streamMetaData +
               ", expiryDateEpochMs=" + expiryDateEpochMs +
               '}';
    }
}
