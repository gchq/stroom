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
 *
 */

package stroom.refdata.offheapstore;

import java.time.Instant;
import java.util.Objects;

public class RefDataProcessingInfo {

    private final long createTimeEpochMs;
    private final long lastAccessedTimeEpochMs;
    private final long effectiveTimeEpochMs;
    private final ProcessingState processingState;

    public RefDataProcessingInfo(final long createTimeEpochMs,
                                 final long lastAccessedTimeEpochMs,
                                 final long effectiveTimeEpochMs,
                                 final ProcessingState processingState) {
        this.createTimeEpochMs = createTimeEpochMs;
        this.lastAccessedTimeEpochMs = lastAccessedTimeEpochMs;
        this.effectiveTimeEpochMs = effectiveTimeEpochMs;
        this.processingState = processingState;
    }

    public RefDataProcessingInfo cloneWithNewState(final ProcessingState newProcessingState, boolean touchLastAccessedTime) {

        long newLastAccessedTime;
        if (touchLastAccessedTime) {
            newLastAccessedTime = System.currentTimeMillis();
        } else {
            newLastAccessedTime = lastAccessedTimeEpochMs;
        }
        return new RefDataProcessingInfo(createTimeEpochMs, newLastAccessedTime, effectiveTimeEpochMs, newProcessingState);
    }

    public RefDataProcessingInfo updateLastAccessedTime() {
        return new RefDataProcessingInfo(createTimeEpochMs, System.currentTimeMillis(), effectiveTimeEpochMs, processingState);
    }

    public long getCreateTimeEpochMs() {
        return createTimeEpochMs;
    }

    public long getLastAccessedTimeEpochMs() {
        return lastAccessedTimeEpochMs;
    }

    public long getEffectiveTimeEpochMs() {
        return effectiveTimeEpochMs;
    }

    public ProcessingState getProcessingState() {
        return processingState;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final RefDataProcessingInfo that = (RefDataProcessingInfo) o;
        return createTimeEpochMs == that.createTimeEpochMs &&
                effectiveTimeEpochMs == that.effectiveTimeEpochMs &&
                processingState == that.processingState;
    }

    @Override
    public int hashCode() {

        return Objects.hash(createTimeEpochMs, effectiveTimeEpochMs, processingState);
    }

    @Override
    public String toString() {
        return "RefDataProcessingInfo{" +
                "createTimeEpochMs=" + Instant.ofEpochMilli(createTimeEpochMs).toString() +
                ", lastAccessedTimeEpochMs=" + Instant.ofEpochMilli(lastAccessedTimeEpochMs).toString() +
                ", effectiveTimeEpochMs=" + Instant.ofEpochMilli(effectiveTimeEpochMs).toString() +
                ", processingState=" + processingState +
                '}';
    }

}
