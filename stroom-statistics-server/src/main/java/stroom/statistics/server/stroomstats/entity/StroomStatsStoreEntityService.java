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

package stroom.statistics.server.stroomstats.entity;

import stroom.entity.shared.DocumentEntityService;
import stroom.entity.shared.FindService;
import stroom.stats.shared.StroomStatsStoreEntity;

public interface StroomStatsStoreEntityService extends DocumentEntityService<StroomStatsStoreEntity>,
        FindService<StroomStatsStoreEntity, FindStroomStatsStoreEntityCriteria> {

    String FIELD_NAME_DATE_TIME = "Date Time";
    String FIELD_NAME_VALUE = "Value";

}
