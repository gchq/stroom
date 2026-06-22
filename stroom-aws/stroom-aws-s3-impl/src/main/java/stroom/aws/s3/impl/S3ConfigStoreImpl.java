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

package stroom.aws.s3.impl;

import stroom.aws.s3.shared.S3ClientConfig;
import stroom.aws.s3.shared.S3ConfigDoc;
import stroom.docref.DocRef;
import stroom.docstore.api.AbstractDocumentStore;
import stroom.docstore.api.StoreFactory;
import stroom.util.json.JsonUtil;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

@Singleton
class S3ConfigStoreImpl
        extends AbstractDocumentStore<S3ConfigDoc>
        implements S3ConfigStore {

    private final Provider<S3Config> s3ConfigProvider;

    @Inject
    S3ConfigStoreImpl(final StoreFactory storeFactory,
                      final Provider<S3Config> s3ConfigProvider,
                      final S3ConfigSerialiser serialiser) {
        super(storeFactory,
                serialiser,
                S3ConfigDoc.TYPE,
                S3ConfigDoc::builder,
                S3ConfigDoc::copy);
        this.s3ConfigProvider = s3ConfigProvider;
    }

    @Override
    public DocRef createDocument(final String name) {
        // create the document with some configurable skeleton content
        return getStore().createDocument(
                name,
                (uuid, docName, version, createTime, updateTime, createUser, updateUser) -> {

                    final String skeletonConfigText = s3ConfigProvider.get().getSkeletonConfigContent();

                    return new S3ConfigDoc(
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

    @Override
    public S3ConfigDoc writeDocument(final S3ConfigDoc document) {
        // Test serialisation.
        JsonUtil.readValue(document.getData(), S3ClientConfig.class);

        return super.writeDocument(document);
    }
}
