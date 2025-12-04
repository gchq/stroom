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

package stroom.kafka.impl;

import stroom.docstore.api.ContentIndexable;
import stroom.docstore.api.DocumentActionHandlerBinder;
import stroom.explorer.api.ExplorerActionHandler;
import stroom.importexport.api.ImportExportActionHandler;
import stroom.kafka.api.KafkaProducerFactory;
import stroom.kafka.shared.KafkaConfigDoc;
import stroom.lifecycle.api.LifecycleBinder;
import stroom.util.RunnableWrapper;
import stroom.util.guice.GuiceUtil;
import stroom.util.guice.HasSystemInfoBinder;
import stroom.util.shared.Clearable;

import com.google.inject.AbstractModule;
import jakarta.inject.Inject;

public class KafkaConfigModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(KafkaProducerFactory.class).to(KafkaProducerFactoryImpl.class);
        bind(KafkaConfigStore.class).to(KafkaConfigStoreImpl.class);

        GuiceUtil.buildMultiBinder(binder(), ExplorerActionHandler.class)
                .addBinding(KafkaConfigStoreImpl.class);
        GuiceUtil.buildMultiBinder(binder(), ImportExportActionHandler.class)
                .addBinding(KafkaConfigStoreImpl.class);
        GuiceUtil.buildMultiBinder(binder(), ContentIndexable.class)
                .addBinding(KafkaConfigStoreImpl.class);

        DocumentActionHandlerBinder.create(binder())
                .bind(KafkaConfigDoc.TYPE, KafkaConfigStoreImpl.class);

        HasSystemInfoBinder.create(binder())
                .bind(KafkaProducerFactoryImpl.class);

        GuiceUtil.buildMultiBinder(binder(), Clearable.class)
                .addBinding(KafkaConfigDocCache.class);

        LifecycleBinder.create(binder())
                .bindShutdownTaskTo(KafkaProducerFactoryShutdown.class);
    }

    private static class KafkaProducerFactoryShutdown extends RunnableWrapper {

        @Inject
        KafkaProducerFactoryShutdown(final KafkaProducerFactoryImpl kafkaProducerFactory) {
            super(kafkaProducerFactory::shutdown);
        }
    }
}
