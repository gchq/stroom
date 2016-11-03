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

package stroom.statistics.shared.common.engines;

import java.util.List;

import stroom.util.shared.SharedObject;

public class FetchStatisticsEnginesResults implements SharedObject, Comparable<FetchStatisticsEnginesResults> {
    private static final long serialVersionUID = -249111056436304051L;

    private List<String> engines;

    public FetchStatisticsEnginesResults() {
        // Default constructor necessary for GWT serialisation.
    }

    public FetchStatisticsEnginesResults(final List<String> engines) {
        this.engines = engines;
    }

    public void setEngines(final List<String> engines) {
        this.engines = engines;
    }

    public List<String> getEngines() {
        return engines;
    }

    @Override
    public int compareTo(final FetchStatisticsEnginesResults o) {
        if (engines.size() == o.getEngines().size()) {
            if (engines.containsAll(o.getEngines())) {
                return 0;
            } else {
                return 1;
            }
        } else {
            return engines.size() - o.getEngines().size();
        }
    }
}
