/*
 * Copyright 2024 Crown Copyright
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

package stroom.query.language.functions;

public interface StateProvider {

    /**
     * Get the state value for the supplied map and key at the given effective time.
     *
     * @return Never null. {@link ValNull#INSTANCE} if there is no value for the key, or a {@link ValErr}
     * describing the problem if the value could not be determined, so that a failure surfaces in the
     * expression result rather than being indistinguishable from an absent value. See gh-5689 and gh-5692.
     */
    Val getState(String map, String key, long effectiveTimeMs);
}
