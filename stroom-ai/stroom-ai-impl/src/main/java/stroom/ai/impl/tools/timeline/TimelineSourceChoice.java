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

package stroom.ai.impl.tools.timeline;

import stroom.ai.impl.TabSnapshots;
import stroom.ai.impl.TabSnapshots.StoredTabSnapshot;
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.UserQuestions;
import stroom.ai.impl.tools.query.DataSourceAccess;
import stroom.ai.impl.tools.query.ResultsRequests;
import stroom.ai.impl.workspace.WorkspaceException;
import stroom.ai.shared.AiTabSearch;
import stroom.dashboard.shared.DashboardDoc;
import stroom.docref.DocRef;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.util.shared.NullSafe;

import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Which data sources an activity timeline reads when neither the call nor a skill names them: asked in a chat's
/// form (with a dashboard attached, only its data source, all supported data sources, or the user's own words;
/// without one, all supported by default, or the user's own words, matched to the indexes and shown to confirm);
/// over MCP, with no form, all supported, said in the result. Supported: every Elastic Index the user can use.
class TimelineSourceChoice {

    /// The form's key.
    static final String FORM = "timeline-sources";
    /// The confirming form's key.
    static final String CONFIRM_FORM = "timeline-sources-confirm";
    static final String CHOICE = "sources";
    static final String OTHER = "other";
    static final String INDEXES = "indexes";
    static final String ALL = "All supported data sources";
    static final String OWN_WORDS = "Other (say which)";
    static final String DASHBOARD = "Only the dashboard's data source";
    private static final Pattern WORD = Pattern.compile("[A-Za-z][A-Za-z0-9]{2,}");

    private final DataSourceAccess dataSourceAccess;
    private final Provider<TabSnapshots> tabSnapshotsProvider;

    /// @param dataSourceAccess     Lists the Elastic Indexes the user can use.
    /// @param tabSnapshotsProvider Gives the dashboard attached to the chat's question.
    @Inject
    TimelineSourceChoice(final DataSourceAccess dataSourceAccess, final Provider<TabSnapshots> tabSnapshotsProvider) {
        this.dataSourceAccess = dataSourceAccess;
        this.tabSnapshotsProvider = tabSnapshotsProvider;
    }

    /// @return Every Elastic Index the user can use, by name.
    List<DocRef> supported() {
        return dataSourceAccess.listUsable().stream()
                .filter(doc -> ElasticIndexDoc.TYPE.equals(doc.getType()))
                .toList();
    }

    /// Asks the user which sources to read, or takes them all where they can't be asked.
    ///
    /// @param request  The call.
    /// @param context  The call's context.
    /// @param toolName The tool asking.
    /// @return The indexes chosen and how.
    Chosen choose(final ToolCall request, final ToolCallContext context, final String toolName) {
        final List<DocRef> supported = supported();
        if (supported.isEmpty()) {
            throw new WorkspaceException("You can't use any Elastic Index, so there is nothing to build a timeline "
                                         + "from.");
        }
        if (!context.session().isChat() || !context.userQuestions().canAsk()) {
            return new Chosen(supported, true, "all supported data sources (" + supported.size() + " Elastic "
                                               + "Index(es) you can use): no form could ask which");
        }
        final List<DocRef> dashboard = dashboardSources(context);
        final List<String> choices = new ArrayList<>();
        if (!dashboard.isEmpty()) {
            choices.add(DASHBOARD + " (" + String.join(", ", dashboard.stream().map(DocRef::getName).toList())
                        + ")");
        }
        choices.add(ALL);
        choices.add(OWN_WORDS);
        final Map<String, Object> form = new LinkedHashMap<>();
        form.put("type", "object");
        final Map<String, Object> properties = new LinkedHashMap<>();
        properties.put(CHOICE, Map.of("type", "string", "title", "Which data sources?", "enum", choices,
                "default", choices.getFirst()));
        properties.put(OTHER, Map.of("type", "string", "title", "Other: which, in your words",
                "description", "e.g. web proxy logs and Windows events"));
        form.put("properties", properties);
        form.put("required", List.of(CHOICE));
        final ElicitResult answer = context.userQuestions().ask(toolName, UserQuestions.formCallId(request.id(),
                FORM), "Which data sources should the timeline read? Supported: the " + supported.size()
                       + " Elastic Index(es) you can use.", form);
        if (answer.action() != ElicitResult.Action.ACCEPT) {
            throw new WorkspaceException("The user didn't choose the data sources, so no timeline was built: ask "
                                         + "which to use, or give sources.");
        }
        final Map<String, Object> content = NullSafe.map(answer.content());
        final String choice = String.valueOf(content.get(CHOICE));
        if (choice.startsWith(DASHBOARD)) {
            return new Chosen(dashboard, false, "the attached dashboard's data source, as the user chose");
        }
        if (ALL.equals(choice)) {
            return new Chosen(supported, true, "all supported data sources, as the user chose");
        }
        final String words = String.valueOf(Objects.requireNonNullElse(content.get(OTHER), "")).strip();
        return confirm(request, context, toolName, words, supported);
    }

