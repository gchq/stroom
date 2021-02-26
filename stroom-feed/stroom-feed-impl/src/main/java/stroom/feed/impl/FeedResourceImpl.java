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

import stroom.docref.DocRef;
import stroom.docstore.api.DocumentResourceHelper;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.feed.api.FeedStore;
import stroom.feed.shared.FeedDoc;
import stroom.feed.shared.FeedResource;
import stroom.util.shared.EntityServiceException;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Provider;

@AutoLogged
class FeedResourceImpl implements FeedResource {

    private static final List<String> SUPPORTED_ENCODINGS;

    static {
        final List<String> list = new ArrayList<>();
        list.add("UTF-8");
        list.add("UTF-16LE");
        list.add("UTF-16BE");
        list.add("UTF-32LE");
        list.add("UTF-32BE");
        list.add("ASCII");
        list.add("");

        list.addAll(Charset.availableCharsets().keySet());

        SUPPORTED_ENCODINGS = list;
    }

    private final Provider<FeedStore> feedStoreProvider;
    private final Provider<DocumentResourceHelper> documentResourceHelperProvider;

    @Inject
    FeedResourceImpl(final Provider<FeedStore> feedStoreProvider,
                     final Provider<DocumentResourceHelper> documentResourceHelperProvider) {
        this.feedStoreProvider = feedStoreProvider;
        this.documentResourceHelperProvider = documentResourceHelperProvider;
    }

    @Override
    public FeedDoc fetch(final String uuid) {
        return documentResourceHelperProvider.get().read(feedStoreProvider.get(), getDocRef(uuid));
    }

    @Override
    public FeedDoc update(final String uuid, final FeedDoc doc) {
        if (doc.getUuid() == null || !doc.getUuid().equals(uuid)) {
            throw new EntityServiceException("The document UUID must match the update UUID");
        }
        return documentResourceHelperProvider.get().update(feedStoreProvider.get(), doc);
    }

    private DocRef getDocRef(final String uuid) {
        return DocRef.builder()
                .uuid(uuid)
                .type(FeedDoc.DOCUMENT_TYPE)
                .build();
    }

    @Override
    public List<String> fetchSupportedEncodings() {
        return SUPPORTED_ENCODINGS;
    }
}
