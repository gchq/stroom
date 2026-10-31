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

import stroom.ai.impl.AiAuditLog;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.shared.AiToolCategory;
import stroom.data.retention.shared.DataRetentionDeleteSummary;
import stroom.data.retention.shared.DataRetentionRule;
import stroom.data.retention.shared.DataRetentionRules;
import stroom.job.api.JobStates.JobNodeState;
import stroom.job.api.JobStates.JobState;
import stroom.meta.shared.DataRetentionFields;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaFields;
import stroom.meta.shared.MetaRow;
import stroom.meta.shared.Status;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.security.api.SecurityContext;
import stroom.security.shared.AppPermission;
import stroom.util.date.DateUtil;
import stroom.util.json.JsonUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PermissionException;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

/// `explain_retention`: how long Stroom keeps data and why: the data retention rules in the order they apply, the
/// rule that keeps a feed's or a stream's data and until when, what the rules would delete now, and the jobs that
/// do the deleting, with how Stroom applies the rules ([#BEHAVIOUR]) so that it is never guessed. Read-only.
///
/// The rules themselves, and what they would delete, need the Manage Policies permission, as the Data Retention
/// screen does. Without it, the rule that keeps each of the user's own streams is still given, as the Data screen
/// gives it, and the jobs need Manage Jobs.
class ExplainRetentionTool extends AbstractBuiltInTool {

    static final String NAME = "explain_retention";
    static final String FEED_ARG = "feed";
    static final String STREAM_ID_ARG = "stream_id";
    static final String IMPACT_ARG = "impact";

    static final String RETENTION_JOB = "Policy Based Data Retention";
    static final String DELETE_JOB = "Data Delete";

    /// The most of a feed's streams read to find its newest of each type.
    static final int FEED_STREAMS = 200;
    /// The most feed and type counts given for each rule's impact.
    static final int MAX_IMPACT_ROWS = 10;

    /// How Stroom applies the data retention rules, as its code does it.
    static final List<String> BEHAVIOUR = List.of(
            "Retention is by age alone: a stream is deleted once it is older (by its create time) than the period "
            + "of the rule that applies to it. Disk space plays no part: Stroom never deletes data because a "
            + "volume is full (see " + VolumesTool.NAME + ").",
            "The rules are tried in rule number order, 1 first (the top of the Data Retention screen), and the "
            + "first whose expression matches the stream applies; the rest are ignored for it. Disabled rules, "
            + "and rules whose expression is disabled, are skipped.",
            "A stream no rule matches is kept forever (the screen's 'Default Retain All Forever Rule'). A rule "
            + "that keeps Forever keeps what it matches forever, even where a later rule would delete it.",
            "The '" + RETENTION_JOB + "' job (by default daily at midnight, run by one node at a time) marks the "
            + "streams past their rule's period as deleted: a logical delete, after which they are no longer "
            + "shown, searched or processed. Locked streams (still being written) are left until unlocked. If "
            + "the job is disabled, nothing is deleted however old it is.",
            "The '" + DELETE_JOB + "' job then removes the files and records of streams deleted longer ago than "
            + "stroom.data.store.deletePurgeAge. Until then, a deleted stream can still be restored (Restore on "
            + "the Data screen).",
            "After a run, the next run only looks at streams that have crossed a rule's period since; changing "
            + "the rules makes the next run look at all the data again.",
            "These rules cover streams only. Lucene index shards are deleted by each index's own retention "
            + "setting (the Index Shard Retention job), and Elasticsearch indices by Elasticsearch's own "
            + "lifecycle policies.");

    static final String REDUCED = "Reduced view: you don't have the Manage Policies permission, so the rules "
                                  + "themselves, and what they would delete, aren't shown; the rule that keeps "
                                  + "each of your streams is, as the Data screen shows it. Someone with Manage "
                                  + "Policies (the Data Retention screen) can see them all.";

