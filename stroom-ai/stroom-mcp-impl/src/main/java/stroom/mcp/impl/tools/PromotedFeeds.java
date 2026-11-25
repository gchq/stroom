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

import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.sample.ToolJson;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// The feeds a promotion gave new filters, kept in the session's workspace (the chat's, or the MCP session's), so
/// `build_status` can say how they are selected once the build is promoted and its record gone: a feed selected by
/// two pipelines' filters, or by no enabled one, and the old filters' tasks still open (decided by the user,
/// 2026-11-25). Only what the promotion made is kept; how the feeds are selected is read live each time. A session
/// without a workspace keeps nothing, and only the promotion's own result says it.
final class PromotedFeeds {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(PromotedFeeds.class);

    /// The key of the report in a result.
    static final String FEED_SELECTION = "feed_selection";
    // What the session keeps it under, with the promotion's layout shown.
    private static final String BLOCK = "promoted feeds";
    private static final String STREAM_TYPE = "stream_type";
    private static final String FEEDS = "feeds";

    private PromotedFeeds() {
        // Static only.
    }

    /// Keeps each new filter's feeds for the build, and says how they are selected now.
    ///
    /// @param context    The call's context.
    /// @param build      The build promoted.
    /// @param processing What promotion planned for its pipelines.
    /// @param made       What it made, in the plan's order.
    /// @param switchOver Reads how the feeds are selected, or null (nothing said).
    /// @return The report, as [PromotionSwitchOver#selectionReport] says it.
    static List<String> remember(final ToolCallContext context,
                                 final String build,
                                 final List<PromotionProcessing.Entry> processing,
                                 final List<Map<String, Object>> made,
                                 final PromotionSwitchOver switchOver) {
        final Map<String, String> kept = new LinkedHashMap<>();
        final List<Promoted> promoted = new ArrayList<>();
        for (int i = 0; i < processing.size() && i < made.size(); i++) {
            final PromotionProcessing.Entry entry = processing.get(i);
            if (entry.feeds().isEmpty() || !(made.get(i).get("filter_id") instanceof final Integer id)) {
                continue;
            }
            final Map<String, Object> said = new LinkedHashMap<>();
            said.put(STREAM_TYPE, entry.streamType());
            said.put(FEEDS, entry.feeds());
            kept.put(String.valueOf(id), ToolJson.dumpsCompact(said));
            promoted.add(new Promoted(id, entry.streamType(), entry.feeds()));
        }
        UnsavedChoices.remember(context, PromotionReview.shownTarget(build), BLOCK, kept);
        return switchOver == null
                ? List.of()
                : report(promoted, switchOver);
    }

    /// @param context The call's context.
    /// @param build   A build.
    /// @return The feeds its promotion in this session gave new filters; none if it wasn't promoted here.
    static List<Promoted> recalled(final ToolCallContext context, final String build) {
        final List<Promoted> out = new ArrayList<>();
        UnsavedChoices.recalled(context, PromotionReview.shownTarget(build), BLOCK).forEach((id, json) -> {
            try {
                if (ToolJson.loads(json) instanceof final Map<?, ?> said
                    && said.get(FEEDS) instanceof final List<?> feeds) {
                    out.add(new Promoted(Integer.parseInt(id), String.valueOf(said.get(STREAM_TYPE)),
                            feeds.stream().map(String::valueOf).toList()));
                }
            } catch (final RuntimeException e) {
                LOGGER.debug(() -> LogUtil.message("Promoted filter {} of {} not read: {}", id, build,
                        e.getMessage()));
            }
        });
        return out;
    }

    /// @param promoted   The feeds a promotion gave new filters.
    /// @param switchOver Reads how they are selected.
    /// @return How they are selected now, as [PromotionSwitchOver#selectionReport] says it.
    static List<String> report(final List<Promoted> promoted, final PromotionSwitchOver switchOver) {
        final Set<Integer> ids = new LinkedHashSet<>();
        promoted.forEach(filter -> ids.add(filter.filterId()));
        final List<String> out = new ArrayList<>();
        for (final Promoted filter : promoted) {
            out.addAll(switchOver.selectionReport(NullSafe.list(filter.feeds()), filter.streamType(), ids));
        }
        return out;
    }


    // --------------------------------------------------------------------------------


    /// A promotion's new filter and its feeds.
    ///
    /// @param filterId   The filter.
    /// @param streamType The stream type it selects.
    /// @param feeds      The feeds it selects.
    record Promoted(int filterId, String streamType, List<String> feeds) {

    }
}
