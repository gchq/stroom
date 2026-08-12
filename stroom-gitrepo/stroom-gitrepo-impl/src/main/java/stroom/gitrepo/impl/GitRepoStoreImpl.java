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

package stroom.gitrepo.impl;

import stroom.docstore.api.AbstractDocumentStore;
import stroom.docstore.api.DependencyRemapFunction;
import stroom.docstore.api.StoreFactory;
import stroom.gitrepo.api.GitRepoStore;
import stroom.gitrepo.impl.db.jooq.tables.GitRepo;
import stroom.gitrepo.shared.GitRepoDoc;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class GitRepoStoreImpl
        extends AbstractDocumentStore<GitRepoDoc>
        implements GitRepoStore {

    @Inject
    GitRepoStoreImpl(final StoreFactory storeFactory,
                     final GitRepoSerialiser serialiser) {
        super(storeFactory,
                serialiser,
                GitRepoDoc.TYPE,
                GitRepoDoc::builder,
                GitRepoDoc::copy);
    }

    @Override
    protected DependencyRemapFunction<GitRepoDoc> getDependencyRemapFunction() {
        // No-op mapper — GitRepo docs track dependencies structurally but no fields need remapping.
        return (doc, dependencyRemapper) -> doc;
    }
}
