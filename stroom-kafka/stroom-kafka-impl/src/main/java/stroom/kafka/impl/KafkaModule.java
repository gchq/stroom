/*
 * Copyright 2018 Crown Copyright
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

import com.google.inject.AbstractModule;
import stroom.kafka.pipeline.KafkaProducerFactory;

public class KafkaModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(KafkaProducerFactory.class).to(KafkaProducerFactoryImpl.class);
        bind(KafkaConfigStore.class).to(KafkaConfigStoreImpl.class);
    }
}