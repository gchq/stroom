/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.dashboard;

import stroom.dashboard.shared.FindQueryCriteria;
import stroom.dashboard.shared.QueryEntity;
import stroom.entity.DocumentEntityService;
import stroom.entity.FindService;

import java.util.List;

public interface QueryService extends DocumentEntityService<QueryEntity>, FindService<QueryEntity, FindQueryCriteria> {
    void clean(String user, boolean favourite, Integer oldestId, long oldestCrtMs);

    List<String> getUsers(boolean favourite);

    Integer getOldestId(String user, boolean favourite, int retain);
}
