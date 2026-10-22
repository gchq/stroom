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

import stroom.data.shared.DataInfoSection;
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
import stroom.meta.shared.MetaFields;
import stroom.meta.shared.MetaRow;
import stroom.pipeline.shared.AbstractFetchDataResult;
import stroom.pipeline.shared.FetchDataRequest;
import stroom.pipeline.shared.FetchDataRequest.DisplayMode;
import stroom.pipeline.shared.FetchDataResult;
import stroom.pipeline.shared.SourceLocation;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.security.api.SecurityContext;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.Count;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;
import stroom.util.shared.Severity;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.LongSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Finds and reads streams for the tools, as the user: their meta, their records (raw input, Events XML, or a
/// child part such as the receipt headers), a raw stream's text, and the attributes a raw stream carries.
///
/// Meta is found through [MetaService], which only finds streams of feeds the user may view. Records are read
/// through [DataService], as the data viewer reads them, which needs the View Data permission. A raw stream's text
/// is read through the stream store, which checks the user may view the stream's feed when it opens it, decoded
/// with the feed's encoding as a pipeline decodes it (see [StreamCharsets]); callers check the View Data permission
/// first. Each caller logs what it read.
@Singleton
public class StreamReader {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(StreamReader.class);

    /// What the data viewer says of a stream with bytes its feed's character set can't decode.
    public static final String UNDECODABLE = "Unable to decode";
    /// How long a raw stream's text is kept between calls: a stream never changes, and the same sample is read on
    /// every attempt at a mapping (seen: a hundred attempts, each reading it from Stroom again).
    static final long SAMPLE_TTL_MS = 900_000L;
    /// The most text kept, in all.
    static final long SAMPLE_CACHE_CHARS = 64_000_000L;
    /// The most reports of undecodable bytes kept.
    static final int MAX_REPORTS = 500;
    /// The most characters of a raw stream read to split it into records (as a survey reads a sample).
    public static final int MAX_SAMPLE_CHARS = 1_000_000;
    /// The most records a raw stream is split into.
    static final int MAX_SPLIT_RECORDS = 1_000_000;
    /// Formats with a record a line.
    static final Set<String> LINE_FORMATS = Set.of(RecordSplitter.XML_FRAGMENTS, RecordSplitter.JSON_LINES,
            "key=value", RecordSplitter.DELIMITED, "syslog rfc3164", "syslog rfc5424", "cef",
            RecordSplitter.UNKNOWN_TEXT);

    private static final Pattern PREFIXED_ROOT = Pattern.compile(
            "^\\s*(?:<\\?xml[^>]*\\?>\\s*)?<([\\w.-]+):([\\w.-]+)[\\s/>]");
    private static final int MAX_BYTES_PER_CHAR = 4;

    private final Provider<MetaService> metaServiceProvider;
    private final Provider<DataService> dataServiceProvider;
    private final Provider<Store> storeProvider;
    private final Provider<FeedProperties> feedPropertiesProvider;
    private final Provider<SecurityContext> securityContextProvider;
    private final LongSupplier clock;
    private final Map<List<Object>, CachedText> texts = new LinkedHashMap<>();
    private final Map<Long, Map<String, Object>> reports = new LinkedHashMap<>();

    @Inject
    public StreamReader(final Provider<MetaService> metaServiceProvider,
                        final Provider<DataService> dataServiceProvider,
                        final Provider<Store> storeProvider,
                        final Provider<FeedProperties> feedPropertiesProvider,
                        final Provider<SecurityContext> securityContextProvider) {
        this(metaServiceProvider, dataServiceProvider, storeProvider, feedPropertiesProvider,
                securityContextProvider, System::currentTimeMillis);
    }

    /// As the injected constructor, with the clock given, for tests.
    StreamReader(final Provider<MetaService> metaServiceProvider,
                 final Provider<DataService> dataServiceProvider,
                 final Provider<Store> storeProvider,
                 final Provider<FeedProperties> feedPropertiesProvider,
                 final Provider<SecurityContext> securityContextProvider,
                 final LongSupplier clock) {
        this.metaServiceProvider = metaServiceProvider;
        this.dataServiceProvider = dataServiceProvider;
        this.storeProvider = storeProvider;
        this.feedPropertiesProvider = feedPropertiesProvider;
        this.securityContextProvider = securityContextProvider;
        this.clock = clock;
    }

    // --------------------------------------------------------------------------------
    // Meta.
    // --------------------------------------------------------------------------------

    /// @param expression The search.
    /// @param limit      The most streams to find.
    /// @param newestFirst True for the newest first, false for the oldest.
    /// @return The streams found, of feeds the user may view, with their meta values.
    public ResultPage<MetaRow> find(final ExpressionOperator expression, final int limit, final boolean newestFirst) {
        final FindMetaCriteria criteria = new FindMetaCriteria(expression);
        criteria.setPageRequest(new PageRequest(0, limit));
        criteria.setSort(MetaFields.FIELD_ID, newestFirst, false);
        return metaServiceProvider.get().findRows(criteria);
    }

    /// @param streamId A stream (meta) id.
    /// @return The stream's meta and meta values, whatever its status, if it exists and the user may view its
    /// feed.
    public Optional<MetaRow> metaRow(final long streamId) {
        final ExpressionOperator expression = ExpressionOperator.builder().op(Op.AND)
                .addIdTerm(MetaFields.ID, Condition.EQUALS, streamId)
                .build();
        return NullSafe.list(NullSafe.get(find(expression, 1, true), ResultPage::getValues)).stream().findFirst();
    }

    /// @param streamId A stream (meta) id.
    /// @return The stream's meta.
    /// @throws StreamReadException if there is no such stream, or the user may not view its feed.
    public Meta meta(final long streamId) {
        return metaRow(streamId)
                .map(MetaRow::getMeta)
                .orElseThrow(() -> new StreamReadException("No stream with id " + streamId));
    }

    /// @param streamId A stream (meta) id.
    /// @param limit    The most to find.
    /// @return The streams produced from it (e.g. the Events and Error streams from a Raw Events stream), newest
    /// first, whatever their status.
    public List<MetaRow> children(final long streamId, final int limit) {
        final ExpressionOperator expression = ExpressionOperator.builder().op(Op.AND)
                .addIdTerm(MetaFields.PARENT_ID, Condition.EQUALS, streamId)
                .build();
        return NullSafe.list(NullSafe.get(find(expression, limit, true), ResultPage::getValues));
    }

    // --------------------------------------------------------------------------------
    // Records.
    // --------------------------------------------------------------------------------

    /// Up to `count` records from `first`, within a character budget.
    ///
    /// Stroom returns one record per fetch from a segmented stream (e.g. Events), whatever count is asked for, so
    /// segmented streams are read record by record. A raw stream is one record.
    ///
    /// @param streamId  The stream.
    /// @param first     The zero-based index of the first record.
    /// @param count     The most records.
    /// @param childType A child part to read instead (e.g. `Meta Data` or `Context`), or null.
    /// @param budget    The most characters of record data.
    /// @return The records, and what the first fetch said of the stream.
    /// @throws StreamReadException if Stroom could not read the stream, saying why.
    public Records readRecords(final long streamId,
                               final long first,
                               final int count,
                               final String childType,
                               final int budget) {
        Fetched fetched = fetch(streamId, first, childType);
        if (!fetched.errors().isEmpty()) {
            throw new StreamReadException("Stroom could not read stream " + streamId + ": "
                                          + String.join("; ", fetched.errors()));
        }
        final Fetched firstFetched = fetched;
        final long total = Math.max(1, fetched.totalItems() == null
                ? 1
                : fetched.totalItems());
        final List<String> records = new ArrayList<>();
        int used = 0;
        long index = first;
        while (true) {
            final String whole = NullSafe.string(fetched.data());
            if (fetched.segmented() && !records.isEmpty() && whole.length() > budget - used) {
                // An event cut at the budget is not well-formed XML: seen validated as "attributes construct
                // error". It is left for the next page (first_record) rather than returned cut; only a first
                // record is ever cut.
                return new Records(records, firstFetched);
            }
            final String data = whole.substring(0, Math.min(whole.length(), Math.max(0, budget - used)));
            records.add(fetched.segmented()
                    ? rootClosed(data)
                    : data);
            used += data.length();
            index++;
            if (records.size() >= count || index >= total || used >= budget || !fetched.segmented()) {
                return new Records(records, firstFetched);
            }
            fetched = fetch(streamId, index, childType);
        }
    }

    /// @param streamId  The stream.
    /// @param record    The zero-based index of the record (of the first part).
    /// @param childType A child part to read instead, or null.
    /// @return What the data viewer gives for one record, as text.
    public Fetched fetch(final long streamId, final long record, final String childType) {
        final SourceLocation location = SourceLocation.builder(streamId)
                .withPartIndex(0L)
                .withRecordIndex(record)
                .withChildStreamType(childType)
                .build();
        final FetchDataRequest request = new FetchDataRequest(location, 1L, null, false, Severity.SEVERITIES,
                DisplayMode.TEXT);
        final AbstractFetchDataResult result = dataServiceProvider.get().fetch(request);
        final String data = result instanceof final FetchDataResult dataResult
                ? dataResult.getData()
                : null;
        final boolean segmented = result instanceof final FetchDataResult dataResult
                                  && DataType.SEGMENTED.equals(dataResult.getDataType());
        return new Fetched(result.getFeedName(), result.getStreamTypeName(), data, segmented,
                NullSafe.get(result.getTotalItemCount(), Count::getCount),
                result.getAvailableChildStreamTypes() == null
                        ? null
                        : List.copyOf(result.getAvailableChildStreamTypes()),
                NullSafe.list(result.getErrors()));
    }

    /// A segmented record as a well-formed document. Stroom wraps each record in the stream's root element, but
    /// closes a prefixed root (`<evt:Events>`, written by an XSLT with a namespace prefix) by its local name alone
    /// (`</Events>`).
    ///
    /// @param record A record.
    /// @return It with its root closed by its own name.
    public static String rootClosed(final String record) {
        final Matcher matcher = PREFIXED_ROOT.matcher(record);
        if (!matcher.find()) {
            return record;
        }
        final String prefix = matcher.group(1);
        final String local = matcher.group(2);
        final String body = record.stripTrailing();
        final String close = "</" + local + ">";
        if (body.endsWith(close)) {
            return body.substring(0, body.length() - close.length()) + "</" + prefix + ":" + local + ">"
                   + record.substring(body.length());
        }
        return record;
    }

    // --------------------------------------------------------------------------------
    // Raw text.
    // --------------------------------------------------------------------------------

    /// A raw stream's text (its first part), up to `maxChars` and then cut at a line end, decoded with its feed's
    /// encoding as a pipeline decodes it: each byte the encoding can't decode is replaced, and what was replaced is
    /// remembered for the stream (see [#charsetReport(long)]).
    ///
    /// @param streamId The stream.
    /// @param maxChars The most characters.
    /// @return The text, and whether it was cut short.
    /// @throws StreamReadException if the stream can't be read.
    public RawText rawText(final long streamId, final int maxChars) {
        final int maxBytes = (int) Math.min(Integer.MAX_VALUE - 8L, (long) maxChars * MAX_BYTES_PER_CHAR);
        final byte[] bytes;
        final boolean moreBytes;
        final String encoding;
        try (final Source source = storeProvider.get().openSource(streamId)) {
            final Meta meta = source.getMeta();
            encoding = encoding(meta);
            try (final InputStreamProvider provider = source.get(0);
                    final SegmentInputStream in = provider.get()) {
                in.includeAll();
                final byte[] read = in.readNBytes(maxBytes + 1);
                moreBytes = read.length > maxBytes;
                bytes = moreBytes
                        ? Arrays.copyOf(read, maxBytes)
                        : read;
            }
        } catch (final IOException | RuntimeException e) {
            LOGGER.debug(() -> "Unable to read stream " + streamId + ": " + e.getMessage(), e);
            throw new StreamReadException("Stroom could not read stream " + streamId + ": " + e.getMessage());
        }
        final StreamCharsets.Decoded decoded = StreamCharsets.decode(bytes, encoding, !moreBytes);
        remember(streamId, decoded.report());
        String text = decoded.text();
        if (!text.isEmpty() && text.charAt(0) == '﻿') {
            // A byte order mark, which a pipeline doesn't read as text either.
            text = text.substring(1);
        }
        final boolean truncated = moreBytes || text.length() > maxChars;
        if (truncated) {
            text = text.substring(0, Math.min(text.length(), maxChars));
            final int lineEnd = text.lastIndexOf('\n');
            if (lineEnd >= 0) {
                text = text.substring(0, lineEnd + 1);
            }
        }
        return new RawText(text, truncated);
    }

    /// As [#rawText(long, int)], kept a while for the user: a stream never changes, and the same sample is read
    /// on every attempt at a mapping. Kept per user, as a stream one user may read another may not.
    ///
    /// @param streamId The stream.
    /// @param maxChars The most characters.
    /// @return The text, and whether it was cut short.
    public RawText cachedRawText(final long streamId, final int maxChars) {
        final List<Object> key = List.of(userKey(), streamId, maxChars);
        final long now = clock.getAsLong();
        synchronized (texts) {
            texts.values().removeIf(cached -> now - cached.at() > SAMPLE_TTL_MS);
            final CachedText cached = texts.get(key);
            if (cached != null) {
                return cached.text();
            }
        }
        final RawText text = rawText(streamId, maxChars);
        synchronized (texts) {
            long held = texts.values().stream().mapToLong(cached -> cached.text().text().length()).sum();
            while (!texts.isEmpty() && held + text.text().length() > SAMPLE_CACHE_CHARS) {
                final List<Object> oldest = texts.keySet().iterator().next();
                held -= texts.remove(oldest).text().text().length();
            }
            texts.put(key, new CachedText(now, text));
        }
        return text;
    }

    /// @param streamId A stream read with [#rawText(long, int)].
    /// @return What reading it replaced, as [StreamCharsets.Decoded#report()] gives it, or null if every byte
    /// decoded (or it hasn't been read).
    public Map<String, Object> charsetReport(final long streamId) {
        synchronized (reports) {
            return reports.get(streamId);
        }
    }

    private void remember(final long streamId, final Map<String, Object> report) {
        synchronized (reports) {
            if (report == null) {
                reports.remove(streamId);
                return;
            }
            if (reports.size() >= MAX_REPORTS) {
                reports.remove(reports.keySet().iterator().next());
            }
            reports.put(streamId, report);
        }
    }

    private String encoding(final Meta meta) {
        try {
            return feedPropertiesProvider.get().getEncoding(meta.getFeedName(), meta.getTypeName(), null);
        } catch (final IOException | RuntimeException e) {
            // The feed unreadable: Stroom's default.
            LOGGER.debug(() -> "Unable to read the encoding of feed " + meta.getFeedName(), e);
            return null;
        }
    }

    private String userKey() {
        try {
            return NullSafe.getOrElse(securityContextProvider.get().getUserRef(), Object::toString, "");
        } catch (final RuntimeException e) {
            return "";
        }
    }

    /// Records of a raw stream Stroom holds as one record, split from its text as a survey splits them: seen in
    /// production, reads of records 200, 450, 700 and 950 of a 1,000-fragment sample each returned its first four.
    ///
    /// @param streamId The raw stream.
    /// @param first    The zero-based index of the first record.
    /// @param count    The most records.
    /// @param budget   The most characters of record data.
    /// @return `records`, `total_records`, any `header`, `records_are` and any `hint`, as the Stroom MCP server gave
    /// them; or null when it holds only one record after all, or its text can't be split.
    public Map<String, Object> splitRaw(final long streamId, final int first, final int count, final int budget) {
        final RawText raw = cachedRawText(streamId, MAX_SAMPLE_CHARS);
        final String text = raw.text();
        final boolean truncated = raw.truncated();
        final RecordSplitter.Chunk chunk;
        try {
            chunk = RecordSplitter.split(text, MAX_SPLIT_RECORDS, truncated);
        } catch (final RuntimeException e) {
            // A text the splitter can't read: Stroom's one record, as before.
            return null;
        }
        if (chunk.records().size() <= 1) {
            return null;
        }
        List<String> lines = RecordSplitter.lines(text);
        if (truncated && !lines.isEmpty() && !text.endsWith("\n") && !text.endsWith("\r")) {
            lines = lines.subList(0, lines.size() - 1);
        }
        if (chunk.header() != null && !lines.isEmpty() && lines.getFirst().equals(chunk.header())) {
            lines = lines.subList(1, lines.size());
        }
        // One record a line: each as it is in the stream (an XML fragment keeps its namespace declaration);
        // otherwise each record as the splitter gives it.
        final List<String> whole = LINE_FORMATS.contains(chunk.format()) && lines.size() == chunk.records().size()
                ? lines
                : chunk.records();
        final List<String> out = new ArrayList<>();
        int used = 0;
        for (final String record : whole.subList(Math.min(first, whole.size()),
                Math.min(whole.size(), first + count))) {
            if (!out.isEmpty() && used + record.length() > budget) {
                break;
            }
            out.add(record.substring(0, Math.min(record.length(), budget - used)));
            used += out.getLast().length();
        }
        final int total = whole.size();
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", out);
        result.put("total_records", truncated
                ? null
                : total);
        // The header line names the columns: given apart from the records (seen in e2e: it was dropped from the
        // records and given nowhere, so the fields a translation never reads couldn't be named).
        if (chunk.header() != null) {
            result.put("header", chunk.header());
        }
        result.put("records_are", "split from the stream's text (" + chunk.format()
                                  + (chunk.header() != null
                ? "; its header line, naming the columns, is header"
                : "")
                                  + "), as Stroom holds it as one record"
                                  + (truncated
                ? "; only its first " + thousands(text.length()) + " characters were read, holding "
                  + total + " records"
                : ""));
        if (first >= total) {
            result.put("hint", truncated
                    ? "firstRecord=" + first + " is past the " + total + " records in the first "
                      + thousands(text.length()) + " characters read."
                    : "Records run from 0 to " + (total - 1) + ": firstRecord=" + first + " is past the end.");
        }
        return result;
    }

    /// @param number A number.
    /// @return It with thousands separated by commas, e.g. `60,000`.
    public static String thousands(final long number) {
        return String.format(Locale.ROOT, "%,d", number);
    }

    // --------------------------------------------------------------------------------
    // Attributes.
    // --------------------------------------------------------------------------------

    /// Attributes a raw stream carries: its receipt headers (e.g. Feed, RemoteAddress, ReceivedTime, or custom ones
    /// such as MyHost) with values, usable in XSLT as `stroom:meta('Name')`, and the stream's own details.
    ///
    /// @param streamId The stream.
    /// @return `stream_id`, `attributes` (the headers) and `stream` (its details), as the Stroom MCP server gave
    /// them.
    public Map<String, Object> streamAttributes(final long streamId) {
        final Map<String, String> headers = new LinkedHashMap<>();
        final Fetched fetched = fetch(streamId, 0, StreamTypeNames.META);
        for (final String line : NullSafe.string(fetched.data()).split("\\R")) {
            final int colon = line.indexOf(':');
            if (colon >= 0) {
                headers.put(line.substring(0, colon).strip(), line.substring(colon + 1).strip());
            }
        }
        final Map<String, String> details = new LinkedHashMap<>();
        for (final DataInfoSection section : NullSafe.list(dataServiceProvider.get().info(streamId))) {
            if ("Stream".equals(section.getTitle()) || "Attributes".equals(section.getTitle())) {
                for (final DataInfoSection.Entry entry : NullSafe.list(section.getEntries())) {
                    if (NullSafe.isNonEmptyString(entry.getKey())) {
                        details.put(entry.getKey(), entry.getValue());
                    }
                }
            }
        }
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("stream_id", streamId);
        result.put("attributes", headers);
        result.put("stream", details);
        return result;
    }

    // --------------------------------------------------------------------------------
    // Error streams.
    // --------------------------------------------------------------------------------

    /// The start of an Error stream's text, all its parts, as UTF-8.
    ///
    /// @param store    The stream store.
    /// @param streamId The Error stream.
    /// @param maxBytes The most bytes to read.
    /// @return The text, and whether it was cut short.
    /// @throws IOException      if it can't be read.
    /// @throws RuntimeException if it can't be opened, e.g. it has been deleted or the user may not view its feed.
    public static RawText errorStreamText(final Store store, final long streamId, final int maxBytes)
            throws IOException {
        try (final Source source = store.openSource(streamId)) {
            final long parts = source.count();
            final StringBuilder text = new StringBuilder();
            int remaining = maxBytes;
            boolean truncated = false;
            for (long part = 0; part < parts; part++) {
                if (remaining <= 0) {
                    truncated = true;
                    break;
                }
                try (final InputStreamProvider provider = source.get(part);
                        final SegmentInputStream in = provider.get()) {
                    in.includeAll();
                    final byte[] bytes = in.readNBytes(remaining + 1);
                    final int length = Math.min(bytes.length, remaining);
                    if (bytes.length > remaining) {
                        truncated = true;
                    }
                    text.append(new String(bytes, 0, length, StandardCharsets.UTF_8));
                    if (!text.isEmpty() && text.charAt(text.length() - 1) != '\n' && !truncated) {
                        text.append('\n');
                    }
                    remaining -= length;
                }
            }
            return new RawText(text.toString(), truncated);
        }
    }


    // --------------------------------------------------------------------------------


    /// What the data viewer gives for one record.
    ///
    /// @param feedName            The stream's feed.
    /// @param streamTypeName      The stream's type.
    /// @param data                The record's text, or null.
    /// @param segmented           True if the stream is segmented into records (e.g. Events); false for a raw
    ///                            stream, held as one record.
    /// @param totalItems          How many records (or parts) there are, or null.
    /// @param availableChildTypes The child parts it has, e.g. `Meta Data` or `Context`, or null.
    /// @param errors              Why it couldn't be read, if it couldn't; else empty.
    public record Fetched(String feedName,
                          String streamTypeName,
                          String data,
                          boolean segmented,
                          Long totalItems,
                          List<String> availableChildTypes,
                          List<String> errors) {

        /// @return True if Stroom couldn't show the stream as text, as some of its bytes aren't in its feed's
        /// character set.
        public boolean undecodable() {
            return errors.stream().anyMatch(error -> NullSafe.string(error).contains(UNDECODABLE));
        }
    }

    /// Records read.
    ///
    /// @param records The records, each whole but a first one larger than the budget.
    /// @param first   What the first fetch said of the stream.
    public record Records(List<String> records, Fetched first) {

    }

    /// A stream's text.
    ///
    /// @param text      The text.
    /// @param truncated True if it was cut short.
    public record RawText(String text, boolean truncated) {

    }

    private record CachedText(long at, RawText text) {

    }

    /// A stream that couldn't be found or read, saying why.
    public static class StreamReadException extends RuntimeException {

        /// @param message Why, for the agent.
        public StreamReadException(final String message) {
            super(message);
        }
    }
}
