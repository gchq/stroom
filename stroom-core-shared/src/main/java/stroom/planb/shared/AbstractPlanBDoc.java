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

package stroom.planb.shared;

import stroom.docstore.shared.AbstractDoc;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * Abstract base for all PlanB-backed documents (e.g. {@link PlanBDoc}, {@code TracesDoc}).
 * <p>
 * Holds the fields that are common across every PlanB document type:
 * {@code description}, {@code stateType}, and {@code settings}.
 * Infrastructure classes (ShardManager, ShardWriters, MergeProcessor, PlanBDocCache)
 * work against this type so they remain independent of any concrete subtype.
 */
public abstract class AbstractPlanBDoc extends AbstractDoc implements PlanBDocument {

    @JsonProperty
    private final String description;
    @JsonProperty
    private final StateType stateType;
    @JsonProperty
    private final AbstractPlanBSettings settings;

    protected AbstractPlanBDoc(
            final String type,
            final String uuid,
            final String name,
            final String version,
            final Long createTimeMs,
            final Long updateTimeMs,
            final String createUser,
            final String updateUser,
            final String description,
            final StateType stateType,
            final AbstractPlanBSettings settings) {
        super(type, uuid, name, version, createTimeMs, updateTimeMs, createUser, updateUser);
        this.description = description;
        this.stateType = stateType;
        this.settings = settings;
    }

    public String getDescription() {
        return description;
    }

    public StateType getStateType() {
        return stateType;
    }

    public AbstractPlanBSettings getSettings() {
        return settings;
    }

    @JsonIgnore
    public int getShardCount() {
        return settings instanceof final HasSharedFileStore s && s.getSharedFileStore() != null
                ? s.getSharedFileStore().getShardCount()
                : 0;
    }

    @JsonIgnore
    public String getSharedPath() {
        return settings instanceof final HasSharedFileStore s && s.getSharedFileStore() != null
                ? s.getSharedFileStore().getSharedPath()
                : null;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final AbstractPlanBDoc that = (AbstractPlanBDoc) o;
        return Objects.equals(description, that.description) &&
               stateType == that.stateType &&
               Objects.equals(settings, that.settings);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), description, stateType, settings);
    }


    // --------------------------------------------------------------------------------


    public abstract static class AbstractBuilder<D extends AbstractPlanBDoc, B extends AbstractBuilder<D, B>>
            extends AbstractDoc.AbstractBuilder<D, B> {

        protected String description;
        protected StateType stateType;
        protected AbstractPlanBSettings settings;

        protected AbstractBuilder() {
        }

        protected AbstractBuilder(final AbstractPlanBDoc doc) {
            super(doc);
            this.description = doc.description;
            this.stateType = doc.stateType;
            this.settings = doc.settings;
        }

        public B description(final String description) {
            this.description = description;
            return self();
        }

        public B stateType(final StateType stateType) {
            this.stateType = stateType;
            return self();
        }

        public B settings(final AbstractPlanBSettings settings) {
            this.settings = settings;
            return self();
        }
    }
}
