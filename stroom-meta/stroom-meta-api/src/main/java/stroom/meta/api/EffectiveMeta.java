package stroom.meta.api;

import stroom.meta.shared.Meta;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.time.Instant;
import java.util.Objects;

// TODO: 08/02/2023 May want to consider combining this with SimpleMeta class
// Could maybe have used the EffectiveStream class but this has feed/type in which aids
// debugging.
public class EffectiveMeta {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(EffectiveMeta.class);

    private final long id;
    private final String feedName;
    private final String typeName;
    private final long effectiveMs;

    public EffectiveMeta(final long id,
                         final String feedName,
                         final String typeName,
                         final long effectiveMs) {
        this.id = id;
        this.feedName = feedName;
        this.typeName = typeName;
        this.effectiveMs = effectiveMs;
    }

    public EffectiveMeta(final Meta meta) {
        this.id = meta.getId();
        this.feedName = Objects.requireNonNull(meta.getFeedName());
        this.typeName = Objects.requireNonNull(meta.getTypeName());
        if (meta.getEffectiveMs() != null) {
            this.effectiveMs = meta.getEffectiveMs();
        } else {
            LOGGER.debug("Using createMs for effectiveMs for id {}", id);
            this.effectiveMs = meta.getCreateMs();
        }
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

    public Long getEffectiveMs() {
        return effectiveMs;
    }

    @Override
    public String toString() {
        return "EffectiveMetaData{" +
                "id=" + id +
                ", feedName='" + feedName + '\'' +
                ", typeName='" + typeName + '\'' +
                ", effectiveMs=" + effectiveMs + " (" + Instant.ofEpochMilli(effectiveMs) + ")" +
                '}';
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
                && Objects.equals(feedName, that.feedName)
                && Objects.equals(typeName, that.typeName)
                && Objects.equals(effectiveMs, that.effectiveMs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, feedName, typeName, effectiveMs);
    }
}
