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

package stroom.ai.impl.tools;

import stroom.data.shared.DataType;
import stroom.data.shared.StreamTypeNames;
import stroom.data.store.api.DataService;
import stroom.data.store.api.InputStreamProvider;
import stroom.data.store.api.SegmentInputStream;
import stroom.data.store.api.Source;
import stroom.data.store.api.Store;
import stroom.feed.api.FeedProperties;
import stroom.meta.api.MetaService;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaRow;
import stroom.meta.shared.Status;
import stroom.pipeline.shared.FetchDataRequest;
import stroom.pipeline.shared.FetchDataRequest.DisplayMode;
import stroom.pipeline.shared.FetchDataResult;
import stroom.query.api.ExpressionItem;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;
import stroom.security.api.SecurityContext;
import stroom.security.shared.AppPermission;
import stroom.util.shared.Count;
import stroom.util.shared.ResultPage;

import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/// Streams for the stream tools' tests, held in memory: their meta, the records the data viewer gives, the bytes
/// the stream store holds, and their receipt headers, behind mocks of [MetaService], [DataService], [Store] and
/// [FeedProperties].
final class StreamWorld {

    static final String FEED = "ACME-EVENTS";

    final MetaService metaService = Mockito.mock(MetaService.class);
    final DataService dataService = Mockito.mock(DataService.class);
    final Store store = Mockito.mock(Store.class);
    final FeedProperties feedProperties = Mockito.mock(FeedProperties.class);
    final SecurityContext securityContext = Mockito.mock(SecurityContext.class);
    /// The streams, by id.
    final Map<Long, MetaRow> metas = new LinkedHashMap<>();
    /// What the data viewer gives for each record of a segmented stream, by stream id.
    final Map<Long, List<String>> records = new HashMap<>();
    /// The bytes of each raw stream's first part, by stream id.
    final Map<Long, byte[]> bytes = new HashMap<>();
    /// The bytes of each part of a raw stream of several parts, by stream id (else it has one part).
    final Map<Long, List<byte[]>> parts = new HashMap<>();
    /// The errors the data viewer gives for a stream, by id.
    final Map<Long, List<String>> errors = new HashMap<>();
    /// The receipt headers of each stream, by id.
    final Map<Long, String> headers = new HashMap<>();
    /// The encoding of each feed, by name; UTF-8 if not given.
    final Map<String, String> encodings = new HashMap<>();
    /// The longest a raw stream's text is given by the data viewer, as its page of characters.
    final int viewerPage = 1_000;
    /// The child streams the data viewer says each stream has.
    final Set<String> childTypes = new HashSet<>(Set.of(StreamTypeNames.META));

