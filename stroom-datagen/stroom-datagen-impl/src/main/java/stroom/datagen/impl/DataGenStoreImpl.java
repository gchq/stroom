/*
 * Copyright 2026 Crown Copyright
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

package stroom.datagen.impl;

import stroom.datagen.shared.DataGenDoc;
import stroom.datagen.shared.DataGenDoc.Builder;
import stroom.docref.DocRef;
import stroom.docstore.api.AbstractDocumentStore;
import stroom.docstore.api.DependencyRemapFunction;
import stroom.docstore.api.StoreFactory;
import stroom.docstore.api.UniqueNameUtil;
import stroom.feed.shared.FeedDoc;
import stroom.security.api.SecurityContext;
import stroom.util.logging.LogUtil;
import stroom.util.shared.EntityServiceException;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Set;

/**
 * Document store for {@link DataGenDoc}, adding data-generator specific behaviour on top of
 * {@link AbstractDocumentStore}: validation of the destination feed on write, and declaration of
 * the doc's dependency on that feed.
 */
@Singleton
class DataGenStoreImpl
        extends AbstractDocumentStore<DataGenDoc>
        implements DataGenStore {

    @Inject
    DataGenStoreImpl(final StoreFactory storeFactory,
                     final SecurityContext securityContext,
                     final DataGenSerialiser serialiser) {
        super(storeFactory,
                securityContext,
                serialiser,
                DataGenDoc.TYPE,
                DataGenDoc::builder,
                DataGenDoc::copy);
    }

    @Override
    public DocRef copyDocument(final DocRef docRef,
                               final String name,
                               final boolean makeNameUnique,
                               final Set<String> existingNames) {
        final String newName = UniqueNameUtil.getCopyName(name, makeNameUnique, existingNames);
        final DataGenDoc document = super.readDocument(docRef);
        return getStore().createDocument(newName,
                (uuid, docName, version, createTime, updateTime, createUser, updateUser) -> {
                    final Builder builder = document
                            .copy()
                            .uuid(uuid)
                            .name(docName)
                            .version(version)
                            .createTimeMs(createTime)
                            .updateTimeMs(updateTime)
                            .createUser(createUser)
                            .updateUser(updateUser);

                    return builder.build();
                });
    }

    @Override
    public DataGenDoc writeDocument(final DataGenDoc document) {
        validateFeed(document.getFeed());
        return super.writeDocument(document);
    }

    /**
     * A null feed is allowed - a doc can be saved before it has been fully configured - but a feed of
     * the wrong type is not. The UI restricts the picker to feeds, so a bad type can only arrive over
     * the REST API, and it would not fail until the generator next ran.
     */
    private void validateFeed(final DocRef feed) {
        if (feed != null && !FeedDoc.TYPE.equals(feed.getType())) {
            throw new EntityServiceException(LogUtil.message(
                    "The destination feed must be of type '{}' but was of type '{}'",
                    FeedDoc.TYPE,
                    feed.getType()));
        }
    }

    /**
     * Declares the destination feed as this doc's one dependency. The store uses this for two
     * separate things, and returning null (the inherited default) silently disables both:
     * <ol>
     *     <li>Rewriting the feed reference when a data generator is imported or copied and the feed
     *         it points at is given a new UUID. Without it an imported generator still refers to the
     *         source system's feed.</li>
     *     <li>Recording the generator-to-feed edge in the dependency graph, which is what the
     *         explorer's dependency view and delete-time warnings are built from.</li>
     * </ol>
     */
    @Override
    protected DependencyRemapFunction<DataGenDoc> getDependencyRemapFunction() {
        return (doc, dependencyRemapper) -> {
            final Builder builder = doc.copy();
            if (doc.getFeed() != null) {
                builder.feed(dependencyRemapper.remap(doc.getFeed()));
            }
            return builder.build();
        };
    }
}
