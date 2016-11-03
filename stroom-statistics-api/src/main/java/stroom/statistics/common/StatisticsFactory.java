/*
 * Copyright 2016 Crown Copyright
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

package stroom.statistics.common;

import java.util.List;
import java.util.Set;

public interface StatisticsFactory {
    void initStatisticEventStoreBeanNames();

    /**
     * @return An event store class representing the default engines which are
     *         defined by the property represented by
     *         STROOM_STATISTIC_ENGINES_PROPERTY_NAME
     */
    Statistics instance();

    /**
     * Returns a {@link Statistics} object backed by the specified statistics
     * engine
     *
     * @param engineName
     *            The required statistics engine to use
     * @return A {@link Statistics} object
     */
    Statistics instance(String engineName);

    /**
     * Returns a {@link Statistics} object backed by the specified statistics
     * engines
     *
     * @param engineNames
     *            The required statistics engines to use
     * @return A {@link Statistics} object
     */
    Statistics instance(List<String> engineNames);

    Set<String> getAllEngineNames();
}
