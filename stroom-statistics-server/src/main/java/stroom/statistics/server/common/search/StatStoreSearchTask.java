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

package stroom.statistics.server.common.search;

import java.io.Serializable;
import java.util.Map;

import stroom.query.shared.CoprocessorSettings;
import stroom.query.shared.Search;
import stroom.statistics.shared.StatisticStoreEntity;
import stroom.util.shared.VoidResult;
import stroom.util.task.ServerTask;

public class StatStoreSearchTask extends ServerTask<VoidResult>implements Serializable {
    private static final long serialVersionUID = 3554750052786267074L;

    private final String searchName;
    private final Search search;
    private final StatisticStoreEntity entity;
    private final Map<Integer, CoprocessorSettings> coprocessorMap;
    private volatile transient StatStoreSearchResultCollector resultCollector;

    public StatStoreSearchTask(final String userToken, final String searchName,
            final Search search, final StatisticStoreEntity entity,
            final Map<Integer, CoprocessorSettings> coprocessorMap) {
        super(null, userToken);
        this.searchName = searchName;
        this.search = search;
        this.entity = entity;
        this.coprocessorMap = coprocessorMap;
    }

    public String getSearchName() {
        return searchName;
    }

    public Search getSearch() {
        return search;
    }

    public StatisticStoreEntity getEntity() {
        return entity;
    }

    public Map<Integer, CoprocessorSettings> getCoprocessorMap() {
        return coprocessorMap;
    }

    public StatStoreSearchResultCollector getResultCollector() {
        return resultCollector;
    }

    public void setResultCollector(final StatStoreSearchResultCollector resultCollector) {
        this.resultCollector = resultCollector;
    }
}
