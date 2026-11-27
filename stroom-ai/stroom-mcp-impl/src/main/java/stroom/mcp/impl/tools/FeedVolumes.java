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

import stroom.meta.api.MetaService;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.MetaExpressionUtil;
import stroom.meta.shared.MetaFields;
import stroom.meta.shared.MetaRow;
import stroom.meta.shared.SelectionSummary;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PageRequest;
import stroom.util.shared.Range;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.List;

/// How much a feed holds of a stream type, as its Data tab counts it (Unlocked streams the user may view): its
/// streams, their size, and when the first and last were created. Said before a promotion's filter can run, as a new
/// filter processes every matching stream it hasn't processed, a dormant feed's history too (decided by the user,
/// 2026-11-25).
class FeedVolumes {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(FeedVolumes.class);

    /// The most streams read for their size; past them the size is estimated from their average.
    static final int MAX_SIZED = 1_000;

    private final Provider<MetaService> metaServiceProvider;

    /// @param metaServiceProvider Counts and finds streams, as the user.
    @Inject
    FeedVolumes(final Provider<MetaService> metaServiceProvider) {
        this.metaServiceProvider = metaServiceProvider;
    }

    /// @param feed       A feed's name.
    /// @param streamType A stream type.
    /// @return What the feed holds of the type; empty (`unread`) when it can't be read.
    Volume volume(final String feed, final String streamType) {
        try {
            final ExpressionOperator expression = ExpressionOperator.builder()
                    .addOperator(MetaExpressionUtil.createFeedExpression(feed))
                    .addTextTerm(MetaFields.TYPE, Condition.EQUALS, streamType)
                    .build();
            final MetaService metaService = metaServiceProvider.get();
            final SelectionSummary summary = metaService.getSelectionSummary(new FindMetaCriteria(expression));
            final long count = summary == null
                    ? 0
                    : summary.getItemCount();
            if (count == 0) {
                return new Volume(feed, streamType, 0, 0L, false, null, null, false);
            }
            final Range<Long> age = summary.getAgeRange();
            final FindMetaCriteria newest = new FindMetaCriteria(expression);
            newest.setPageRequest(new PageRequest(0, MAX_SIZED));
            newest.setSort(MetaFields.ID.getFldName(), true, false);
            final List<MetaRow> rows = NullSafe.list(NullSafe.get(metaService.findRows(newest),
                    ResultPage::getValues));
            long bytes = 0;
            int sized = 0;
            for (final MetaRow row : rows) {
                final String size = NullSafe.string(row.getAttributeValue(MetaFields.FILE_SIZE.getFldName())).strip();
                if (!size.isEmpty() && size.chars().allMatch(Character::isDigit)) {
                    bytes += Long.parseLong(size);
                    sized++;
                }
            }
            final boolean estimated = sized < count;
            final Long total = sized == 0
                    ? null
                    : estimated
                            ? Math.round((double) bytes / sized * count)
                            : bytes;
            return new Volume(feed, streamType, count, total, estimated, NullSafe.get(age, Range::getFrom),
                    NullSafe.get(age, Range::getTo), false);
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("The streams of feed {} not counted: {}", feed, e.getMessage()));
            return new Volume(feed, streamType, 0, null, false, null, null, true);
        }
    }


    // --------------------------------------------------------------------------------


    /// What a feed holds of a stream type.
    ///
    /// @param feed       The feed.
    /// @param streamType The stream type.
    /// @param streams    Its streams.
    /// @param bytes      Their size, or null when not known.
    /// @param estimated  True if the size is estimated from the newest [#MAX_SIZED] streams' average.
    /// @param firstMs    When its first stream was created, or null.
    /// @param lastMs     When its last stream was created, or null.
    /// @param unread     True if the feed's streams couldn't be counted.
    record Volume(String feed,
                  String streamType,
                  long streams,
                  Long bytes,
                  boolean estimated,
                  Long firstMs,
                  Long lastMs,
                  boolean unread) {

        /// @return It in words, e.g. `ACME-A (Raw Events): 120 streams, 3.4M, created 2026-01-02T... to ...`.
        String said() {
            final String of = feed + " (" + streamType + "): ";
            if (unread) {
                return of + "its streams couldn't be counted";
            }
            if (streams == 0) {
                return of + "no streams yet";
            }
            return of + streams + " stream" + (streams == 1
                    ? ""
                    : "s") + (bytes == null
                    ? ", size not known"
                    : ", " + (estimated
                            ? "about "
                            : "") + ModelStringUtil.formatIECByteSizeString(bytes, true)) + (firstMs == null
                    || lastMs == null
                    ? ""
                    : ", created " + StreamProcessing.iso(firstMs) + " to " + StreamProcessing.iso(lastMs));
        }
    }
}
