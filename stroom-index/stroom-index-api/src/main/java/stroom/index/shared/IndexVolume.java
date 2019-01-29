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

package stroom.index.shared;

import stroom.docref.SharedObject;
import stroom.util.shared.HasAuditInfo;

/**
 * Some path on the network where we can store stuff.
 */
public class IndexVolume implements HasAuditInfo, SharedObject {

    private static final long TEN_GB = 10 * 1024 * 1024 * 1024;
    private static final double NINETY_NINE_PERCENT = 0.99D;

    private Long id;

    private Long createTime;
    private Long updateTime;
    private String createUser;
    private String updateUser;

    private String path;
    private String nodeName;
    private Long bytesLimit;
    private static final double ONE_HUNDRED = 100D;
    private Long bytesUsed;
    private Long bytesFree;
    private Long bytesTotal;
    private Long statusMs;

    public IndexVolume() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCreateTimeMs() {
        return createTime;
    }

    public void setCreateTimeMs(Long createTime) {
        this.createTime = createTime;
    }

    public Long getUpdateTimeMs() {
        return updateTime;
    }

    public void setUpdateTimeMs(Long updateTime) {
        this.updateTime = updateTime;
    }

    @Override
    public String getCreateUser() {
        return createUser;
    }

    @Override
    public void setCreateUser(String createUser) {
        this.createUser = createUser;
    }

    @Override
    public String getUpdateUser() {
        return updateUser;
    }

    @Override
    public void setUpdateUser(String updateUser) {
        this.updateUser = updateUser;
    }

    public static class Builder {
        private final IndexVolume instance;

        public Builder(final IndexVolume instance) {
            this.instance = instance;
        }

        public Builder() {
            this(new IndexVolume());
        }

        // Replaces the copy function
        public Builder fromOriginal(final IndexVolume original) {
            instance.path = original.path;
            instance.nodeName = original.nodeName;
            instance.bytesLimit = original.bytesLimit;
            return this;
        }

        public Builder nodeName(final String value) {
            instance.setNodeName(value);
            return this;
        }

        public Builder path(final String value) {
            instance.setPath(value);
            return this;
        }

        public Builder bytesUsed(final Long value) {
            instance.setBytesUsed(value);
            return this;
        }

        public Builder bytesFree(final Long value) {
            instance.setBytesFree(value);
            return this;
        }

        public Builder bytesTotal(final Long value) {
            instance.setBytesTotal(value);
            return this;
        }

        public Builder statusMs(final Long value) {
            instance.setStatusMs(value);
            return this;
        }

        public IndexVolume build() {
            return instance;
        }
    }

    public String getNodeName() {
        return nodeName;
    }

    public void setNodeName(String nodeName) {
        this.nodeName = nodeName;
    }

    public String getPath() {
        return path;
    }

    public void setPath(final String path) {
        this.path = path;
    }

    public Long getBytesLimit() {
        return bytesLimit;
    }

    public void setBytesLimit(final Long bytesLimit) {
        this.bytesLimit = bytesLimit;
    }

    public boolean isFull() {
        // If we haven't established how many bytes are used on a volume then
        // assume it is not full (could be dangerous but worst case we will get
        // an IO error).
        if (bytesUsed == null || bytesTotal == null) {
            return false;
        }

        // If a byte limit has been set then ensure it is less than the total
        // number of bytes on the volume and if it is return whether the number
        // of bytes used are greater than this limit.
        if (bytesLimit != null && bytesLimit < bytesTotal) {
            return bytesUsed >= bytesLimit;
        }

        // No byte limit has been set by the user so establish the maximum size
        // that we will allow.
        // Choose the higher limit of either the total storage minus 10Gb or 99%
        // of total storage.
        final long minusOneGig = bytesTotal - TEN_GB;
        final long percentage = (long) (bytesTotal * NINETY_NINE_PERCENT);
        final long max = Math.max(minusOneGig, percentage);

        return bytesUsed >= max;
    }

    public Long getBytesUsed() {
        return bytesUsed;
    }

    public void setBytesUsed(final Long bytesUsed) {
        this.bytesUsed = bytesUsed;
    }

    public Long getBytesFree() {
        return bytesFree;
    }

    public void setBytesFree(final Long bytesFree) {
        this.bytesFree = bytesFree;
    }

    public Long getBytesTotal() {
        return bytesTotal;
    }

    public void setBytesTotal(final Long bytesTotal) {
        this.bytesTotal = bytesTotal;
    }

    public Long getStatusMs() {
        return statusMs;
    }

    public void setStatusMs(final Long statusMs) {
        this.statusMs = statusMs;
    }

    public Long getPercentUsed() {
        Long percent = null;
        if (bytesUsed != null && bytesTotal != null) {
            percent = Double.valueOf(((double) bytesUsed) / ((double) bytesTotal) * ONE_HUNDRED).longValue();
        }
        return percent;
    }
}
