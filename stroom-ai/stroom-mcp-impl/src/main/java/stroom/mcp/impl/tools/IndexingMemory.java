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

import stroom.ai.impl.tools.ToolSession;
import stroom.ai.impl.tools.indexing.FieldPlan;
import stroom.security.api.SecurityContext;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.UserRef;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/// What the indexing tools remember for each user, in this node's memory, as the Stroom MCP server kept it in its
/// own (`tools/indexing.py`'s session stores): how the user chose in a form to name the index's fields
/// (`field_naming_chosen`), the time field they picked for a discovery index (`discovery_time_chosen`), the component
/// templates given (`component_templates_given`), the index templates shown for review (`templates_shown`), the plans
/// drafted (`index_plans_drafted`) and the verification checks that passed (`verify_checks_passed`). Each is a
/// convenience: without it, the user is asked again, or the agent gives the template or plan again.
@Singleton
class IndexingMemory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(IndexingMemory.class);

    /// How long a choice, a component template or a template shown is remembered.
    static final Duration CHOICE_TTL = Duration.ofHours(4);
    /// How long a passed verification check is remembered.
    static final Duration PASSED_TTL = Duration.ofHours(1);
    /// The most entries kept in each store: the oldest are forgotten first.
    static final int MAX_ENTRIES = 10_000;

    private final SecurityContext securityContext;
    private final LongSupplier clock;
    private final Map<String, Timed<Map<String, Object>>> naming = bounded();
    private final Map<String, Timed<String>> times = bounded();
    private final Map<String, Timed<Map<String, Object>>> components = bounded();
    private final Map<String, Timed<String>> shown = bounded();
    private final Map<String, Map<String, Object>> plans = bounded();
    private final Map<String, Map<String, Double>> planPopulations = bounded();
    private final Map<String, Timed<Map<String, Map<String, Object>>>> passed = bounded();

    /// @param securityContext Says who the user is.
    @Inject
    IndexingMemory(final SecurityContext securityContext) {
        this(securityContext, System::currentTimeMillis);
    }

    /// As the injected constructor, with the clock given, for tests.
    ///
    /// @param securityContext Says who the user is.
    /// @param clock           The time now, in milliseconds.
    IndexingMemory(final SecurityContext securityContext, final LongSupplier clock) {
        this.securityContext = securityContext;
        this.clock = clock;
    }

    /// Remembers how the user chose, in get_field_conventions' form, to name the index's fields: so
    /// draft_index_mapping doesn't ask them again. Seen in VS Code: the user picked ECS in the form, the agent
    /// drafted with convention=ecs but no without_example, and the user was asked the same question again (and with
    /// it, would have confirmed it again).
    ///
    /// @param choice The choice: `convention` or `like_index`.
    void chooseNaming(final Map<String, Object> choice) {
        naming.put(user(), new Timed<>(new LinkedHashMap<>(choice), now()));
    }

    /// @return The user's own choice in the form, within [#CHOICE_TTL] (only a form's answer: one the agent relays
    /// isn't theirs); empty if none.
    Map<String, Object> namingChosen() {
        final Timed<Map<String, Object>> chosen = naming.get(user());
        return chosen != null && fresh(chosen, CHOICE_TTL)
                ? chosen.value()
                : Map.of();
    }

    /// Remembers the source field the user picked in the form as a discovery index's @timestamp.
    ///
    /// @param indexName The discovery index.
    /// @param field     The field.
    void chooseTime(final String indexName, final String field) {
        times.put(user() + "\n" + indexName, new Timed<>(field, now()));
    }

    /// @param indexName A discovery index.
    /// @return The field the user picked as its @timestamp, within [#CHOICE_TTL].
    Optional<String> timeChosen(final String indexName) {
        final Timed<String> chosen = times.get(user() + "\n" + indexName);
        return chosen != null && fresh(chosen, CHOICE_TTL)
                ? Optional.of(chosen.value())
                : Optional.empty();
    }

    /// The component templates given now, by name, with those of names given earlier: kept so the agent needn't
    /// send them again. Seen in testing: re-sent call after call, the bodies came back corrupted (a GET response
    /// nested in another, an escaped string), and the user's template could never be agreed.
    ///
    /// @param parsed The component templates given now, by name.
    /// @param names  The names the template composes.
    /// @return Those of the names given before, with those given now.
    Map<String, Map<String, Object>> components(final Map<String, Map<String, Object>> parsed,
                                                final List<String> names) {
        final String user = user();
        final long now = now();
        parsed.forEach((name, body) -> components.put(user + "\n" + name, new Timed<>(body, now)));
        final Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        for (final String name : names) {
            final Timed<Map<String, Object>> kept = components.get(user + "\n" + name);
            if (kept != null && fresh(kept, CHOICE_TTL)) {
                out.put(name, kept.value());
            }
        }
        out.putAll(parsed);
        return out;
    }

    /// Remembers the template a needs_review reply shows, by its review id, so the call after the review needn't
    /// send it again (seen in testing: retyped by the agent, the template and its components came back corrupted).
    ///
    /// @param devTools The template, as shown.
    void showTemplate(final String devTools) {
        shown.put(user() + "\n" + Consent.reviewId(devTools), new Timed<>(devTools, now()));
    }

    /// @param reviewed The review id given.
    /// @return The template this user was shown under that id, within [#CHOICE_TTL].
    Optional<String> shownTemplate(final Object reviewed) {
        if (reviewed == null) {
            return Optional.empty();
        }
        final Timed<String> found = shown.get(user() + "\n" + String.valueOf(reviewed).strip()
                .toLowerCase(Locale.ROOT));
        return found != null && fresh(found, CHOICE_TTL)
                ? Optional.of(found.value())
                : Optional.empty();
    }

    /// Keeps a plan drafted or amended, for changes= and the tools that take a plan. Seen in Ask Stroom AI: after
    /// the client's step cap the agent had lost its earlier extra_fields and spent eight calls recovering them, and
    /// pasted the 50-field plan inline five times over.
    ///
    /// @param plan The plan.
    void keepPlan(final FieldPlan plan) {
        plans.put(user() + "\n" + plan.indexName(), plan.toJson());
    }

    /// Keeps how often the Events a plan was drafted from populate each path, so an amendment that gives no
    /// streams is checked against them without reading them again (not in the Stroom MCP server; seen: a field
    /// added by changes= from a path no event has, unchecked).
    ///
    /// @param indexName  The plan's index name.
    /// @param population The % of events each path (and each named Data entry) is populated in; null keeps
    ///                   nothing.
    void keepPlanPopulation(final String indexName, final Map<String, Double> population) {
        if (population != null) {
            planPopulations.put(user() + "\n" + indexName, Map.copyOf(population));
        }
    }

    /// @param indexName A plan's index name.
    /// @return How often the Events this user's plan for it was last checked against populate each path, or
    /// null.
    Map<String, Double> planPopulation(final String indexName) {
        return planPopulations.get(user() + "\n" + indexName);
    }

    /// @return The plans this user drafted or amended, by index name, as kept (JSON).
    Map<String, Map<String, Object>> plans() {
        final String prefix = user() + "\n";
        final Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        synchronized (plans) {
            plans.forEach((key, plan) -> {
                if (key.startsWith(prefix)) {
                    out.put(key.substring(prefix.length()), plan);
                }
            });
        }
        return out;
    }

    /// The plans this user drafted or amended, as save_xslt reads them (the plans are kept per user, as the Stroom
    /// MCP server kept them, so the session doesn't narrow them). A kept plan that no longer reads is left out.
    ///
    /// @param session The session asking.
    /// @return The plans, by index name.
    public Map<String, FieldPlan> drafted(final ToolSession session) {
        final Map<String, FieldPlan> drafted = new LinkedHashMap<>();
        plans().forEach((indexName, json) -> {
            try {
                drafted.put(indexName, FieldPlan.fromJson(json));
            } catch (final IllegalArgumentException e) {
                LOGGER.debug(() -> LogUtil.message(
                        "A kept plan for {} no longer reads: {}",
                        indexName,
                        e.getMessage()));
            }
        });
        return drafted;
    }

    /// The checks that passed earlier, for these streams in this index as the indexing pipeline's current code
    /// wrote them: a store the run adds its own passes to.
    ///
    /// @param key The index, the streams, the documents expected and the code's digest.
    /// @return The checks that passed, by check key; changes to it are kept for [#PASSED_TTL].
    Map<String, Map<String, Object>> passedChecks(final String key) {
        final String full = user() + "\n" + key;
        final Timed<Map<String, Map<String, Object>>> found = passed.get(full);
        if (found != null && fresh(found, PASSED_TTL)) {
            return found.value();
        }
        final Map<String, Map<String, Object>> checks = new ConcurrentHashMap<>();
        passed.put(full, new Timed<>(checks, now()));
        return checks;
    }

    private String user() {
        try {
            return NullSafe.string(NullSafe.get(securityContext.getUserRef(), UserRef::getUuid));
        } catch (final RuntimeException e) {
            return "";
        }
    }

    private long now() {
        return clock.getAsLong();
    }

    private boolean fresh(final Timed<?> timed, final Duration ttl) {
        return now() - timed.atMs() < ttl.toMillis();
    }

    private static <V> Map<String, V> bounded() {
        return Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75F, false) {
            @Override
            protected boolean removeEldestEntry(final Map.Entry<String, V> eldest) {
                return size() > MAX_ENTRIES;
            }
        });
    }


    // --------------------------------------------------------------------------------


    private record Timed<V>(V value, long atMs) {

    }
}
