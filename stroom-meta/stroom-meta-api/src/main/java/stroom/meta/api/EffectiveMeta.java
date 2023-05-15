package stroom.meta.api;

import stroom.meta.shared.Meta;
import stroom.util.date.DateUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.Instant;
import java.util.NavigableSet;
import java.util.Objects;

// TODO: 08/02/2023 May want to consider combining this with SimpleMeta class
// Could maybe have used the EffectiveStream class but this has feed/type in which aids
// debugging.
public class EffectiveMeta implements Comparable<EffectiveMeta> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(EffectiveMeta.class);

    private final long id;
    private final String feedName;
    private final String typeName;
    private final long effectiveMs;
    private final int hashcode;

    public EffectiveMeta(final long id,
                         final String feedName,
                         final String typeName,
                         final long effectiveMs) {
        this.id = id;
        // Many EffectiveMeta objects will share the same feed/type (which help with debugging)
        // so intern them to reduce mem use.
        this.feedName = Objects.requireNonNull(feedName).intern();
        this.typeName = Objects.requireNonNull(typeName).intern();
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
     * Only used as a threshold in navigable set navigation where we only care about the effectiveMs
     */
    private EffectiveMeta(final long effectiveMs) {
        this.id = -1;
        this.feedName = null;
        this.typeName = null;
        this.effectiveMs = effectiveMs;

        // feed/type will always be the same for a given id so ignore them in equals/hash
        this.hashcode = buildHash(id, effectiveMs);
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
        return Long.compare(effectiveMs, o.effectiveMs);
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

    /**
     * Find the latest {@link EffectiveMeta} in effectiveMetaSet that is before timeMs.
     */
    public static EffectiveMeta findLatestBefore(final long timeMs,
                                                 final NavigableSet<EffectiveMeta> effectiveMetaSet) {
        return effectiveMetaSet.floor(new EffectiveMeta(timeMs));
    }

    private static int buildHash(final long id,
                                 final long effectiveMs) {
        return Objects.hash(id, effectiveMs);
    }
}
