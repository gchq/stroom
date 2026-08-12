/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.search.elastic;

import stroom.docstore.api.AbstractDocumentStore;
import stroom.docstore.api.StoreFactory;
import stroom.search.elastic.shared.ElasticClusterDoc;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class ElasticClusterStoreImpl
        extends AbstractDocumentStore<ElasticClusterDoc>
        implements ElasticClusterStore {

    @Inject
    public ElasticClusterStoreImpl(
            final StoreFactory storeFactory,
            final ElasticClusterSerialiser serialiser) {
        super(storeFactory,
                serialiser,
                ElasticClusterDoc.TYPE,
                ElasticClusterDoc::builder,
                ElasticClusterDoc::copy);
    }
}
