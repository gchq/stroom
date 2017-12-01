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

package stroom.logging;

import event.logging.Query;
import stroom.entity.shared.BaseCriteria;
import stroom.entity.shared.BaseResultList;

public interface DocumentEventLog {
//    void create(Object entity);

    void create(Object entity, Throwable ex);

//    void create(String entityType, String entityName);

    void create(String entityType, String entityName, Throwable ex);

//    void update(Object before, Object after);



//    void copy(Object before, Object after);

    void copy(Object before, Object after, Throwable ex);

//    void move(Object before, Object after);

    void move(Object before, Object after, Throwable ex);

    void rename(Object before, Object after, Throwable ex);

//    void delete(Object entity);

    void delete(Object entity, Throwable ex);






    void update(Object before, Object after, Throwable ex);


    void view(Object entity);

    void view(Object entity, Throwable ex);

    void delete(BaseCriteria criteria, Query query, Long size);

    void delete(BaseCriteria criteria, Query query, Throwable ex);

    void search(BaseCriteria criteria, Query query, BaseResultList<?> results);

    void search(BaseCriteria criteria, Query query, Throwable ex);

    void searchSummary(BaseCriteria criteria, Query query, BaseResultList<?> results);

    void searchSummary(BaseCriteria criteria, Query query, Throwable ex);
}
