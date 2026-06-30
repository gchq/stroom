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

/**
 * Controls how archive shard directories are labelled.
 *
 * <p>This enum is GWT-compiled (used in the shared module) so it must not
 * reference {@code java.time.*}. The time-bucketing logic
 * ({@code label}, {@code bucketEnd}, {@code detect}) lives in the
 * server-side {@code ArchivalGranularityUtil} in {@code stroom-planb-impl}.
 */
public enum ArchivalGranularity {
    HOUR,
    DAY,
    WEEK
}