    // The indexes the user's words name, shown to confirm.
    private Chosen confirm(final ToolCall request,
                           final ToolCallContext context,
                           final String toolName,
                           final String words,
                           final List<DocRef> supported) {
        final Matched matched = match(words, supported, this::documentation);
        final Map<String, DocRef> byLabel = labels(matched.listed().isEmpty()
                ? supported
                : matched.listed());
        final List<String> preselected = byLabel.entrySet().stream()
                .filter(entry -> matched.preselected().contains(entry.getValue()))
                .map(Map.Entry::getKey)
                .toList();
        final Map<String, Object> field = new LinkedHashMap<>();
        field.put("type", "array");
        field.put("title", "These data sources?");
        field.put("items", Map.of("type", "string", "enum", List.copyOf(byLabel.keySet())));
        field.put("default", preselected);
        final Map<String, Object> form = new LinkedHashMap<>();
        form.put("type", "object");
        form.put("properties", Map.of(INDEXES, field));
        form.put("required", List.of(INDEXES));
        final ElicitResult answer = context.userQuestions().ask(toolName, UserQuestions.formCallId(request.id(),
                CONFIRM_FORM), matched.listed().isEmpty()
                ? "No data source's name or documentation matches '" + words + "'. Which of these?"
                : "The data sources matching '" + words + "'. Read these?", form);
        if (answer.action() != ElicitResult.Action.ACCEPT) {
            throw new WorkspaceException("The user didn't confirm the data sources, so no timeline was built.");
        }
        final Object picked = NullSafe.map(answer.content()).get(INDEXES);
        final List<DocRef> chosen = new ArrayList<>();
        if (picked instanceof final List<?> list) {
            list.forEach(label -> NullSafe.consume(byLabel.get(String.valueOf(label)), chosen::add));
        } else if (picked != null) {
            NullSafe.consume(byLabel.get(String.valueOf(picked)), chosen::add);
        }
        if (chosen.isEmpty()) {
            throw new WorkspaceException("The user chose no data source, so no timeline was built.");
        }
        return new Chosen(chosen, false, "the data sources matching the user's words '" + words + "', as they "
                                         + "confirmed");
    }

    private String documentation(final DocRef doc) {
        try {
            return dataSourceAccess.documentation(doc).orElse("");
        } catch (final RuntimeException e) {
            return "";
        }
    }

