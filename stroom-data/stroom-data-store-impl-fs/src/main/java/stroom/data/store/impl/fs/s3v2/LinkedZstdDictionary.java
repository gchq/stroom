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

package stroom.data.store.impl.fs.s3v2;


import stroom.util.shared.HasAuditInfoGetters;
import stroom.util.shared.HasIntegerId;

import java.util.Objects;
import java.util.UUID;

/**
 * A {@link ZstdDictionary} that is linked to a {@link ZstdDictionaryKey}.
 */
public class LinkedZstdDictionary implements HasAuditInfoGetters, HasIntegerId {

    private final Integer id;
    private final int version;
    private final long createTimeMs;
    private final String createUser;
    private final long updateTimeMs;
    private final String updateUser;
    private final ZstdDictionaryKey dictionaryKey;
    private final String dictionaryUuid;
    //    private final ZstdDictionary zstdDictionary;
    private final ZstdDictionaryStatus status;

    public LinkedZstdDictionary(final Integer id,
                                final int version,
                                final long createTimeMs,
                                final String createUser,
                                final long updateTimeMs,
                                final String updateUser,
                                final String feedName,
                                final String streamTypeName,
                                final String childStreamTypeName,
//                                final int dictionaryVersion,
                                final String dictionaryUuid,
//                                final byte[] dictionaryBytes,
                                final ZstdDictionaryStatus status) {
        this(id,
                version,
                createTimeMs,
                createUser,
                updateTimeMs,
                updateUser,
                ZstdDictionaryKey.of(feedName, streamTypeName, childStreamTypeName),
                dictionaryUuid,
//                new ZstdDictionary(Objects.requireNonNull(dictionaryUuid), dictionaryBytes),
                status);
    }

    public LinkedZstdDictionary(final Integer id,
                                final int version,
                                final long createTimeMs,
                                final String createUser,
                                final long updateTimeMs,
                                final String updateUser,
                                final ZstdDictionaryKey dictionaryKey,
                                final String dictionaryUuid,
//                                final ZstdDictionary zstdDictionary,
                                final ZstdDictionaryStatus status) {
        this.id = id;
        this.version = version;
        this.createTimeMs = createTimeMs;
        this.createUser = createUser;
        this.updateTimeMs = updateTimeMs;
        this.updateUser = updateUser;
        this.dictionaryKey = Objects.requireNonNull(dictionaryKey);
        this.dictionaryUuid = Objects.requireNonNull(dictionaryUuid);
        // Make sure the uuid is parseable
        final UUID ignored = UUID.fromString(dictionaryUuid);
//        this.zstdDictionary = Objects.requireNonNull(zstdDictionary);
        this.status = Objects.requireNonNull(status);
    }

    public Integer getId() {
        return id;
    }

    public int getVersion() {
        return version;
    }

    @Override
    public Long getCreateTimeMs() {
        return createTimeMs;
    }

    @Override
    public String getCreateUser() {
        return createUser;
    }

    @Override
    public Long getUpdateTimeMs() {
        return updateTimeMs;
    }

    @Override
    public String getUpdateUser() {
        return updateUser;
    }

//    public ZstdDictionary getZstdDictionary() {
//        return zstdDictionary;
//    }

    public ZstdDictionaryKey getDictionaryKey() {
        return dictionaryKey;
    }

    public ZstdDictionaryStatus getStatus() {
        return status;
    }

    public String getFeedName() {
        return dictionaryKey.feedName();
    }

    public String getChildStreamType() {
        return dictionaryKey.childStreamType();
    }

    public String getStreamTypeName() {
        return dictionaryKey.streamTypeName();
    }

    public String getUuid() {
        return dictionaryUuid;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final LinkedZstdDictionary that = (LinkedZstdDictionary) o;
        return version == that.version
               && createTimeMs == that.createTimeMs
               && updateTimeMs == that.updateTimeMs
               && status == that.status
               && Objects.equals(id, that.id)
               && Objects.equals(createUser, that.createUser)
               && Objects.equals(updateUser, that.updateUser)
               && Objects.equals(dictionaryKey, that.dictionaryKey)
               && Objects.equals(dictionaryUuid, that.dictionaryUuid);
//               && Objects.equals(zstdDictionary, that.zstdDictionary);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                id,
                version,
                createTimeMs,
                createUser,
                updateTimeMs,
                updateUser,
                dictionaryKey,
                dictionaryUuid,
//                zstdDictionary,
                status);
    }

    @Override
    public String toString() {
        return "LinkedZstdDictionary{" +
               "id=" + id +
               ", version=" + version +
               ", createTimeMs=" + createTimeMs +
               ", createUser='" + createUser + '\'' +
               ", updateTimeMs=" + updateTimeMs +
               ", updateUser='" + updateUser + '\'' +
               ", dictionaryKey=" + dictionaryKey +
               ", dictionaryUuid=" + dictionaryUuid +
//               ", zstdDictionary=" + zstdDictionary +
               ", status=" + status +
               '}';
    }
}
