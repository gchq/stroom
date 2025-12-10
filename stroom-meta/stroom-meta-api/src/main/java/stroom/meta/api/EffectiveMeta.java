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

package stroom.meta.api;

import stroom.meta.shared.Meta;
import stroom.util.date.DateUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.Instant;
import java.util.Comparator;
import java.util.NavigableSet;
import java.util.Objects;

/**
 * Represents a stream with an effective time. Used for determining which reference
 * stream to perform a lookup against.
 */
public class EffectiveMeta implements Comparable<EffectiveMeta> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(EffectiveMeta.class);
    private static final String DUMMY = "DUMMY";

    // We have to be able to compare EffectiveMeta items on their effective time, so that we
    // can get the latest EffectiveMeta before time X. Include stream ID also, so that we have
    // deterministic ordering if two streams have the same effectiveMs.
    // IMPORTANT: compareTo is used for equality checking of items in the equals method of a TreeSet
    private static final Comparator<EffectiveMeta> TIME_THEN_ID_COMPARATOR = Comparator.comparingLong(
                    (EffectiveMeta effectiveMeta) -> effectiveMeta.getEffectiveMs())
            .thenComparing(EffectiveMeta::getId);

    private final long id;
    // feed/type are mainly here to aid with debugging and logging
    private final String feedName;
    private final String typeName;
    private final long effectiveMs;
    private final int hashcode;

    public EffectiveMeta(final long id,
                         final String feedName,
                         final String typeName,
                         final long effectiveMs) {
        this.id = id;
        this.feedName = Objects.requireNonNull(feedName);
        this.typeName = Objects.requireNonNull(typeName);
        this.effectiveMs = effectiveMs;

        // feed/type will always be the same for a given id so ignore them in equals/hash
        this.hashcode = buildHash(id, effectiveMs);
    }

    public EffectiveMeta(final long id,
                         final String feedName,
                         final String typeName,
                         final Instant effectiveTime) {
        this(id,
                feedName,
                typeName,
                Objects.requireNonNull(effectiveTime).toEpochMilli());
    }

    /**
     * Only to be used as a threshold in {@link NavigableSet#floor(Object)} navigation
     * where we only care about the effectiveMs.
     */
    static EffectiveMeta asFloorKey(final long effectiveMs) {
        // Use Long.MAX_VALUE so that the floor() when used with TIME_THEN_ID_COMPARATOR
        // will return the item with an effectiveMs equal to the passed effectiveMs
        return new EffectiveMeta(Long.MAX_VALUE, DUMMY, DUMMY, effectiveMs);
    }

    public EffectiveMeta(final Meta meta) {
        this(meta.getId(),
                meta.getFeedName(),
                meta.getTypeName(),
                getEffectiveMs(meta));
    }

    private static long getEffectiveMs(final Meta meta) {
        final long effectiveMs;
        // When there is not effectiveMs for a stream we have to fall back on the mandatory createMs
        if (meta.getEffectiveMs() != null) {
            effectiveMs = meta.getEffectiveMs();
        } else {
            LOGGER.debug(() -> "Using createMs for effectiveMs for id " + meta.getId());
            effectiveMs = meta.getCreateMs();
        }
        return effectiveMs;
    }

    public static EffectiveMeta of(final long id,
                                   final String feedName,
                                   final String typeName,
                                   final Instant effectiveTime) {
        return new EffectiveMeta(id, feedName, typeName, effectiveTime.toEpochMilli());
    }

    public long getId() {
        return id;
    }

    public String getFeedName() {
        return feedName;
    }

    public String getTypeName() {
        return typeName;
    }

    public long getEffectiveMs() {
        return effectiveMs;
    }

    @JsonIgnore
    public Instant getEffectiveTime() {
        return Instant.ofEpochMilli(effectiveMs);
    }

    @Override
    public String toString() {
        return "EffectiveMeta{" +
                feedName + ":" +
                typeName + ":" +
                id +
                ", effectiveTime=" + DateUtil.createNormalDateTimeString(effectiveMs) +
                '}';
    }

    @Override
    public int compareTo(final EffectiveMeta o) {
        // IMPORTANT: compareTo is used for equality checking of items in the equals method of a TreeSet
        return TIME_THEN_ID_COMPARATOR.compare(this, o);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final EffectiveMeta that = (EffectiveMeta) o;
        return id == that.id
                && Objects.equals(effectiveMs, that.effectiveMs);
    }

    @Override
    public int hashCode() {
        return hashcode;
    }

    // TODO We could potentially do equals/hash on just the id given that an id can only be
    //  associated with one effectiveMs and id is unique.
    private static int buildHash(final long id,
                                 final long effectiveMs) {
        return Objects.hash(id, effectiveMs);
    }
}