    StreamWorld() {
        // The user may view data, as reading a stream's text needs; a test takes it away to see the refusal.
        Mockito.when(securityContext.hasAppPermission(AppPermission.VIEW_DATA_PERMISSION)).thenReturn(true);
        Mockito.when(metaService.findRows(Mockito.any())).thenAnswer(invocation -> {
            final FindMetaCriteria criteria = invocation.getArgument(0);
            final List<MetaRow> found = metas.values().stream()
                    .filter(row -> matches(row.getMeta(), criteria.getExpression()))
                    .sorted(Comparator.comparingLong((MetaRow row) -> row.getMeta().getId()).reversed())
                    .toList();
            return new ResultPage<>(found);
        });
        Mockito.when(dataService.fetch(Mockito.any())).thenAnswer(invocation -> fetch(invocation.getArgument(0)));
        Mockito.when(dataService.info(Mockito.anyLong())).thenReturn(List.of());
        try {
            Mockito.when(store.openSource(Mockito.anyLong())).thenAnswer(invocation ->
                    source(invocation.<Long>getArgument(0)));
            Mockito.when(feedProperties.getEncoding(Mockito.any(), Mockito.any(), Mockito.any()))
                    .thenAnswer(invocation -> encodings.getOrDefault(invocation.<String>getArgument(0), "UTF-8"));
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /// @return A stream reader over this world.
    StreamReader streamReader() {
        return new StreamReader(() -> metaService, () -> dataService, () -> store, () -> feedProperties,
                () -> securityContext);
    }

    /// Adds a stream.
    Meta add(final long id,
             final String type,
             final Long parentId,
             final String pipelineUuid,
             final Map<String, String> attributes) {
        final Meta meta = Meta.builder()
                .id(id)
                .feedName(FEED)
                .typeName(type)
                .parentDataId(parentId)
                .pipelineUuid(pipelineUuid)
                .status(Status.UNLOCKED)
                .createMs(1_790_841_600_000L + id)
                .build();
        metas.put(id, new MetaRow(meta, null, new HashMap<>(attributes)));
        return meta;
    }

    /// Adds a segmented stream (e.g. Events), with what the data viewer gives for each record.
    Meta addSegmented(final long id, final String type, final Long parentId, final String pipelineUuid,
                      final List<String> recordTexts) {
        records.put(id, recordTexts);
        return add(id, type, parentId, pipelineUuid, Map.of());
    }

    /// Adds a raw stream, held as one record, with its text.
    Meta addRaw(final long id, final String text) {
        bytes.put(id, text.getBytes(StandardCharsets.UTF_8));
        return add(id, StreamTypeNames.RAW_EVENTS, null, null, Map.of());
    }

    private static boolean matches(final Meta meta, final ExpressionItem item) {
        if (item instanceof final ExpressionOperator operator) {
            return operator.getChildren() == null
                   || operator.getChildren().stream().allMatch(child -> matches(meta, child));
        }
        final ExpressionTerm term = (ExpressionTerm) item;
        return switch (term.getField()) {
            case "Id" -> Objects.equals(String.valueOf(meta.getId()), term.getValue());
            case "Parent Id" -> Objects.equals(String.valueOf(meta.getParentMetaId()), term.getValue());
            case "Type" -> Objects.equals(meta.getTypeName(), term.getValue());
            case "Feed" -> Objects.equals(meta.getFeedName(), term.getValue());
            default -> true;
        };
    }

    private FetchDataResult fetch(final FetchDataRequest request) {
        final long id = request.getSourceLocation().getMetaId();
        final long index = request.getSourceLocation().getRecordIndex();
        final String childType = request.getSourceLocation().getOptChildType().orElse(null);
        final MetaRow row = metas.get(id);
        final List<String> problems = errors.getOrDefault(id, List.of());
        if (row == null || !problems.isEmpty()) {
            return result(row, null, 0, null, problems.isEmpty()
                    ? List.of("Error fetching data: no such stream")
                    : problems);
        }
        if (StreamTypeNames.META.equals(childType)) {
            return result(row, headers.get(id), 1, DataType.NON_SEGMENTED, null);
        }
        final List<String> recordTexts = records.get(id);
        if (recordTexts != null) {
            return result(row, index < recordTexts.size()
                    ? recordTexts.get((int) index)
                    : null, recordTexts.size(), DataType.SEGMENTED, null);
        }
        final String text = new String(bytes.getOrDefault(id, new byte[0]), StandardCharsets.UTF_8);
        return result(row, text.substring(0, Math.min(viewerPage, text.length())), 1, DataType.NON_SEGMENTED, null);
    }

    private FetchDataResult result(final MetaRow row,
                                   final String data,
                                   final long total,
                                   final DataType dataType,
                                   final List<String> problems) {
        return new FetchDataResult(row == null
                ? null
                : row.getMeta().getFeedName(),
                row == null
                        ? null
                        : row.getMeta().getTypeName(),
                null, null, null, Count.of(total, true), null, null, childTypes, data, false,
                dataType, DisplayMode.TEXT, problems);
    }

    private Source source(final long id) throws IOException {
        final MetaRow row = metas.get(id);
        if (row == null) {
            throw new IllegalStateException("No stream " + id);
        }
        final Source source = Mockito.mock(Source.class);
        Mockito.when(source.getMeta()).thenReturn(row.getMeta());
        Mockito.when(source.count()).thenReturn(parts.containsKey(id)
                ? (long) parts.get(id).size()
                : 1L);
        Mockito.when(source.get(Mockito.anyLong())).thenAnswer(invocation -> {
            final long part = invocation.getArgument(0);
            final InputStreamProvider provider = Mockito.mock(InputStreamProvider.class);
            Mockito.when(provider.get()).thenAnswer(get -> new FakeSegmentInputStream(parts.containsKey(id)
                    ? parts.get(id).get((int) part)
                    : bytes.getOrDefault(id, String.join("\n", records.getOrDefault(id, new ArrayList<>()))
                            .getBytes(StandardCharsets.UTF_8))));
            return provider;
        });
        return source;
    }


    // --------------------------------------------------------------------------------


    /// A stream of one segment, held in memory.
    static final class FakeSegmentInputStream extends SegmentInputStream {

        private final ByteArrayInputStream in;
        private final long size;

        FakeSegmentInputStream(final byte[] bytes) {
            this.in = new ByteArrayInputStream(bytes);
            this.size = bytes.length;
        }

        @Override
        public long count() {
            return 1;
        }

        @Override
        public void include(final long segment) {
            // One segment only.
        }

        @Override
        public void includeAll() {
            // One segment only.
        }

        @Override
        public void exclude(final long segment) {
            // One segment only.
        }

        @Override
        public void excludeAll() {
            // One segment only.
        }

        @Override
        public long size() {
            return size;
        }

        @Override
        public int read() {
            return in.read();
        }

        @Override
        public int read(final byte[] buffer, final int offset, final int length) {
            return in.read(buffer, offset, length);
        }
    }
}
