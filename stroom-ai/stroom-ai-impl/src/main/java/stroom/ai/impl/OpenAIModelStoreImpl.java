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

package stroom.ai.impl;

import stroom.ai.api.OpenAIModelStore;
import stroom.docref.DocRef;
import stroom.docstore.api.AbstractDocumentStore;
import stroom.docstore.api.DocFinder;
import stroom.docstore.api.StoreFactory;
import stroom.openai.shared.OpenAIModelDoc;
import stroom.security.api.SecurityContext;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;

@Singleton
public class OpenAIModelStoreImpl
        extends AbstractDocumentStore<OpenAIModelDoc>
        implements OpenAIModelStore {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(OpenAIModelStoreImpl.class);

    private final DocFinder docFinder;

    @Inject
    public OpenAIModelStoreImpl(
            final StoreFactory storeFactory,
            final SecurityContext securityContext,
            final OpenAIModelSerialiser serialiser,
            final DocFinder docFinder) {
        super(storeFactory,
                securityContext,
                serialiser,
                OpenAIModelDoc.TYPE,
                OpenAIModelDoc::builder,
                OpenAIModelDoc::copy);
        this.docFinder = docFinder;
    }

    @Override
    public List<DocRef> findByName(final String name) {
        return docFinder.findByName(getType(), name, false);
    }

    @Override
    public Optional<DocRef> findByUuid(final String uuid) {
        try {
            return docFinder.decorateIfExists(new DocRef(OpenAIModelDoc.TYPE, uuid));
        } catch (final RuntimeException e) {
            // Expected permission exception for some users.
            LOGGER.debug(e::getMessage, e);
        }
        return Optional.empty();
    }
}
