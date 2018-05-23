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

package stroom.entity.client;

import com.google.gwt.user.client.rpc.AsyncCallback;
import stroom.entity.shared.BaseCriteria;
import stroom.entity.shared.BaseResultList;
import stroom.docref.SharedObject;

public interface EntityServiceAsync<E extends SharedObject, C extends BaseCriteria> {
    void find(C criteria, AsyncCallback<BaseResultList<E>> callback);

    void load(E entity, AsyncCallback<E> callback);

    void loadById(long id, AsyncCallback<E> callback);

    void loadByName(String name, AsyncCallback<E> callback);

    void save(E entity, AsyncCallback<E> callback);

    void saveAs(E entity, String name, AsyncCallback<E> callback);

    void delete(E entity, AsyncCallback<Boolean> callback);
}
