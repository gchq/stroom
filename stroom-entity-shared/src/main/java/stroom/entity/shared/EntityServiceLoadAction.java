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
 */

package stroom.entity.shared;

import stroom.query.api.v2.DocRef;

import java.util.Set;

public class EntityServiceLoadAction<E extends Entity> extends Action<E> {
    private static final long serialVersionUID = 800905016214418723L;

    private DocRef docRef;
    private Set<String> fetchSet;

    public EntityServiceLoadAction() {
        // Default constructor necessary for GWT serialisation.
    }

    public EntityServiceLoadAction(final DocRef docRef, final Set<String> fetchSet) {
        this.docRef = docRef;
        this.fetchSet = fetchSet;
    }

    public DocRef getDocRef() {
        return docRef;
    }

    public Set<String> getFetchSet() {
        return fetchSet;
    }

    @Override
    public String getTaskName() {
        return "Load: " + docRef;
    }
}
