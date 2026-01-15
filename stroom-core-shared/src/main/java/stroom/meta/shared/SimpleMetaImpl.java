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

package stroom.meta.shared;

import java.util.Objects;

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

//    @Override
//    @JsonIgnore
//    public Instant getCreateTime() {
//        return Instant.ofEpochMilli(createMs);
//    }
//
    @Override
    public Long getStatusMs() {
        return statusMs;
    }

//    @Override
//    @JsonIgnore
//    public Optional<Instant> getStatusTime() {
//        return Optional.ofNullable(statusMs)
//                .map(Instant::ofEpochMilli);
//    }
//
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
        return String.join(":", feedName, typeName, Long.toString(id));
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }


    // --------------------------------------------------------------------------------


    public static final class Builder {

        private long id;
        private String feedName;
        private String typeName;
        private Long statusMs;
        private long createMs;

        private Builder() {
        }

        private Builder(final SimpleMeta meta) {
            this.id = meta.getId();
            this.feedName = meta.getFeedName();
            this.typeName = meta.getTypeName();
            this.statusMs = meta.getStatusMs();
            this.createMs = meta.getCreateMs();
        }

        public Builder id(final long id) {
            this.id = id;
            return this;
        }

        public Builder feedName(final String feedName) {
            this.feedName = feedName;
            return this;
        }

        public Builder typeName(final String typeName) {
            this.typeName = typeName;
            return this;
        }

        public Builder statusMs(final Long statusMs) {
            this.statusMs = statusMs;
            return this;
        }

        public Builder createMs(final long createMs) {
            this.createMs = createMs;
            return this;
        }

        public SimpleMetaImpl build() {
            return new SimpleMetaImpl(
                    id,
                    feedName,
                    typeName,
                    statusMs,
                    createMs);
        }
    }
}
