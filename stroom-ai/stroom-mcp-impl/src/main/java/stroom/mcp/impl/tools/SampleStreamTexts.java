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

package stroom.mcp.impl.tools;

import stroom.ai.impl.tools.DocumentAccess;
import stroom.ai.impl.tools.StreamCharsets;
import stroom.ai.impl.tools.StreamReader;
import stroom.ai.impl.tools.StreamReader.RawText;
import stroom.ai.impl.tools.StreamReader.StreamReadException;
import stroom.data.shared.StreamTypeNames;
import stroom.docref.DocRef;
import stroom.feed.shared.FeedDoc;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaFields;
import stroom.meta.shared.MetaRow;
import stroom.meta.shared.Status;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/// Sample streams' raw text, read in place of text sent by the client, so a sample passes through the model once
/// (upload_sample) however many tools then read it (the Stroom MCP server's `tools/streams.py`
/// `read_sample_streams` and `refuse_older_than_feed`).
class SampleStreamTexts {

    /// What is said to a user without the View Data permission.
    static final String NO_VIEW_DATA = "You don't have permission to view data, so the sample streams can't be "
                                       + "read.";

    private final StreamReader streamReader;
    private final FeedNames feedNames;
    private final DocumentAccess documentAccess;

    @Inject
    SampleStreamTexts(final StreamReader streamReader, final FeedNames feedNames, final DocumentAccess documentAccess) {
        this.streamReader = streamReader;
        this.feedNames = feedNames;
        this.documentAccess = documentAccess;
    }

    /// @param streamIds Sample streams.
    /// @return Their text by name (`stream <id>`), and notes on what was cut short or read through its bytes.
    /// @throws ToolRefusal if the user may not view data, a stream is older than its feed (an earlier, deleted
    ///                     feed's), or one can't be read.
    Texts read(final List<Long> streamIds) {
        if (!documentAccess.canViewData()) {
            throw new ToolRefusal(NO_VIEW_DATA);
        }
        refuseOlderThanFeed(streamIds);
        final Map<String, String> texts = new LinkedHashMap<>();
        final List<String> notes = new ArrayList<>();
        for (final long streamId : streamIds) {
            final RawText text;
            try {
                streamReader.meta(streamId);
                text = streamReader.cachedRawText(streamId, StreamReader.MAX_SAMPLE_CHARS);
            } catch (final StreamReadException e) {
                throw new ToolRefusal(e.getMessage());
            }
            texts.put("stream " + streamId, text.text());
            if (text.truncated()) {
                notes.add("stream " + streamId + " is read up to its first "
                          + String.format(Locale.ROOT, "%,d", text.text().length())
                          + " characters (the most a sample is read to)");
            }
            final Map<String, Object> report = streamReader.charsetReport(streamId);
            if (report != null) {
                notes.add(StreamCharsets.note(streamId, report));
            }
        }
        return new Texts(texts, notes);
    }

    // Building and validating pipelines uses only a feed's own streams: refuse any older than its feed, which
    // belongs to an earlier, deleted feed of the same name.
    private void refuseOlderThanFeed(final List<Long> streamIds) {
        final Map<String, List<Long>> older = new LinkedHashMap<>();
        final Map<String, Optional<Long>> created = new LinkedHashMap<>();
        for (final long streamId : streamIds) {
            final Meta meta;
            try {
                meta = streamReader.meta(streamId);
            } catch (final StreamReadException e) {
                // Said when it is read.
                continue;
            }
            final String feed = meta.getFeedName();
            if (feed == null) {
                continue;
            }
            final Optional<Long> feedCreated = created.computeIfAbsent(feed, this::feedCreated);
            if (feedCreated.isPresent() && meta.getCreateMs() < feedCreated.get()) {
                older.computeIfAbsent(feed, ignored -> new ArrayList<>()).add(streamId);
            }
        }
        if (older.isEmpty()) {
            return;
        }
        final List<String> said = new ArrayList<>();
        older.forEach((feed, ids) -> {
            final List<Long> own = ownRawStreams(feed, created.get(feed).orElse(null));
            said.add("stream(s) " + ids + " are older than feed " + feed + " itself: they belong to an earlier feed "
                     + "of that name, since deleted (Stroom keeps its streams, though the UI doesn't show them). The "
                     + "feed's own raw streams: " + (own.isEmpty()
                    ? "none yet (upload_sample)"
                    : own));
        });
        throw new ToolRefusal("Only a feed's own streams are used to build and validate pipelines: "
                              + String.join("; ", said));
    }

    private Optional<Long> feedCreated(final String feed) {
        final List<DocRef> twins = feedNames.feedsNamed(feed);
        if (twins.size() != 1) {
            return Optional.empty();
        }
        return documentAccess.read(twins.getFirst(), FeedDoc.class).map(FeedDoc::getCreateTimeMs);
    }

    private List<Long> ownRawStreams(final String feed, final Long createdMs) {
        final ExpressionOperator expression = ExpressionOperator.builder().op(Op.AND)
                .addTextTerm(MetaFields.FEED, Condition.EQUALS, feed)
                .addTextTerm(MetaFields.TYPE, Condition.EQUALS, StreamTypeNames.RAW_EVENTS)
                .build();
        return NullSafe.list(NullSafe.get(streamReader.find(expression, 20, true), ResultPage::getValues)).stream()
                .map(MetaRow::getMeta)
                .filter(meta -> meta.getStatus() != Status.DELETED)
                .filter(meta -> createdMs == null || meta.getCreateMs() >= createdMs)
                .map(Meta::getId)
                .toList();
    }


    // --------------------------------------------------------------------------------


    /// Sample streams' text.
    ///
    /// @param texts Each stream's text, by name (`stream <id>`).
    /// @param notes What was cut short, or read through its bytes.
    record Texts(Map<String, String> texts, List<String> notes) {

    }
}
