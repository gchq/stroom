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

package stroom.statistics.impl.hbase.entity;

import stroom.docstore.api.DocumentSerialiser2;
import stroom.docstore.api.Serialiser2;
import stroom.docstore.api.Serialiser2Factory;
import stroom.statistics.impl.hbase.shared.StroomStatsStoreDoc;

import jakarta.inject.Inject;

import java.io.IOException;
import java.util.Map;

public class StroomStatsStoreSerialiser implements DocumentSerialiser2<StroomStatsStoreDoc> {

    private final Serialiser2<StroomStatsStoreDoc> delegate;

    @Inject
    public StroomStatsStoreSerialiser(final Serialiser2Factory serialiser2Factory) {
        delegate = serialiser2Factory.createSerialiser(StroomStatsStoreDoc.class);
    }

    @Override
    public StroomStatsStoreDoc read(final Map<String, byte[]> data) throws IOException {
        return delegate.read(data);
    }

    @Override
    public Map<String, byte[]> write(final StroomStatsStoreDoc document) throws IOException {
        return delegate.write(document);
    }
}
