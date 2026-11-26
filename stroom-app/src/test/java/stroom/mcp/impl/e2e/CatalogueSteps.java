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

package stroom.mcp.impl.e2e;

import stroom.mcp.impl.McpTestClient;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// The plan's catalogue step taken as an agent and user would who take the draft's seed as it is, through any MCP
/// client: shared by the e2e suites ([AbstractE2eTest#cataloguedAsSeeded]) and the onboarding suite outside their
/// package. Written against the `catalogue_event_types` contract (stroom-ai/DESIGN.md, Event type catalogue,
/// Contracts).
public final class CatalogueSteps {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(CatalogueSteps.class);

    /// The tool that catalogues a source's event types.
    public static final String CATALOGUE = "catalogue_event_types";

    // How many confirmation rounds a catalogue may take (sets of ten, then the catch-all).
    private static final int MAX_ROUNDS = 80;

    private CatalogueSteps() {
        // Static only.
    }

    /// The draft's seed taken as it is: the seed's event types added, each the check says may be several (and so
    /// can't be confirmed) said to be one, every set confirmed in its form, then the catch-all.
    ///
    /// @param mcp     The client (one that shows forms, or not: a `needs_review` round is answered with its review
    ///                id).
    /// @param build   The build.
    /// @param streams The sample streams.
    /// @return The last catalogue_event_types answer (its `next` the plan's following step).
    @SuppressWarnings("unchecked")
    public static Map<String, Object> cataloguedAsSeeded(final McpTestClient mcp,
                                                         final String build,
                                                         final List<?> streams) {
        final Map<String, Object> listed = mcp.json(CATALOGUE, args("build", build, "stream_ids", streams));
        dump("listed", listed);
        assertThat(listed.get("evidence")).as("the evidence of %s", listed).isInstanceOf(Map.class);
        final Object seed = ((Map<String, Object>) listed.get("evidence")).get("seed");
        assertThat(seed).as("the draft's seed in %s", listed).isInstanceOf(List.class);
        final List<Map<String, Object>> proposals = new ArrayList<>();
        for (final Map<String, Object> seeded : (List<Map<String, Object>>) seed) {
            final Map<String, Object> proposal = new LinkedHashMap<>(seeded);
            proposal.put("op", "add");
            proposals.add(proposal);
        }
        if (!proposals.isEmpty()) {
            final Map<String, Object> checked = mcp.json(CATALOGUE, args("build", build, "stream_ids", streams,
                    "proposals", proposals));
            dump("seeded", checked);
            final List<Map<String, Object>> saidToBeOne = new ArrayList<>();
            for (final Map<String, Object> several : severalOf(checked)) {
                saidToBeOne.add(args("op", "change", "type_id", several.get("type_id"), "one_type_because",
                        "E2E: taken as the draft seeded it"));
            }
            if (!saidToBeOne.isEmpty()) {
                mcp.json(CATALOGUE, args("build", build, "stream_ids", streams, "proposals", saidToBeOne));
            }
        }
        return confirmedCatalogue(mcp, build);
    }

    /// Every set of the build's catalogue put to the user (`confirm=true`) until the catch-all is confirmed: each
    /// form answered by the client's user, a `needs_review` answered with its review id, the catch-all agreed with
    /// a reason (asked only when some records are uncatalogued; with none, it stands with no form).
    ///
    /// @param mcp   The client.
    /// @param build The build.
    /// @return The last answer.
    public static Map<String, Object> confirmedCatalogue(final McpTestClient mcp, final String build) {
        Object reviewed = null;
        for (int round = 0; round < MAX_ROUNDS; round++) {
            final Map<String, Object> result = mcp.json(CATALOGUE, args("build", build, "confirm", true,
                    "reviewed", reviewed, "catch_all", Map.of("allow_unknown",
                            "E2E: records no event type recognises are kept as Unknown")));
            final String status = String.valueOf(result.get("status"));
            reviewed = null;
            if ("needs_review".equals(status)) {
                reviewed = result.get("review_id");
            } else if (!"needs_confirmation".equals(status) && !confirmsAgain(result)) {
                dump("confirmed", result);
                assertThat(status).as("the catalogue confirmed: %s", result).isEqualTo("confirmed");
                return result;
            }
        }
        throw new AssertionError("The catalogue of " + build + " still asked after " + MAX_ROUNDS + " rounds");
    }

    /// @param result A catalogue_event_types answer.
    /// @return The event types its check says may be several and that can't be confirmed so (blocking).
    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> severalOf(final Map<String, Object> result) {
        if (!(result.get("check") instanceof final Map<?, ?> check)
            || !(check.get("several") instanceof final List<?> several)) {
            return List.of();
        }
        return ((List<Map<String, Object>>) several).stream()
                .filter(each -> Boolean.TRUE.equals(each.get("blocking")))
                .toList();
    }

    // Whether an answer's next is to confirm the build's catalogue again (its next set, or the catch-all).
    private static boolean confirmsAgain(final Map<String, Object> result) {
        return result.get("next") instanceof final Map<?, ?> next
               && next.get("call") instanceof final Map<?, ?> call
               && CATALOGUE.equals(call.get("tool"))
               && call.get("arguments") instanceof final Map<?, ?> arguments
               && Boolean.TRUE.equals(arguments.get("confirm"));
    }

    // Keys and values, in turn, nulls left out.
    private static Map<String, Object> args(final Object... pairs) {
        final Map<String, Object> args = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            if (pairs[i + 1] != null) {
                args.put((String) pairs[i], pairs[i + 1]);
            }
        }
        return args;
    }

    private static void dump(final String step, final Object result) {
        LOGGER.info(() -> LogUtil.message("{} {}: {}", CATALOGUE, step, JsonUtil.writeValueAsString(result, true)));
    }
}
