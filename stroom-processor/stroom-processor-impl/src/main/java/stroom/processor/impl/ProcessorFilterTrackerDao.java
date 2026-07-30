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

package stroom.processor.impl;

import stroom.processor.shared.ProcessorFilterTracker;

import java.util.Optional;

public interface ProcessorFilterTrackerDao {

    Optional<ProcessorFilterTracker> fetch(int id);

    /**
     * Update the tracker. Updates are subject to optimistic locking on version so the version of the supplied
     * tracker is incremented to match the DB once the update is committed, allowing the same instance to be
     * updated more than once.
     *
     * @param processorFilterTracker The tracker to update. Its version will be incremented on success.
     * @return The number of rows updated.
     */
    int update(ProcessorFilterTracker processorFilterTracker);
}
