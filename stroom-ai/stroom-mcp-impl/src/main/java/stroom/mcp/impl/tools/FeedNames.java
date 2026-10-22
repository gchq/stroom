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
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.UserQuestions;
import stroom.ai.impl.tools.validation.CloseMatches;
import stroom.docref.DocRef;
import stroom.docstore.api.DocFinder;
import stroom.feed.shared.FeedDoc;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;

/// Feeds by the names users give them: Stroom files a feed's streams under its name without regard to case, and a
/// user may misremember a name, or mean a feed of another Stroom.
class FeedNames {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(FeedNames.class);

    static final String NOT_LISTED = "None of these: I'll correct the name (or the cluster)";
    static final String CHOICE_FIELD = "feed";
    /// How many close names are offered.
    static final int CLOSEST = 3;

    private final Provider<DocFinder> docFinderProvider;
    private final DocumentAccess documentAccess;

    /// @param docFinderProvider Finds feeds by name.
    /// @param documentAccess    Checks View on each.
    @Inject
    FeedNames(final Provider<DocFinder> docFinderProvider, final DocumentAccess documentAccess) {
        this.docFinderProvider = docFinderProvider;
        this.documentAccess = documentAccess;
    }

    /// @param name A feed's name, in any case.
    /// @return The feed documents the user can view whose names are this one whatever their case: all of them
    /// share one set of streams, under whichever spelling Stroom saw first.
    List<DocRef> feedsNamed(final String name) {
        return all().stream()
                .filter(feed -> name.equalsIgnoreCase(feed.getName()))
                .toList();
    }

    /// @param name A feed's name, exactly.
    /// @return The feed of exactly that name, if the user can view it (Stroom keeps one feed per name). A direct
    /// lookup, as the Stroom MCP server's `/feed/v1/getDocRefForName`.
    Optional<DocRef> exact(final String name) {
        return docFinder(name).stream().findFirst();
    }

    /// The feed the user named, as Stroom spells it. Not here by that name: up to three close matches offered in
    /// a form, with a fourth choice to correct the name, the pick returned; none close, or no form, refused,
    /// naming this Stroom (asked for by the user: a feed of another cluster must stop the work, not lead the agent
    /// to use or make another).
    ///
    /// @param name     The name the user gave.
    /// @param toolName The tool asking.
    /// @param callId   The call asking, or null.
    /// @param context  The call's context, to ask the user in.
    /// @return The feed's name as Stroom spells it.
    /// @throws ToolRefusal if it isn't here, or the user didn't pick one.
    String resolve(final String name, final String toolName, final String callId, final ToolCallContext context) {
        final String given = name.strip();
        final List<DocRef> exact = docFinder(given);
        if (!exact.isEmpty()) {
            return NullSafe.nonBlankStringElse(exact.getFirst().getName(), given);
        }
        final List<DocRef> twins = feedsNamed(given);
        if (!twins.isEmpty()) {
            // The same name in another case.
            return twins.getFirst().getName();
        }
        final List<String> near = closest(given, all().stream().map(DocRef::getName).toList(), CLOSEST);
        if (near.isEmpty()) {
            throw new ToolRefusal(notFound("No feed named '" + given + "', nor any like it,"));
        }
        final UserQuestions questions = context.userQuestions();
        if (questions.canAsk()) {
            final List<String> options = new ArrayList<>(near);
            options.add(NOT_LISTED);
            final ElicitResult answer = questions.ask(toolName, callId, "There is no feed named '" + given
                                                                       + "' in this Stroom. Did you mean one of "
                                                                       + "these?", form(options));
            final Object chosen = answer.action() == ElicitResult.Action.ACCEPT
                    ? NullSafe.map(answer.content()).get(CHOICE_FIELD)
                    : null;
            if (chosen != null && near.contains(String.valueOf(chosen))) {
                return String.valueOf(chosen);
            }
            if (NOT_LISTED.equals(chosen)) {
                throw new ToolRefusal("The user said '" + given + "' is none of " + near + ": ask them for the "
                                      + "feed's exact name, and whether this Stroom is the cluster they mean. "
                                      + "Don't go on until they say.");
            }
        }
        throw new ToolRefusal(notFound("No feed named '" + given + "'") + " The closest here: "
                              + String.join(", ", near) + ". Ask the user which they meant, offering these and a "
                              + "fourth choice to correct the name; don't pick one yourself.");
    }

    /// @param what What isn't here, e.g. `No feed named 'X'`.
    /// @return A refusal for a feed or document that isn't in this Stroom: the user may mean another cluster
    /// (asked for by the user: a UUID from one cluster given to another must stop the work, not lead to other
    /// actions).
    static String notFound(final String what) {
        return what + " in this Stroom. If the user named it, they may mean a different Stroom cluster: stop, tell "
               + "them which Stroom this is, and ask them to check the cluster and the name or UUID. Don't look "
               + "for another document to use instead, and don't create one.";
    }

    /// @param name A feed's name, as given.
    /// @return The refusal of a feed name that is no feed's here ([#notFound(String)]).
    static ToolRefusal noFeedNamed(final String name) {
        return new ToolRefusal(notFound("No feed named '" + name + "'"));
    }

    /// @param name  The name given.
    /// @param names The names there are.
    /// @param count How many to give at most.
    /// @return The names most like the one given: one holding the other first, then by how alike they are.
    static List<String> closest(final String name, final List<String> names, final int count) {
        final String want = name.toLowerCase(Locale.ROOT);
        final Map<String, Double> scores = new LinkedHashMap<>();
        for (final String other : new TreeSet<>(names.stream().filter(NullSafe::isNonEmptyString).toList())) {
            final String low = other.toLowerCase(Locale.ROOT);
            scores.put(other, CloseMatches.ratio(want, low) + (want.contains(low) || low.contains(want)
                    ? 0.5
                    : 0));
        }
        return scores.entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry<String, Double>::getValue, Comparator.reverseOrder())
                        .thenComparing(Map.Entry::getKey))
                .filter(entry -> entry.getValue() >= 0.5)
                .map(Map.Entry::getKey)
                .limit(count)
                .toList();
    }

    private List<DocRef> docFinder(final String name) {
        try {
            return NullSafe.list(docFinderProvider.get().findByName(FeedDoc.TYPE, name)).stream()
                    .filter(documentAccess::canView)
                    .toList();
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "Unable to find the feed " + name, e);
            return List.of();
        }
    }

    private List<DocRef> all() {
        try {
            return NullSafe.list(docFinderProvider.get().findByName(FeedDoc.TYPE, "*", true)).stream()
                    .filter(feed -> feed.getName() != null)
                    .filter(documentAccess::canView)
                    .sorted(Comparator.comparing(DocRef::getName))
                    .toList();
        } catch (final RuntimeException e) {
            LOGGER.debug("Unable to list the feeds", e);
            return List.of();
        }
    }

    private static Map<String, Object> form(final List<String> options) {
        final Map<String, Object> field = new LinkedHashMap<>();
        field.put("type", "string");
        field.put("title", "Which feed?");
        field.put("enum", options);
        final Map<String, Object> properties = new LinkedHashMap<>();
        properties.put(CHOICE_FIELD, field);
        final Map<String, Object> form = new LinkedHashMap<>();
        form.put("type", "object");
        form.put("properties", properties);
        form.put("required", List.of(CHOICE_FIELD));
        return form;
    }
}
