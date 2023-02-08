package stroom.meta.shared;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

// TODO: 08/02/2023 May want to consider combining this with EffectiveMeta class

/**
 * A lighter weight version of {@link Meta} with only key fields.
 */
public class SimpleMetaImpl implements SimpleMeta {

    private final long id;
    private final String typeName;
    private final String feedName;
    private final long createMs;
    private final Long statusMs;

    public SimpleMetaImpl(final long id,
                          final String typeName,
                          final String feedName,
                          final long createMs,
                          final Long statusMs) {
        this.id = id;
        this.typeName = Objects.requireNonNull(typeName);
        this.feedName = Objects.requireNonNull(feedName);
        this.createMs = createMs;
        this.statusMs = statusMs;
    }

    public static SimpleMeta from(final Meta meta) {
        return new SimpleMetaImpl(
                meta.getId(),
                meta.getTypeName(),
                meta.getFeedName(),
                meta.getCreateMs(),
                meta.getStatusMs());
    }

    @Override
    public long getId() {
        return id;
    }

    @Override
    public String getTypeName() {
        return typeName;
    }

    @Override
    public String getFeedName() {
        return feedName;
    }

    @Override
    public long getCreateMs() {
        return createMs;
    }

    @Override
    @JsonIgnore
    public Instant getCreateTime() {
        return Instant.ofEpochMilli(createMs);
    }

    @Override
    public Long getStatusMs() {
        return statusMs;
    }

    @Override
    @JsonIgnore
    public Optional<Instant> getStatusTime() {
        return Optional.ofNullable(statusMs)
                .map(Instant::ofEpochMilli);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final SimpleMetaImpl meta = (SimpleMetaImpl) o;
        return id == meta.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return id + " - " + feedName + " - " + typeName;
    }
}