    private final Provider<RetentionFacts> factsProvider;
    private final SecurityContext securityContext;
    private final AiAuditLog auditLog;

    @Inject
    ExplainRetentionTool(final Provider<RetentionFacts> factsProvider,
                         final SecurityContext securityContext,
                         final AiAuditLog auditLog) {
        this.factsProvider = factsProvider;
        this.securityContext = securityContext;
        this.auditLog = auditLog;
    }

    @Override
    public ToolPermissions permissions() {
        return ToolPermissions.builder()
                .unlockedBy(AppPermission.MANAGE_POLICIES_PERMISSION, AppPermission.MANAGE_JOBS_PERMISSION)
                .note("Without Manage Policies, only the rule that keeps each stream the user can view.")
                .build();
    }

    @Override
    public AiToolCategory category() {
        return AiToolCategory.PROCESSING;
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(NAME)
                .description("Explains how long Stroom keeps data and why, from its data retention rules: e.g. "
                             + "'how long is this feed's data kept?', 'when will this stream be deleted?', 'why "
                             + "was this data deleted?', 'what would the rules delete?', 'what happens to old "
                             + "data?'. Gives the rules in the order they apply (number, name, period, what each "
                             + "matches, enabled); with feed, the rule that applies to the newest stream of each "
                             + "of its types and the time it is kept until; with stream_id, that stream's; with "
                             + "impact, how many streams the rules would delete now, by rule, feed and type; and "
                             + "the state of the jobs that delete. 'behaviour' says how Stroom applies the rules: "
                             + "answer from it, not from how other products behave. The rules and impact need "
                             + "the Manage Policies permission; without it, each stream's rule is still given. "
                             + "Read-only: it changes nothing.")
                .parameters(ObjectSchema.builder()
                        .addStringProperty(FEED_ARG, "A feed's name: the rule that keeps its streams, for the "
                                                     + "newest of each stream type.")
                        .addIntegerProperty(STREAM_ID_ARG, "A stream (meta) id: the rule that keeps it.")
                        .addBooleanProperty(IMPACT_ARG, "Also count the streams the rules would delete now, by "
                                                        + "rule, feed and type (as the Data Retention screen's "
                                                        + "Impact tab; it can take a while on a large store).")
                        .build())
                .build();
    }

    @Override
    public ToolResult execute(final ToolCall request, final ToolCallContext context) {
        final String feed = stringArg(request, FEED_ARG);
        final Long streamId;
        try {
            streamId = longArg(request, STREAM_ID_ARG);
        } catch (final NumberFormatException e) {
            return ToolResult.error("stream_id must be a stream's id, a number.");
        }
        if (feed != null && streamId != null) {
            return ToolResult.error("Give feed or stream_id, not both.");
        }
        final boolean impact = Boolean.parseBoolean(Objects.requireNonNullElse(stringArg(request, IMPACT_ARG),
                "false"));
        final boolean canSeeRules = securityContext.hasAppPermission(AppPermission.MANAGE_POLICIES_PERMISSION);
        final RetentionFacts facts = factsProvider.get();
        final Optional<DataRetentionRules> rules = facts.rules();

        final Map<String, Object> result = new LinkedHashMap<>();
        if (!canSeeRules) {
            result.put("view", REDUCED);
        } else {
            result.put("rules", rules.map(ExplainRetentionTool::rules).orElse(List.of()));
            if (rules.map(DataRetentionRules::getRules).map(List::isEmpty).orElse(true)) {
                result.put("rules_note", "There are no data retention rules, so all data is kept forever.");
            }
        }

        final List<Long> streamIds = new ArrayList<>();
        if (streamId != null) {
            final List<MetaRow> rows = facts.streams(ExpressionOperator.builder()
                    .addIdTerm(MetaFields.ID, Condition.EQUALS, streamId)
                    .build(), 1);
            if (rows.isEmpty()) {
                return ToolResult.error("No stream " + streamId + " that you can view.");
            }
            result.put("streams", List.of(stream(rows.getFirst(), facts.now())));
            streamIds.add(streamId);
        } else if (feed != null) {
            final List<MetaRow> newestOfEachType = newestOfEachType(facts.streams(ExpressionOperator.builder()
                    .addTextTerm(MetaFields.FEED, Condition.EQUALS, feed)
                    .build(), FEED_STREAMS));
            if (newestOfEachType.isEmpty()) {
                result.put("streams_note", "No streams of feed '" + feed + "' that you can view.");
            }
            result.put("streams", newestOfEachType.stream().map(row -> stream(row, facts.now())).toList());
            newestOfEachType.forEach(row -> streamIds.add(row.getMeta().getId()));
        }

        if (impact) {
            if (!canSeeRules) {
                result.put("impact", "Not checked: it needs the Manage Policies permission.");
            } else if (rules.isEmpty()) {
                result.put("impact", List.of());
            } else {
                result.put("impact", impact(facts.impact(rules.get())));
            }
        }

        result.put("jobs", jobs(facts));
        facts.deletePurgeAge().ifPresent(age -> result.put("delete_purge_age", age));
        result.put("behaviour", BEHAVIOUR);

        final String query = "data retention" + (feed != null
                ? " of feed " + feed
                : streamId != null
                        ? " of stream " + streamId
                        : "");
        auditLog.streamsSearched(context.session(), query, streamIds,
                Map.of("View", canSeeRules
                        ? "Full"
                        : "Reduced"),
                "Data retention explained by the " + NAME + " tool");
        return ToolResult.text(JsonUtil.writeValueAsString(result, true));
    }

