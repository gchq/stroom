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

package stroom.pool.shared;

import stroom.docref.SharedObject;

public class PoolInfo implements SharedObject {
    private static final String DELIM = ",";

    private static final long serialVersionUID = 463047159587522512L;

    private String name;
    private Long lastAccessTime;
    private Integer inUse;
    private Integer inPool;
    private Long timeToIdleMs;
    private Long timeToLiveMs;
    private String maxObjectsPerKey;

    public static PoolInfo fromString(final String string) {
        final String[] arr = string.split(DELIM);
        final PoolInfo cacheInfo = new PoolInfo();
        cacheInfo.name = arr[0];
        cacheInfo.lastAccessTime = Long.parseLong(arr[1]);
        cacheInfo.inUse = Integer.parseInt(arr[2]);
        cacheInfo.inPool = Integer.parseInt(arr[3]);
        cacheInfo.timeToIdleMs = Long.parseLong(arr[7]);
        cacheInfo.timeToLiveMs = Long.parseLong(arr[8]);
        cacheInfo.maxObjectsPerKey = arr[9];
        return cacheInfo;
    }

    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public Long getLastAccessTime() {
        return lastAccessTime;
    }

    public void setLastAccessTime(final Long lastAccessTime) {
        this.lastAccessTime = lastAccessTime;
    }

    public Integer getInUse() {
        return inUse;
    }

    public void setInUse(final Integer inUse) {
        this.inUse = inUse;
    }

    public Integer getInPool() {
        return inPool;
    }

    public void setInPool(final Integer inPool) {
        this.inPool = inPool;
    }

    public Long getTimeToIdleMs() {
        return timeToIdleMs;
    }

    public void setTimeToIdleMs(final Long timeToIdleMs) {
        this.timeToIdleMs = timeToIdleMs;
    }

    public Long getTimeToLiveMs() {
        return timeToLiveMs;
    }

    public void setTimeToLiveMs(final Long timeToLiveMs) {
        this.timeToLiveMs = timeToLiveMs;
    }

    public String getMaxObjectsPerKey() {
        return maxObjectsPerKey;
    }

    public void setMaxObjectsPerKey(final String maxObjectsPerKey) {
        this.maxObjectsPerKey = maxObjectsPerKey;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append(name);
        sb.append(DELIM);
        sb.append(lastAccessTime);
        sb.append(DELIM);
        sb.append(inUse);
        sb.append(DELIM);
        sb.append(inPool);
        sb.append(DELIM);
        sb.append(timeToIdleMs);
        sb.append(DELIM);
        sb.append(timeToLiveMs);
        sb.append(DELIM);
        sb.append(maxObjectsPerKey);
        return sb.toString();
    }
}
