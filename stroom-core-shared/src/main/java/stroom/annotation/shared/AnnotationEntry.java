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

package stroom.annotation.shared;

import stroom.util.shared.AbstractBuilder;
import stroom.util.shared.UserRef;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class AnnotationEntry {

    @JsonProperty
    private final Long id;
    @JsonProperty
    private final Long entryTime;
    @JsonProperty
    private final UserRef entryUser;
    @JsonProperty
    private final Long updateTime;
    @JsonProperty
    private final UserRef updateUser;
    @JsonProperty
    private final AnnotationEntryType entryType;
    @JsonProperty
    private final EntryValue entryValue;
    @JsonProperty
    private final EntryValue previousValue;
    @JsonProperty
    private final boolean deleted;

    @JsonCreator
    public AnnotationEntry(@JsonProperty("id") final Long id,
                           @JsonProperty("entryTime") final Long entryTime,
                           @JsonProperty("entryUser") final UserRef entryUser,
                           @JsonProperty("updateTime") final Long updateTime,
                           @JsonProperty("updateUser") final UserRef updateUser,
                           @JsonProperty("entryType") final AnnotationEntryType entryType,
                           @JsonProperty("entryValue") final EntryValue entryValue,
                           @JsonProperty("previousValue") final EntryValue previousValue,
                           @JsonProperty("deleted") final boolean deleted) {
        this.id = id;
        this.entryTime = entryTime;
        this.entryUser = entryUser;
        this.entryType = entryType;
        this.entryValue = entryValue;
        this.updateTime = updateTime;
        this.updateUser = updateUser;
        this.previousValue = previousValue;
        this.deleted = deleted;
    }

    public Long getId() {
        return id;
    }

    public Long getEntryTime() {
        return entryTime;
    }

    public UserRef getEntryUser() {
        return entryUser;
    }

    public AnnotationEntryType getEntryType() {
        return entryType;
    }

    public EntryValue getEntryValue() {
        return entryValue;
    }

    public Long getUpdateTime() {
        return updateTime;
    }

    public UserRef getUpdateUser() {
        return updateUser;
    }

    public EntryValue getPreviousValue() {
        return previousValue;
    }

    public boolean isDeleted() {
        return deleted;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AnnotationEntry that = (AnnotationEntry) o;
        return deleted == that.deleted &&
               Objects.equals(id, that.id) &&
               Objects.equals(entryTime, that.entryTime) &&
               Objects.equals(entryUser, that.entryUser) &&
               Objects.equals(updateTime, that.updateTime) &&
               Objects.equals(updateUser, that.updateUser) &&
               entryType == that.entryType &&
               Objects.equals(entryValue, that.entryValue) &&
               Objects.equals(previousValue, that.previousValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id,
                entryTime,
                entryUser,
                updateTime,
                updateUser,
                entryType,
                entryValue,
                previousValue,
                deleted);
    }

    @Override
    public String toString() {
        return "AnnotationEntry{" +
               "id=" + id +
               ", entryTime=" + entryTime +
               ", entryUser=" + entryUser +
               ", updateTime=" + updateTime +
               ", updateUser=" + updateUser +
               ", entryType=" + entryType +
               ", entryValue=" + entryValue +
               ", previousValue=" + previousValue +
               ", deleted=" + deleted +
               '}';
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends AbstractBuilder<AnnotationEntry, AnnotationEntry.Builder> {

        private Long id;
        private Long entryTime;
        private UserRef entryUser;
        private Long updateTime;
        private UserRef updateUser;
        private AnnotationEntryType entryType;
        private EntryValue entryValue;
        private EntryValue previousValue;
        private boolean deleted;

        public Builder() {
        }

        public Builder(final AnnotationEntry doc) {
            this.id = doc.id;
            this.entryTime = doc.entryTime;
            this.entryUser = doc.entryUser;
            this.updateTime = doc.updateTime;
            this.updateUser = doc.updateUser;
            this.entryType = doc.entryType;
            this.entryValue = doc.entryValue;
            this.previousValue = doc.previousValue;
            this.deleted = doc.deleted;
        }

        public Builder id(final Long id) {
            this.id = id;
            return self();
        }

        public Builder entryTime(final Long entryTime) {
            this.entryTime = entryTime;
            return self();
        }

        public Builder entryUser(final UserRef entryUser) {
            this.entryUser = entryUser;
            return self();
        }

        public Builder updateTime(final Long updateTime) {
            this.updateTime = updateTime;
            return self();
        }

        public Builder updateUser(final UserRef updateUser) {
            this.updateUser = updateUser;
            return self();
        }

        public Builder entryType(final AnnotationEntryType entryType) {
            this.entryType = entryType;
            return self();
        }

        public Builder entryValue(final EntryValue entryValue) {
            this.entryValue = entryValue;
            return self();
        }

        public Builder previousValue(final EntryValue previousValue) {
            this.previousValue = previousValue;
            return self();
        }

        public Builder deleted(final boolean deleted) {
            this.deleted = deleted;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public AnnotationEntry build() {
            return new AnnotationEntry(
                    id,
                    entryTime,
                    entryUser,
                    updateTime,
                    updateUser,
                    entryType,
                    entryValue,
                    previousValue,
                    deleted);
        }
    }
}