    /// The indexes some words name: by their names and documentation. A word most of the indexes match (e.g.
    /// "events") says nothing of which, so only the others' matches are chosen beforehand.
    ///
    /// @param words         The user's words.
    /// @param supported     The indexes.
    /// @param documentation Each index's documentation.
    /// @return Those any word matches, and those a telling word matches.
    static Matched match(final String words,
                         final List<DocRef> supported,
                         final Function<DocRef, String> documentation) {
        final Set<String> wanted = new LinkedHashSet<>();
        final Matcher matcher = WORD.matcher(NullSafe.string(words));
        while (matcher.find()) {
            wanted.add(stem(matcher.group().toLowerCase(Locale.ROOT)));
        }
        final Map<DocRef, Set<String>> hits = new LinkedHashMap<>();
        final Map<String, Integer> indexesPerWord = new LinkedHashMap<>();
        for (final DocRef doc : supported) {
            final Set<String> theirs = new LinkedHashSet<>();
            final Matcher own = WORD.matcher(doc.getName() + " " + NullSafe.string(documentation.apply(doc)));
            while (own.find()) {
                theirs.add(stem(own.group().toLowerCase(Locale.ROOT)));
            }
            final Set<String> shared = new LinkedHashSet<>(wanted);
            shared.retainAll(theirs);
            if (!shared.isEmpty()) {
                hits.put(doc, shared);
                shared.forEach(word -> indexesPerWord.merge(word, 1, Integer::sum));
            }
        }
        final List<DocRef> preselected = new ArrayList<>();
        hits.forEach((doc, shared) -> {
            if (supported.size() < 3 || shared.stream().anyMatch(word -> indexesPerWord.get(word) * 2
                                                                         <= supported.size())) {
                preselected.add(doc);
            }
        });
        return new Matched(List.copyOf(hits.keySet()), preselected);
    }

    private static String stem(final String word) {
        return word.length() > 3 && word.endsWith("s")
                ? word.substring(0, word.length() - 1)
                : word;
    }

    // Each index by a label naming it, its uuid's start added where two share a name.
    private static Map<String, DocRef> labels(final List<DocRef> docs) {
        final Map<String, Integer> names = new LinkedHashMap<>();
        docs.forEach(doc -> names.merge(doc.getName(), 1, Integer::sum));
        final Map<String, DocRef> out = new LinkedHashMap<>();
        for (final DocRef doc : docs) {
            out.put(names.get(doc.getName()) > 1
                    ? doc.getName() + " (" + doc.getUuid().substring(0, Math.min(8, doc.getUuid().length())) + ")"
                    : doc.getName(), doc);
        }
        return out;
    }

    // The Elastic Indexes the dashboard attached to the chat's latest question searches.
    private List<DocRef> dashboardSources(final ToolCallContext context) {
        try {
            if (context.session().chatId().isEmpty()) {
                return List.of();
            }
            final StoredTabSnapshot tab = tabSnapshotsProvider.get()
                    .forLatestQuestion(context.session().chatId().getAsInt()).orElse(null);
            if (tab == null || tab.docRef() == null || !DashboardDoc.TYPE.equals(tab.docRef().getType())) {
                return List.of();
            }
            final Set<DocRef> found = new LinkedHashSet<>();
            for (final AiTabSearch search : NullSafe.list(tab.searches())) {
                for (final DocRef doc : new DocRef[]{ResultsRequests.dashboardDataSource(search.getResults()),
                        ResultsRequests.dashboardDataSource(search.getLive())}) {
                    if (doc != null && ElasticIndexDoc.TYPE.equals(doc.getType()) && dataSourceAccess.canUse(doc)) {
                        found.add(doc);
                    }
                }
            }
            return List.copyOf(found);
        } catch (final RuntimeException e) {
            return List.of();
        }
    }


    // --------------------------------------------------------------------------------


    /// The sources chosen.
    ///
    /// @param indexes The indexes.
    /// @param all     True if they are all supported: those without the user's events are left out, listed.
    /// @param how     How they were chosen, for the result.
    record Chosen(List<DocRef> indexes, boolean all, String how) {

        /// Copies the list.
        Chosen {
            indexes = List.copyOf(indexes);
        }
    }


    // --------------------------------------------------------------------------------


    /// The indexes some words name.
    ///
    /// @param listed      Those any word matches.
    /// @param preselected Those a telling word matches.
    record Matched(List<DocRef> listed, List<DocRef> preselected) {

        /// Copies the lists.
        Matched {
            listed = List.copyOf(listed);
            preselected = List.copyOf(preselected);
        }
    }
}
