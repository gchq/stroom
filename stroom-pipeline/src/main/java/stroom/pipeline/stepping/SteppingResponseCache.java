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

package stroom.pipeline.stepping;

import stroom.pipeline.shared.stepping.StepLocation;
import stroom.util.pipeline.scope.PipelineScoped;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

@PipelineScoped
public class SteppingResponseCache {
    private static final int MAX_CACHE_SIZE = 100;

    private final Map<StepLocation, StepData> locationMap = new HashMap<>();
    private Deque<StepLocation> recents = new ArrayDeque<>();

    StepData getStepData(final StepLocation location) {
        return locationMap.get(location);
    }

    void setStepData(final StepLocation location, final StepData stepData) {
        // Cleanup code to remove old data.
        final StepLocation existing = recents.peekLast();
        if (existing == null || !existing.equals(location)) {
            recents.add(location);

            if (recents.size() > MAX_CACHE_SIZE) {
                final StepLocation forRemoval = recents.pollFirst();
                locationMap.remove(forRemoval);
            }
        }

        locationMap.put(location, stepData);
    }
}