    // Each rule, in the order Stroom tries them.
    private static List<Map<String, Object>> rules(final DataRetentionRules rules) {
        return NullSafe.list(rules.getRules()).stream()
                .sorted(DataRetentionRule.comparingByRuleNumber())
                .map(rule -> {
                    final Map<String, Object> out = new LinkedHashMap<>();
                    out.put("number", rule.getRuleNumber());
                    out.put("name", rule.getName());
                    out.put("keep", rule.getAgeString());
                    out.put("matches", NullSafe.get(rule.getExpression(), ExpressionOperator::toString));
                    out.put("enabled", rule.isEnabled());
                    if (rule.isEnabled() && !NullSafe.test(rule.getExpression(), ExpressionOperator::enabled)) {
                        out.put("skipped", "Its expression is disabled, so it never applies.");
                    }
                    return out;
                })
                .toList();
    }

    // The newest stream of each type, newest first, from streams newest first.
    private static List<MetaRow> newestOfEachType(final List<MetaRow> rows) {
        final Map<String, MetaRow> byType = new LinkedHashMap<>();
        rows.forEach(row -> byType.putIfAbsent(row.getMeta().getTypeName(), row));
        return List.copyOf(byType.values());
    }

    private static Map<String, Object> stream(final MetaRow row, final Instant now) {
        final Meta meta = row.getMeta();
        final Map<String, String> attributes = NullSafe.map(row.getAttributes());
        final Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", meta.getId());
        out.put("feed", meta.getFeedName());
        out.put("type", meta.getTypeName());
        out.put("created", DateUtil.createNormalDateTimeString(meta.getCreateMs()));
        out.put("status", NullSafe.get(meta.getStatus(), Status::getDisplayValue));
        final String rule = attributes.get(DataRetentionFields.RETENTION_RULE);
        out.put("rule", "None".equals(rule)
                ? "None: no rule matches it, so it is kept forever"
                : rule);
        out.put("keep", attributes.get(DataRetentionFields.RETENTION_AGE));
        final String until = attributes.get(DataRetentionFields.RETENTION_UNTIL);
        out.put("keep_until", until);
        if (until != null && !DataRetentionRule.FOREVER.equals(until)
            && meta.getStatus() != Status.DELETED) {
            try {
                if (DateUtil.parseNormalDateTimeStringToInstant(until).isBefore(now)) {
                    out.put("overdue", "Past its period but not yet deleted: the '" + RETENTION_JOB + "' job "
                                       + "deletes it on its next run, if it is enabled.");
                }
            } catch (final RuntimeException e) {
                // Not a time (e.g. an error the Data screen would show), so nothing to say.
            }
        }
        return out;
    }

