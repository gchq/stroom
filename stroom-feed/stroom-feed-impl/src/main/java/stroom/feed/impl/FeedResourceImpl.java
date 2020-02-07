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

import com.codahale.metrics.health.HealthCheck.Result;
import stroom.docref.DocRef;
import stroom.docstore.api.DocumentResourceHelper;
import stroom.feed.api.FeedStore;
import stroom.feed.shared.FeedDoc;
import stroom.feed.shared.FeedResource;
import stroom.util.HasHealthCheck;
import stroom.util.shared.RestResource;

import javax.inject.Inject;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

class FeedResourceImpl implements FeedResource, RestResource, HasHealthCheck {
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

    private final FeedStore feedStore;
    private final DocumentResourceHelper documentResourceHelper;

    @Inject
    FeedResourceImpl(final FeedStore feedStore,
                     final DocumentResourceHelper documentResourceHelper) {
        this.feedStore = feedStore;
        this.documentResourceHelper = documentResourceHelper;
    }

    @Override
    public FeedDoc read(final DocRef docRef) {
        return documentResourceHelper.read(feedStore, docRef);
    }

    @Override
    public FeedDoc update(final FeedDoc doc) {
        return documentResourceHelper.update(feedStore, doc);
    }

    @Override
    public List<String> fetchSupportedEncodings() {
        return SUPPORTED_ENCODINGS;
    }

    @Override
    public Result getHealth() {
        return Result.healthy();
    }
}