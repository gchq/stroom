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

package stroom.feed.impl;

import stroom.docstore.api.DocumentResourceHelper;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.feed.api.FeedStore;
import stroom.feed.shared.FeedDoc;
import stroom.feed.shared.FeedResource;
import stroom.util.shared.EntityServiceException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Provider;

@AutoLogged
class FeedResourceImpl implements FeedResource {

    private final Provider<FeedStore> feedStoreProvider;
    private final Provider<DocumentResourceHelper> documentResourceHelperProvider;
    private final List<String> supportedEncodings;

    @Inject
    FeedResourceImpl(final Provider<FeedStore> feedStoreProvider,
                     final Provider<DocumentResourceHelper> documentResourceHelperProvider) {
        this.feedStoreProvider = feedStoreProvider;
        this.documentResourceHelperProvider = documentResourceHelperProvider;
        final List<String> encodings = new ArrayList<>(feedStoreProvider.get().fetchSupportedEncodings());
        // Allow user to select no encoding
        encodings.add("");
        supportedEncodings = Collections.unmodifiableList(encodings);
    }

    @Override
    public FeedDoc fetch(final String uuid) {
        return documentResourceHelperProvider.get()
                .read(feedStoreProvider.get(), FeedDoc.getDocRef(uuid));
    }

    @Override
    public FeedDoc update(final String uuid, final FeedDoc doc) {
        if (doc.getUuid() == null || !doc.getUuid().equals(uuid)) {
            throw new EntityServiceException("The document UUID must match the update UUID");
        }
        return documentResourceHelperProvider.get().update(feedStoreProvider.get(), doc);
    }

    @Override
    public List<String> fetchSupportedEncodings() {
        return supportedEncodings;
    }
}
