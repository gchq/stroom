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

import stroom.util.shared.Document;

/**
 * Interface for any document that defines a PlanB-backed store.
 *
 * <p>Implemented by {@link PlanBDoc} (state, temporal-state, range-state,
 * session, histogram, metric) and by
 * {@link stroom.pathways.shared.TracesDoc} (trace stores), both
 * via {@link AbstractPlanBDoc}.
 *
 * <p>Extends {@link Document} to inherit {@code getType()}, {@code getUuid()},
 * {@code getName()}, and the default {@code asDocRef()} implementation.
 * Infrastructure classes ({@code ShardManager}, {@code MergeProcessor},
 * {@code PlanBDocCache}, etc.) should program to this interface so they remain
 * independent of any concrete subtype.
 */
public interface PlanBDocument extends Document {

    /**
     * Returns the human-readable description for this store document, or
     * {@code null} if none has been set.
     */
    String getDescription();

    /**
     * Returns the {@link StateType} discriminator that determines which PlanB
     * store implementation is used (e.g. {@code STATE}, {@code TRACE}, …).
     */
    StateType getStateType();

    /**
     * Returns the settings object for this store, or {@code null} if no
     * settings have been configured yet.
     */
    AbstractPlanBSettings getSettings();

    /**
     * Returns the number of shards configured for the shared-file-store, or
     * {@code 0} if no shared-file-store is configured.
     */
    int getShardCount();

    /**
     * Returns the filesystem path of the shared-file-store, or {@code null}
     * if no shared-file-store is configured.
     */
    String getSharedPath();
}