    // What the rules would delete now: for each rule, its total and its largest feeds and types.
    private static List<Map<String, Object>> impact(final List<DataRetentionDeleteSummary> summaries) {
        final Map<Integer, List<DataRetentionDeleteSummary>> byRule = summaries.stream()
                .collect(Collectors.groupingBy(DataRetentionDeleteSummary::getRuleNumber, TreeMap::new,
                        Collectors.toList()));
        final List<Map<String, Object>> out = new ArrayList<>();
        byRule.forEach((number, rows) -> {
            final Map<String, Object> rule = new LinkedHashMap<>();
            rule.put("rule", number + " " + Objects.requireNonNullElse(rows.getFirst().getRuleName(), ""));
            rule.put("streams_to_delete", rows.stream().mapToLong(DataRetentionDeleteSummary::getCount).sum());
            final List<DataRetentionDeleteSummary> largest = rows.stream()
                    .sorted(Comparator.comparingInt(DataRetentionDeleteSummary::getCount).reversed())
                    .toList();
            rule.put("by_feed_and_type", largest.stream()
                    .limit(MAX_IMPACT_ROWS)
                    .map(ExplainRetentionTool::impactRow)
                    .toList());
            if (largest.size() > MAX_IMPACT_ROWS) {
                rule.put("more_feeds_and_types", largest.size() - MAX_IMPACT_ROWS);
            }
            out.add(rule);
        });
        return out;
    }

    private static Map<String, Object> impactRow(final DataRetentionDeleteSummary summary) {
        final Map<String, Object> out = new LinkedHashMap<>();
        out.put("feed", summary.getFeed());
        out.put("type", summary.getType());
        out.put("streams", summary.getCount());
        return out;
    }

    private static Map<String, Object> jobs(final RetentionFacts facts) {
        final Map<String, Object> out = new LinkedHashMap<>();
        for (final String name : List.of(RETENTION_JOB, DELETE_JOB)) {
            try {
                out.put(name, facts.job(name)
                        .<Object>map(ExplainRetentionTool::job)
                        .orElse("No such job."));
            } catch (final PermissionException e) {
                out.put(name, "Not checked: it needs the Manage Jobs permission.");
            }
        }
        return out;
    }

    private static Map<String, Object> job(final JobState job) {
        final List<JobNodeState> enabledNodes = NullSafe.list(job.nodes()).stream()
                .filter(JobNodeState::enabled)
                .toList();
        final Map<String, Object> out = new LinkedHashMap<>();
        out.put("enabled", job.enabled());
        out.put("enabled_on", enabledNodes.stream().map(JobNodeState::node).toList());
        if (!job.enabled() || enabledNodes.isEmpty()) {
            out.put("effect", "It isn't enabled on any node, so it doesn't run.");
        }
        enabledNodes.stream().map(JobNodeState::schedule).filter(Objects::nonNull).findFirst()
                .ifPresent(schedule -> out.put("schedule", schedule));
        enabledNodes.stream().map(JobNodeState::lastExecutedMs).filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .ifPresent(ms -> out.put("last_run", DateUtil.createNormalDateTimeString(ms)));
        enabledNodes.stream().map(JobNodeState::nextScheduledMs).filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .ifPresent(ms -> out.put("next_run", DateUtil.createNormalDateTimeString(ms)));
        return out;
    }

    private Long longArg(final ToolCall request, final String name) {
        final Object value = argsMap(request).get(name);
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return Long.parseLong(String.valueOf(value).strip());
    }
}
