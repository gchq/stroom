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

package stroom.logging;

import event.logging.Query;
import stroom.entity.shared.BaseCriteria;
import stroom.entity.shared.BaseEntity;
import stroom.entity.shared.BaseResultList;

public interface EntityEventLog {
    void create(BaseEntity entity);

    void create(BaseEntity entity, Exception ex);

    void create(String entityType, String entityName);

    void create(String entityType, String entityName, Exception ex);

    void update(BaseEntity before, BaseEntity after);

    void update(BaseEntity before, BaseEntity after, Exception ex);

    void move(BaseEntity before, BaseEntity after);

    void move(BaseEntity before, BaseEntity after, Exception ex);

    void delete(BaseEntity entity);

    void delete(BaseEntity entity, Exception ex);

    void view(BaseEntity entity);

    void view(BaseEntity entity, Exception ex);

    void delete(BaseCriteria criteria, Query query, Long size);

    void delete(BaseCriteria criteria, Query query, Exception ex);

    void download(BaseEntity entity, Exception ex);

    void search(BaseCriteria criteria, Query query, BaseResultList<?> results);

    void search(BaseCriteria criteria, Query query, Exception ex);

    void searchSummary(BaseCriteria criteria, Query query, BaseResultList<?> results);

    void searchSummary(BaseCriteria criteria, Query query, Exception ex);
}
