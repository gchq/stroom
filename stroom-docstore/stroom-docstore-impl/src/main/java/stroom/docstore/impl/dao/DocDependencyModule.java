/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.docstore.impl.dao;

import stroom.docstore.api.DocDependencyService;

import com.google.inject.AbstractModule;

/**
 * Guice module for the document dependency tracking feature.
 * Binds the DAO and service.
 * <p>
 * The dependency store is kept current by direct calls from the document write
 * paths (see {@link stroom.docstore.impl.StoreImpl}) rather than via cluster-wide
 * entity events, so that the DB is only mutated once per change rather than once
 * per node.
 */
public class DocDependencyModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(DocDependencyDao.class);
        bind(DocDependencyService.class).to(DocDependencyServiceImpl.class);
    }
}
