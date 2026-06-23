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

import stroom.docref.DocRef;
import stroom.docstore.api.AbstractDocumentStore;
import stroom.docstore.api.StoreFactory;
import stroom.kafka.shared.KafkaConfigDoc;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

@Singleton
class KafkaConfigStoreImpl
        extends AbstractDocumentStore<KafkaConfigDoc>
        implements KafkaConfigStore {

    private final Provider<KafkaConfig> kafkaConfigProvider;

    @Inject
    KafkaConfigStoreImpl(final StoreFactory storeFactory,
                         final Provider<KafkaConfig> kafkaConfigProvider,
                         final KafkaConfigSerialiser serialiser) {
        super(storeFactory,
                serialiser,
                KafkaConfigDoc.TYPE,
                KafkaConfigDoc::builder,
                KafkaConfigDoc::copy);
        this.kafkaConfigProvider = kafkaConfigProvider;
    }

    @Override
    public DocRef createDocument(final String name) {
        // create the document with some configurable skeleton content
        return getStore().createDocument(
                name,
                (uuid, docName, version, createTime, updateTime, createUser, updateUser) -> {

                    final String skeletonConfigText = kafkaConfigProvider.get().getSkeletonConfigContent();

                    return new KafkaConfigDoc(
                            uuid,
                            docName,
                            version,
                            createTime,
                            updateTime,
                            createUser,
                            updateUser,
                            "",
                            skeletonConfigText);
                });
    }
}
