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

package stroom.docstore.impl;

import stroom.docstore.api.DocumentResourceHelper;
import stroom.docstore.api.Serialiser2Factory;
import stroom.docstore.api.StoreFactory;
import stroom.event.logging.api.DocumentEventLog;
import stroom.security.api.SecurityContext;

import com.google.inject.AbstractModule;

public class DocStoreModule extends AbstractModule {

    @Override
    protected void configure() {
        requireBinding(SecurityContext.class);
        requireBinding(Persistence.class);
        requireBinding(DocumentEventLog.class);

        bind(DocumentResourceHelper.class).to(DocumentResourceHelperImpl.class);
        bind(StoreFactory.class).to(StoreFactoryImpl.class);
        bind(Serialiser2Factory.class).to(Serialiser2FactoryImpl.class);
    }
}
