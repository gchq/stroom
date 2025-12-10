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
import stroom.util.shared.string.CIKey;

import jakarta.validation.constraints.Min;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public class CachedHashedDataFeedKey {

    private final HashedDataFeedKey hashedDataFeedKey;
    private final Path sourceFile;

    private final int hashCode;

    public CachedHashedDataFeedKey(final HashedDataFeedKey hashedDataFeedKey,
                                   final Path sourceFile) {
        this.hashedDataFeedKey = Objects.requireNonNull(hashedDataFeedKey);
        this.sourceFile = Objects.requireNonNull(sourceFile);
        this.hashCode = Objects.hash(hashedDataFeedKey, sourceFile);
    }

    public HashedDataFeedKey getDataFeedKey() {
        return hashedDataFeedKey;
    }

    /**
     * @return The path to the file that this {@link HashedDataFeedKey} was loaded from.
     */
    public Path getSourceFile() {
        return sourceFile;
    }

    /**
     * @return The hash of the data feed key. The hash algorithm used is defined by
     * {@link CachedHashedDataFeedKey#getHashAlgorithm()}
     */
    public String getHash() {
        return hashedDataFeedKey.getHash();
    }

    /**
     * @return May be null if the algorithm encodes the salt in the hash
     */
    public String getSalt() {
        return hashedDataFeedKey.getSalt();
    }

    public DataFeedKeyHashAlgorithm getHashAlgorithm() {
        return hashedDataFeedKey.getHashAlgorithm();
    }

    /**
     * @return The value of a specified meta key.
     */
    public String getStreamMetaValue(final String metaKey) {
        return hashedDataFeedKey.getStreamMetaValue(metaKey);
    }

    /**
     * @return The value of a specified meta key.
     */
    public String getStreamMetaValue(final CIKey metaKey) {
        return hashedDataFeedKey.getStreamMetaValue(metaKey);
    }

    public Map<CIKey, String> getStreamMetaData() {
        return hashedDataFeedKey.getCIStreamMetaData();
    }

    public AttributeMap getAttributeMap() {
        return hashedDataFeedKey.getAttributeMap();
    }

    @Min(0)
    public long getExpiryDateEpochMs() {
        return hashedDataFeedKey.getExpiryDateEpochMs();
    }

    /**
     * @return The expiry date of this data feed key
     */
    public Instant getExpiryDate() {
        return hashedDataFeedKey.getExpiryDate();
    }

    /**
     * @return True if this data feed key has expired
     */
    public boolean isExpired() {
        return hashedDataFeedKey.isExpired();
    }

    @Override
    public boolean equals(final Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }
        final CachedHashedDataFeedKey that = (CachedHashedDataFeedKey) object;
        return Objects.equals(hashedDataFeedKey, that.hashedDataFeedKey)
               && Objects.equals(sourceFile, that.sourceFile);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public String toString() {
        return "CachedDataFeedKey{" +
               "dataFeedKey=" + hashedDataFeedKey +
               ", sourceFile=" + sourceFile +
               '}';
    }
}
