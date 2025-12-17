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

import stroom.docrefinfo.api.DocRefDecorator;
import stroom.docstore.api.DocumentSerialiser2;
import stroom.docstore.api.Store;
import stroom.docstore.api.StoreFactory;
import stroom.docstore.shared.AbstractDoc;
import stroom.docstore.shared.AbstractDoc.AbstractBuilder;
import stroom.security.api.SecurityContext;
import stroom.util.entityevent.EntityEventBus;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.function.Supplier;

public class StoreFactoryImpl implements StoreFactory {

    private final Persistence persistence;
    private final EntityEventBus entityEventBus;
    private final SecurityContext securityContext;
    private final Provider<DocRefDecorator> docRefInfoServiceProvider;

    @Inject
    public StoreFactoryImpl(final Persistence persistence,
                            final EntityEventBus entityEventBus,
                            final SecurityContext securityContext,
                            final Provider<DocRefDecorator> docRefInfoServiceProvider) {
        this.persistence = persistence;
        this.entityEventBus = entityEventBus;
        this.securityContext = securityContext;
        this.docRefInfoServiceProvider = docRefInfoServiceProvider;
    }

    @Override
    public <D extends AbstractDoc> Store<D> createStore(final DocumentSerialiser2<D> serialiser,
                                                        final String type,
                                                        final Supplier<AbstractBuilder<D, ?>> builderSupplier) {
        return new StoreImpl<>(
                persistence,
                entityEventBus,
                securityContext,
                docRefInfoServiceProvider,
                serialiser,
                type,
                builderSupplier);
    }
}
