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

package stroom.index.mock;

import stroom.index.impl.IndexShardWriter;
import stroom.index.impl.IndexShardWriterCache;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class MockIndexShardWriterCache implements IndexShardWriterCache {

    private final int maxDocumentCount;

    private final Map<Long, IndexShardWriter> openWritersByShardId = new ConcurrentHashMap<>();

    @Inject
    MockIndexShardWriterCache() {
        this(Integer.MAX_VALUE);
    }

    public MockIndexShardWriterCache(final int maxDocumentCount) {
        this.maxDocumentCount = maxDocumentCount;
    }

    @Override
    public Optional<IndexShardWriter> getIfPresent(final long indexShardId) {
        return Optional.ofNullable(openWritersByShardId.get(indexShardId));
    }

    @Override
    public IndexShardWriter getOrOpenWriter(final long indexShardId) {
        return openWritersByShardId.computeIfAbsent(indexShardId, k ->
                new MockIndexShardWriter(indexShardId, maxDocumentCount));
    }

    @Override
    public void flushAll() {
        openWritersByShardId.values().parallelStream().forEach(IndexShardWriter::flush);
    }

    @Override
    public void flush(final long indexShardId) {
        final IndexShardWriter indexShardWriter = openWritersByShardId.get(indexShardId);
        if (indexShardWriter != null) {
            indexShardWriter.flush();
        }
    }

    @Override
    public void sweep() {
    }

    public void close(final IndexShardWriter indexShardWriter) {
        indexShardWriter.close();
        openWritersByShardId.remove(indexShardWriter.getIndexShardId());
    }

    @Override
    public void delete(final long indexShardId) {
        openWritersByShardId.values().forEach(indexShardWriter -> {
            if (indexShardWriter.getIndexShardId() == indexShardId) {
                close(indexShardWriter);
            }
        });
    }

    public void shutdown() {
        openWritersByShardId.values().parallelStream().forEach(this::close);
    }

    public Map<Long, IndexShardWriter> getWriters() {
        return openWritersByShardId;
    }
}
