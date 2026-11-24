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

package stroom.ai.impl.tools.compare;

import stroom.ai.impl.AiAuditLog;
import stroom.ai.impl.AiWorkspaceConfig;
import stroom.ai.impl.tools.AbstractWorkspaceTool;
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolDefinition;
import stroom.ai.impl.tools.ToolDescription;
import stroom.ai.impl.tools.ToolPermissions;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.impl.tools.ToolTables;
import stroom.ai.impl.tools.UserQuestions;
import stroom.ai.impl.tools.compare.ActivityProfiles.Fields;
import stroom.ai.impl.tools.compare.ActivityProfiles.Scope;
import stroom.ai.impl.tools.compare.ActivityScoring.PeerData;
import stroom.ai.impl.tools.compare.ActivityScoring.Scored;
import stroom.ai.impl.tools.compare.ActivityScoring.SourceData;
import stroom.ai.impl.tools.compare.ActivityScoring.Spec;
import stroom.ai.impl.tools.compare.ActivityScoring.Unit;
import stroom.ai.impl.tools.compare.AggregateQuery.Source;
import stroom.ai.impl.tools.compare.AggregateRunner.Context;
import stroom.ai.impl.tools.compare.AggregateRunner.Table;
import stroom.ai.impl.tools.instructions.InstructionRules;
import stroom.ai.impl.tools.query.DataSourceAccess;
import stroom.ai.impl.tools.query.EsqlQueryTool;
import stroom.ai.impl.tools.query.StroomQlDatePoints;
import stroom.ai.impl.tools.sequence.EqlSequenceResults;
import stroom.ai.impl.tools.time.ToolTimeZone;
import stroom.ai.impl.tools.time.UserTimeZones;
import stroom.ai.impl.tools.timeline.PersonScope;
import stroom.ai.impl.tools.timeline.TimelineFields;
import stroom.ai.impl.workspace.ChatWorkspace;
import stroom.ai.impl.workspace.SessionWorkspace;
import stroom.ai.impl.workspace.WorkspaceException;
import stroom.ai.impl.workspace.WorkspacePath;
import stroom.ai.shared.AiToolCategory;
import stroom.docref.DocRef;
import stroom.index.shared.LuceneIndexDoc;
import stroom.query.api.datasource.FieldType;
import stroom.query.api.datasource.QueryField;
import stroom.query.impl.QueryService;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.security.shared.DocumentPermission;
import stroom.util.json.JsonUtil;
import stroom.util.shared.NullSafe;

import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// `compare_activity` - compares one person's activity in a window with their own baseline and their peers', from
/// index aggregates only (ES|QL on Elastic Indexes, StroomQL on others, in monthly slices in parallel), never the
/// events themselves: 12 months is a few thousand numbers. The features, checks and scoring are
/// [ActivityProfiles] and [ActivityScoring]; the peers' groups are read from one event each ([PeerGroups]). The
/// model gets at most [#MAX_FINDINGS] ranked findings, worded as indicators with an alternative explanation, and a
/// line a month; the daily profile, the monthly rollup and every finding are workspace files. A re-run asks only for
/// the days since ([CachedAggregates]).
public class CompareActivityTool extends AbstractWorkspaceTool {

    static final String NAME = "compare_activity";
    static final String PERSON_ARG = "person";
    static final String LOGON_IDS_ARG = "logonIds";
    static final String PERSON_FIELD_ARG = "personField";
    static final String USER_FIELD_ARG = "userField";
    static final String WINDOW_FROM_ARG = "windowFrom";
    static final String WINDOW_TO_ARG = "windowTo";
    static final String BASELINE_FROM_ARG = "baselineFrom";
    static final String BASELINE_TO_ARG = "baselineTo";
    static final String UNIT_ARG = "unit";
    static final String SOURCES_ARG = "sources";
    static final String SUM_FIELDS_ARG = "sumFields";
    static final String BREADTH_FIELDS_ARG = "breadthFields";
    static final String LEAVE_ARG = "leave";
    static final String EXCEPTIONS_ARG = "exceptions";
    static final String PEERS_ARG = "peers";
    static final String CATEGORIES_ARG = "categories";
    static final String CATEGORY_FIELD_ARG = "categoryField";
    static final String CATEGORY_LIST_ARG = "categoryList";
    static final String CATEGORY_CORRECTIONS_ARG = "categoryCorrections";
    static final String CATEGORIES_FILE = "categories.csv";
    static final String PEER_ELEMENT_ARG = "peerElement";
    /// The most findings the result gives; the rest are in the findings file.
    static final int MAX_FINDINGS = 20;
    /// The most people whose group is read from their events in a call.
    static final int MAX_PEOPLE = 200;
    /// The days a window is by default.
    static final int DEFAULT_WINDOW_DAYS = 30;
    /// The days a baseline is by default.
    static final int DEFAULT_BASELINE_DAYS = 180;
    /// The longest period, in days.
    static final int MAX_DAYS = 3 * 366;
    static final String FOLDER = "/outputs/compare/";
    static final String PROFILE_FILE = "profile.csv";
    static final String MONTHLY_FILE = "monthly.csv";
    static final String FINDINGS_FILE = "findings.csv";
    static final String VALUES_FILE = "values.csv";
    static final String CACHE_FILE = "aggregates.json";
    static final String PEERS_FORM = "compare-peers";
    private static final Pattern DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    private static final Pattern RANGE = Pattern.compile("\\s*(\\d{4}-\\d{2}-\\d{2})\\s*(?:(?:\\.\\.|/|\\s+to\\s+)"
                                                         + "\\s*(\\d{4}-\\d{2}-\\d{2}))?\\s*");
    private static final Pattern UNSAFE = Pattern.compile("[^A-Za-z0-9._-]+");
    private static final String READ_FIRST = "Read the groups of the first " + MAX_PEOPLE;
    private static final String NO_PEERS = "Compare without peers";

    private final DataSourceAccess dataSourceAccess;
    private final Provider<QueryService> queryServiceProvider;
    private final Provider<? extends AggregateRunner> runnerProvider;
    private final TimelineFields timelineFields;
    private final PeerGroups peerGroups;
    private final AiAuditLog auditLog;

    /// @param workspace            The chats' workspaces.
    /// @param dataSourceAccess     Finds the indexes the user can use.
    /// @param queryServiceProvider Gives each index's fields.
    /// @param runnerProvider       Runs the aggregates.
    /// @param timelineFields       Finds each index's person, user and breadth fields.
    /// @param peerGroups           Reads the peers' groups.
    /// @param auditLog             Audits what each index was searched for.
    @Inject
    CompareActivityTool(final ChatWorkspace workspace,
                        final DataSourceAccess dataSourceAccess,
                        final Provider<QueryService> queryServiceProvider,
                        final Provider<IndexAggregates> runnerProvider,
                        final TimelineFields timelineFields,
                        final PeerGroups peerGroups,
                        final AiAuditLog auditLog) {
        super(workspace);
        this.dataSourceAccess = dataSourceAccess;
        this.queryServiceProvider = queryServiceProvider;
        this.runnerProvider = runnerProvider;
        this.timelineFields = timelineFields;
        this.peerGroups = peerGroups;
        this.auditLog = auditLog;
    }

    /// For tests: the aggregates run by the runner given (e.g. over events held in memory).
    ///
    /// @param workspace            The chats' workspaces.
    /// @param dataSourceAccess     Finds the indexes the user can use.
    /// @param queryServiceProvider Gives each index's fields.
    /// @param runner               Runs the aggregates.
    /// @param timelineFields       Finds each index's person, user and breadth fields.
    /// @param peerGroups           Reads the peers' groups.
    /// @param auditLog             Audits what each index was searched for.
    CompareActivityTool(final ChatWorkspace workspace,
                        final DataSourceAccess dataSourceAccess,
                        final Provider<QueryService> queryServiceProvider,
                        final AggregateRunner runner,
                        final TimelineFields timelineFields,
                        final PeerGroups peerGroups,
                        final AiAuditLog auditLog) {
        super(workspace);
        this.dataSourceAccess = dataSourceAccess;
        this.queryServiceProvider = queryServiceProvider;
        this.runnerProvider = () -> runner;
        this.timelineFields = timelineFields;
        this.peerGroups = peerGroups;
        this.auditLog = auditLog;
    }

    @Override
    public ToolPermissions permissions() {
        return ToolPermissions.builder()
                .checksDocuments(DocumentPermission.USE)
                .note("Use on every index is checked, as for any search, again by Stroom as each aggregate runs. "
                      + "The peers' groups are read from one event each as the data viewer reads it (View Data, "
                      + "and the stream store's check on each stream's feed); without View Data there are no "
                      + "peers. No permission of its own: the tool policies decide who may compare a person's "
                      + "activity.")
                .build();
    }

    /// Offered to a user who can use an Elastic or Lucene index.
    ///
    /// @return True if the user can use one.
    @Override
    public boolean isUsable() {
        return dataSourceAccess.canUseAny(ElasticIndexDoc.TYPE) || dataSourceAccess.canUseAny(LuceneIndexDoc.TYPE);
    }

    @Override
    public boolean usesRunSlot() {
        // It runs searches.
        return true;
    }

    @Override
    public AiToolCategory category() {
        return AiToolCategory.SEARCH;
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(NAME)
                .description(ToolDescription.builder()
                        .general("Compares one person's activity in a window with their own baseline and their "
                                 + "peers' (the same UserDetails Organisation, read from one event each), from "
                                 + "index aggregates only, never the events: volume (events, active hours, sums), "
                                 + "rhythm (first and last of the day, hour of the week), breadth (distinct "
                                 + "values) and novelty (values first seen). Scored in code: robust z-scores, "
                                 + "change points with their date, drifts, values started and stopped; coverage "
                                 + "gaps, leave and source-wide shifts are checked first. Gives at most "
                                 + MAX_FINDINGS + " ranked findings, each an indicator with its evidence and an "
                                 + "alternative explanation (never a conclusion about the person: say so), a line "
                                 + "a month, and CSV files of the daily profile and monthly rollup. Give person "
                                 + "(the person id) or logonIds where there is none.")
                        .chatOnly(" Drill into flagged days with activity_timeline.")
                        .build())
                .parameters(ObjectSchema.builder()
                        .addStringProperty(PERSON_ARG, "The person id, as the indexes' person field holds it.")
                        .addProperty(LOGON_IDS_ARG, stringArraySchema("Logon ids (accounts) to use where an index "
                                                                      + "has no person field, each kept visible; "
                                                                      + "other accounts aren't included."))
                        .addStringProperty(PERSON_FIELD_ARG, "Optional: the person field, where the AGENTS doc's "
                                                             + "## Fields or the convention don't give it.")
                        .addStringProperty(USER_FIELD_ARG, "Optional: the user (logon id) field, likewise.")
                        .addStringProperty(WINDOW_FROM_ARG, "The window's start: a date (yyyy-MM-dd) or Stroom's "
                                                            + "now()-30d (default " + DEFAULT_WINDOW_DAYS
                                                            + " days before windowTo).")
                        .addStringProperty(WINDOW_TO_ARG, "The window's end, exclusive (default now()).")
                        .addStringProperty(BASELINE_FROM_ARG, "The baseline's start (default "
                                                              + DEFAULT_BASELINE_DAYS + " days before "
                                                              + "baselineTo).")
                        .addStringProperty(BASELINE_TO_ARG, "The baseline's end, exclusive (default windowFrom).")
                        .addEnumProperty(UNIT_ARG, List.of("day", "week", "month"), "What the window is compared "
                                                                                   + "in (default week).")
                        .addProperty(SOURCES_ARG, stringArraySchema("The indexes by name or uuid (default: every "
                                                                    + "Elastic and Lucene index you can use with a "
                                                                    + "person or user field)."))
                        .addProperty(SUM_FIELDS_ARG, stringArraySchema("Numeric fields to sum a day, e.g. bytes "
                                                                       + "sent."))
                        .addProperty(BREADTH_FIELDS_ARG, stringArraySchema("Fields whose distinct and new values "
                                                                           + "to count a day (default: the host, "
                                                                           + "application and object fields "
                                                                           + "found)."))
                        .addProperty(LEAVE_ARG, stringArraySchema("The person's leave, each yyyy-MM-dd..yyyy-MM-dd, "
                                                                  + "from the question, skill or AGENTS doc: not "
                                                                  + "scored."))
                        .addProperty(EXCEPTIONS_ARG, stringArraySchema("Approved exceptions, each "
                                                                       + "yyyy-MM-dd..yyyy-MM-dd: reason (e.g. an "
                                                                       + "approved migration), from the question, "
                                                                       + "skill or AGENTS doc: not scored."))
                        .addBooleanProperty(PEERS_ARG, "Compare with peers (default true).")
                        .addBooleanProperty(CATEGORIES_ARG, "Add events a day by category (personal cloud "
                                                            + "storage, webmail, job search...) of the first "
                                                            + "breadth field's values: from categoryField where the "
                                                            + "index has one, else classified by the model (marked "
                                                            + "inferred). Default false.")
                        .addStringProperty(CATEGORY_FIELD_ARG, "Optional: the index's own category field, e.g. a "
                                                               + "proxy's URL category.")
                        .addProperty(CATEGORY_LIST_ARG, stringArraySchema("The site's own categories, each "
                                                                          + "value=category (from the AGENTS doc "
                                                                          + "or skill): override the rest."))
                        .addProperty(CATEGORY_CORRECTIONS_ARG, stringArraySchema("Corrections of categories "
                                                                                 + "inferred, each "
                                                                                 + "value=category: kept for "
                                                                                 + "re-runs."))
                        .addEnumProperty(PEER_ELEMENT_ARG, PeerGroups.ELEMENTS.stream().sorted().toList(),
                                "The UserDetails element peers share (default Organisation).")
                        .addStringProperty(UserTimeZones.ARG, UserTimeZones.DESCRIPTION
                                                              + " The person's days and hours are in it.")
                        .build())
                .build();
    }

    @Override
    protected Duration timeLimit(final ToolCallContext context) {
        // Many aggregates, each within the query time limit.
        return limits(context).getQueryTimeLimit().getDuration().multipliedBy(4);
    }

    @Override
    protected ToolResult run(final ToolCall request, final ToolCallContext context) {
        final SessionWorkspace workspace = sessionWorkspace(context);
        final AiWorkspaceConfig config = limits(context);
        final String person = stringArg(request, PERSON_ARG);
        final List<String> logonIds = NullSafe.list(stringListArg(request, LOGON_IDS_ARG));
        if (NullSafe.isBlankString(person) && logonIds.isEmpty()) {
            throw new WorkspaceException("Give " + PERSON_ARG + " (the person id) or " + LOGON_IDS_ARG + " (their "
                                         + "accounts, where an index has no person field).");
        }
        final ToolTimeZone zone = timeZone(request, context);
        final long now = System.currentTimeMillis();
        final Periods periods = periods(request, zone, now);
        final Unit unit = unit(stringArg(request, UNIT_ARG));
        final List<LocalDate[]> leave = leave(stringListArg(request, LEAVE_ARG));
        final Spec spec = new Spec(periods.baselineFrom(), periods.baselineTo(), periods.windowFrom(),
                periods.windowTo(), unit, leave, zone.zone(), exceptions(stringListArg(request, EXCEPTIONS_ARG)));
        final String element = peerElement(stringArg(request, PEER_ELEMENT_ARG));
        final boolean peers = booleanArg(request, PEERS_ARG, true);
        final boolean categories = booleanArg(request, CATEGORIES_ARG, false);
        final Map<String, String> siteList = pairs(stringListArg(request, CATEGORY_LIST_ARG), CATEGORY_LIST_ARG);
        final Map<String, String> corrections = pairs(stringListArg(request, CATEGORY_CORRECTIONS_ARG),
                CATEGORY_CORRECTIONS_ARG);

        final List<Planned> planned = plan(request, context, person, logonIds);
        final String folder = FOLDER + safe(NullSafe.isBlankString(person)
                ? String.join("_", logonIds)
                : person) + "/";
        final AggregateRunner runner = new CachedAggregates(runnerProvider.get(), workspace,
                WorkspacePath.parse(folder + CACHE_FILE));
        final Context runContext = new Context(zone.zone(), limits(context).getQueryTimeLimit().getDuration(),
                config.getJoinWorkers(), context.stopped());
        final Instant from = periods.baselineFrom().atStartOfDay(zone.zone()).toInstant();
        final Instant to = periods.windowTo().atStartOfDay(zone.zone()).toInstant();

        context.progress().report("Aggregating " + planned.size() + " source(s) for " + periods.describe());
        final List<AggregateQuery> queries = new ArrayList<>();
        final List<int[]> at = new ArrayList<>();
        for (final Planned source : planned.stream().filter(Planned::usable).toList()) {
            final int start = queries.size();
            queries.add(ActivityProfiles.daily(source.source(), source.scope(), source.fields(), from, to));
            queries.add(ActivityProfiles.hourly(source.source(), source.scope(), from, to));
            queries.add(ActivityProfiles.population(source.source(), source.scope().field(), from, to));
            for (final String field : source.fields().breadth()) {
                queries.add(ActivityProfiles.seen(source.source(), source.scope(), field, from, to));
            }
            if (categories && !source.fields().breadth().isEmpty()) {
                queries.add(categoryQuery(source, from, to));
            }
            final boolean peersHere = peers && source.hasEventIds();
            if (peersHere) {
                queries.add(ActivityProfiles.active(source.source(), source.scope().field(), from, to,
                        ActivityProfiles.MAX_ROWS));
            }
            at.add(new int[]{start, queries.size() - start});
        }
        final List<Table> tables = runner.run(queries, runContext);
        final List<String> notes = new ArrayList<>();
        final List<SourceData> data = new ArrayList<>();
        final Map<Integer, List<String>> peerMembers = new LinkedHashMap<>();
        final Map<Integer, String> peerGroupNames = new LinkedHashMap<>();
        int peopleRead = 0;
        boolean peersDeclined = false;
        int index = 0;
        int queriesRun = 0;
        for (final Planned source : planned.stream().filter(Planned::usable).toList()) {
            final int start = at.get(index)[0];
            final List<Table> own = tables.subList(start, start + at.get(index)[1]);
            own.forEach(table -> notes.addAll(table.notes()));
            final int ownQueries = own.stream().mapToInt(Table::queries).sum();
            queriesRun += ownQueries;
            audit(context, source, own, ownQueries);
            final Map<String, List<ActivityProfiles.Seen>> seen = new LinkedHashMap<>();
            final Set<String> seenCutShort = new LinkedHashSet<>();
            for (int i = 0; i < source.fields().breadth().size(); i++) {
                final Table table = own.get(3 + i);
                seen.put(source.fields().breadth().get(i), ActivityProfiles.seen(table));
                if (table.cutShort()) {
                    seenCutShort.add(source.fields().breadth().get(i));
                }
            }
            if (peers && source.hasEventIds() && !peersDeclined) {
                final Table active = own.getLast();
                final int cap = MAX_PEOPLE - peopleRead;
                if (active.rows().size() > cap && !askedToReadFirst(request, context, source, active.rows().size(),
                        cap, notes)) {
                    peersDeclined = true;
                } else {
                    final PeerGroups.Found found = peerGroups.find(context.session(), workspace, element,
                            source.scope().field(), source.scope().values(), active, Math.max(0, cap),
                            context.stopped());
                    peopleRead += found.read();
                    if (!found.canViewData()) {
                        notes.add("You may not view data, so the peers' " + element + " couldn't be read from "
                                  + "their events: no peers.");
                    } else if (found.groups().isEmpty()) {
                        notes.add(source.source().name() + ": no " + element + " was found in "
                                  + source.scope().values() + "'s Event XML (EventSource/User/UserDetails/" + element
                                  + "): no peers.");
                    } else {
                        peerMembers.put(index, found.members().stream().limit(MAX_PEOPLE).toList());
                        peerGroupNames.put(index, element + " " + String.join(" or ", found.groups().stream()
                                .map(group -> "'" + group + "'")
                                .toList()));
                        if (found.unknown() > 0) {
                            notes.add(source.source().name() + ": the " + element + " of " + found.unknown()
                                      + " active people wasn't read (the cap of " + MAX_PEOPLE + " a call, or no "
                                      + "event readable): the peer group may be missing members.");
                        }
                    }
                }
            } else if (peers && !source.hasEventIds()) {
                notes.add(source.source().name() + " has no StreamId and EventId fields, so the peers' groups "
                          + "can't be read from their events: compared without peers.");
            }
            Map<String, SortedMap<LocalDate, Double>> extra = Map.of();
            if (categories) {
                if (source.fields().breadth().isEmpty()) {
                    notes.add(source.source().name() + ": no breadth field whose values to put in categories.");
                } else {
                    extra = categorised(context, workspace, source, own.get(3 + source.fields().breadth().size()),
                            siteList, corrections, folder, notes);
                }
            }
            data.add(new SourceData(source.source().name(), source.label(), source.fields(),
                    ActivityProfiles.days(own.get(0), own.get(1), source.fields(), zone.zone()),
                    ActivityProfiles.hourOfWeek(own.get(1), zone.zone()), seen, seenCutShort,
                    ActivityProfiles.population(own.get(2)), null, extra));
            index++;
        }
        // The peers' days, in a second round.
        final List<AggregateQuery> peerQueries = new ArrayList<>();
        final List<Integer> peerAt = new ArrayList<>();
        final List<Planned> usable = planned.stream().filter(Planned::usable).toList();
        for (final Map.Entry<Integer, List<String>> members : peerMembers.entrySet()) {
            if (!members.getValue().isEmpty()) {
                final Planned source = usable.get(members.getKey());
                peerQueries.add(ActivityProfiles.peersDaily(source.source(), source.scope().field(),
                        members.getValue(), from, to));
                peerAt.add(members.getKey());
            }
        }
        if (!peerQueries.isEmpty()) {
            context.progress().report("Aggregating the peers' days");
            final List<Table> peerTables = runner.run(peerQueries, runContext);
            for (int i = 0; i < peerTables.size(); i++) {
                final int sourceIndex = peerAt.get(i);
                queriesRun += peerTables.get(i).queries();
                final SourceData old = data.get(sourceIndex);
                data.set(sourceIndex, old.withPeers(new PeerData(peerGroupNames.get(sourceIndex),
                        ActivityProfiles.peersDaily(peerTables.get(i)))));
            }
        }
        final List<Scored> scored = data.stream().map(source -> ActivityScoring.score(spec, source)).toList();
        final CompareResult.Run run = new CompareResult.Run(person, logonIds, periods, unit, zone, planned, scored,
                notes, queriesRun, folder, element);
        final CompareResult.Files files = CompareResult.write(workspace, run);
        final Map<String, Object> structured = CompareResult.structured(run, files);
        return context.session().isChat()
                ? ToolResult.text(CompareResult.markdown(run, files)).withStructuredContent(structured)
                : ToolResult.text(JsonUtil.writeValueAsString(structured, false)).withStructuredContent(structured);
    }

    // --------------------------------------------------------------------------------
    // The sources

    // Every source to read, each with whose events it is filtered to and its fields; those that can't be read kept,
    // with why, for the result to say.
    private List<Planned> plan(final ToolCall request,
                               final ToolCallContext context,
                               final String person,
                               final List<String> logonIds) {
        final List<String> given = NullSafe.list(stringListArg(request, SOURCES_ARG));
        final List<DocRef> docs = new ArrayList<>();
        if (given.isEmpty()) {
            dataSourceAccess.listUsable().stream()
                    .filter(doc -> ElasticIndexDoc.TYPE.equals(doc.getType())
                                   || LuceneIndexDoc.TYPE.equals(doc.getType()))
                    .forEach(docs::add);
        } else {
            for (final String name : given) {
                final List<DocRef> found = dataSourceAccess.find(name);
                if (found.isEmpty()) {
                    throw new WorkspaceException("No index you can use is called '" + name + "': list_data_sources "
                                                 + "gives them.");
                }
                docs.add(found.getFirst());
            }
        }
        if (docs.isEmpty()) {
            throw new WorkspaceException("You can't use any Elastic or Lucene index, so there is nothing to compare.");
        }
        final List<String> personFields = optionalList(stringArg(request, PERSON_FIELD_ARG));
        final List<String> userFields = optionalList(stringArg(request, USER_FIELD_ARG));
        final List<String> sums = NullSafe.list(stringListArg(request, SUM_FIELDS_ARG));
        final List<String> breadth = NullSafe.list(stringListArg(request, BREADTH_FIELDS_ARG));
        final String categoryField = stringArg(request, CATEGORY_FIELD_ARG);
        final List<Planned> planned = new ArrayList<>();
        for (final DocRef doc : docs) {
            final Optional<QueryField> time = dataSourceAccess.timeField(doc);
            final boolean elastic = ElasticIndexDoc.TYPE.equals(doc.getType());
            final Map<String, QueryField> fields = new LinkedHashMap<>();
            EsqlQueryTool.fields(queryServiceProvider.get(), doc).forEach(field -> fields.put(field.getFldName(),
                    field));
            if (time.isEmpty()) {
                planned.add(Planned.leftOut(doc, "it has no time field"));
                continue;
            }
            final Source source = new Source(doc, time.get().getFldName(), elastic);
            final PersonScope.Scope scope = PersonScope.resolve(timelineFields, context.session(), doc, person,
                    logonIds, personFields, userFields, fields::containsKey);
            if (!scope.usable()) {
                planned.add(Planned.leftOut(doc, scope.leftOut()));
                continue;
            }
            final List<String> sumFields = sums.stream().filter(fields::containsKey).toList();
            final List<String> breadthFields = breadth.isEmpty()
                    ? defaultBreadth(context, doc, fields, elastic, scope.field().field())
                    : breadth.stream().filter(fields::containsKey).toList();
            final boolean hasIds = fields.containsKey(EqlSequenceResults.STREAM_ID)
                                   && fields.containsKey(EqlSequenceResults.EVENT_ID);
            planned.add(new Planned(doc, source, new Scope(scope.field().field(), scope.values()),
                    new Fields(sumFields, breadthFields), scope.by().label() + " (" + scope.field().field() + " from "
                                                          + scope.field().from() + ")", hasIds, null,
                    categoryField != null && fields.containsKey(categoryField)
                            ? categoryField
                            : null));
        }
        if (planned.stream().noneMatch(Planned::usable)) {
            throw new WorkspaceException("No source can be compared: " + String.join("; ", planned.stream()
                    .map(p -> p.doc().getName() + ": " + p.leftOut())
                    .toList()) + ".");
        }
        return planned;
    }

    // The person's events by day and value of the first breadth field (and the index's category field, where it
    // has one).
    private static AggregateQuery categoryQuery(final Planned source, final Instant from, final Instant to) {
        final List<String> groups = new ArrayList<>();
        groups.add(source.fields().breadth().getFirst());
        if (source.categoryField() != null) {
            groups.add(source.categoryField());
        }
        return new AggregateQuery(source.source(), source.scope().filter(), from, to, AggregateQuery.Bucket.DAY,
                groups, List.of(new AggregateQuery.Metric(AggregateQuery.Kind.COUNT, null, ActivityProfiles.EVENTS)),
                ActivityProfiles.MAX_ROWS);
    }

    // Each category's events a day: the values' categories from the index's category field, else the model's; the
    // site's list and the corrections over both. The categories are written to a file, said in the notes.
    private Map<String, SortedMap<LocalDate, Double>> categorised(final ToolCallContext context,
                                                                           final SessionWorkspace workspace,
                                                                           final Planned source,
                                                                           final Table table,
                                                                           final Map<String, String> siteList,
                                                                           final Map<String, String> corrections,
                                                                           final String folder,
                                                                           final List<String> notes) {
        final String field = source.fields().breadth().getFirst();
        final SortedMap<LocalDate, Map<String, Double>> byDay = new TreeMap<>();
        final Map<String, Long> counts = new LinkedHashMap<>();
        final Map<String, String> fromField = new LinkedHashMap<>();
        for (final Map<String, String> row : table.rows()) {
            final String value = row.get(AggregateQuery.groupColumn(0));
            final String bucket = row.get(AggregateQuery.BUCKET_COLUMN);
            if (NullSafe.isBlankString(value) || NullSafe.isBlankString(bucket)) {
                continue;
            }
            final double events = ActivityProfiles.number(row.get(ActivityProfiles.EVENTS));
            byDay.computeIfAbsent(LocalDate.parse(bucket), k -> new LinkedHashMap<>()).merge(value, events,
                    Double::sum);
            counts.merge(value, (long) events, Long::sum);
            final String category = row.get(AggregateQuery.groupColumn(1));
            if (NullSafe.isNonBlankString(category)) {
                fromField.putIfAbsent(value, category);
            }
        }
        if (table.cutShort()) {
            notes.add(source.source().name() + ": the events by " + field + " were cut short at "
                      + ActivityProfiles.MAX_ROWS + " rows a month: the categories' counts are approximate.");
        }
        final Map<String, ActivityCategories.Category> categories = new TreeMap<>();
        if (source.categoryField() != null) {
            fromField.forEach((value, category) -> categories.put(value, new ActivityCategories.Category(
                    category, ActivityCategories.Origin.FIELD)));
            counts.keySet().forEach(value -> {
                final String key = value.toLowerCase(Locale.ROOT);
                if (siteList.containsKey(key)) {
                    categories.put(value, new ActivityCategories.Category(siteList.get(key),
                            ActivityCategories.Origin.SITE));
                } else if (corrections.containsKey(key)) {
                    categories.put(value, new ActivityCategories.Category(corrections.get(key),
                            ActivityCategories.Origin.CORRECTED));
                }
            });
            notes.add(source.source().name() + ": categories from its field " + source.categoryField()
                      + (siteList.isEmpty() && corrections.isEmpty()
                              ? ""
                              : ", the site's list and corrections over it") + ".");
        } else {
            final ActivityCategories.Classified classified = ActivityCategories.classify(workspace, field, counts,
                    siteList, corrections, context.sideCalls());
            categories.putAll(classified.categories());
            classified.notes().forEach(note -> notes.add(source.source().name() + ": " + note));
        }
        final List<List<String>> rows = new ArrayList<>();
        categories.forEach((value, category) -> rows.add(List.of(field, value, category.name(),
                category.origin().name().toLowerCase(Locale.ROOT), String.valueOf(counts.getOrDefault(value, 0L)))));
        final WorkspacePath path = WorkspacePath.parse(folder + safe(source.source().name()) + "-" + CATEGORIES_FILE);
        ToolTables.write(workspace, path, List.of("field", "value", "category", "origin", "events"), rows,
                source.doc().getUuid());
        notes.add(source.source().name() + ": each value's category is in " + path.path() + ".");
        final Map<String, SortedMap<LocalDate, Double>> extra = new LinkedHashMap<>();
        ActivityCategories.daily(byDay, categories).forEach((category, daily) ->
                extra.put(ActivityScoring.CATEGORY + category, daily));
        return extra;
    }

    private static Map<String, String> pairs(final List<String> values, final String arg) {
        try {
            return ActivityCategories.pairs(values);
        } catch (final IllegalArgumentException e) {
            throw new WorkspaceException(arg + ": " + e.getMessage() + ": give value=category.");
        }
    }

    // The host, application and object fields the index has (by the call, skill, AGENTS doc or convention), of a
    // type that can be counted distinct.
    private List<String> defaultBreadth(final ToolCallContext context,
                                        final DocRef doc,
                                        final Map<String, QueryField> fields,
                                        final boolean elastic,
                                        final String personField) {
        final List<String> found = new ArrayList<>();
        for (final String what : List.of(InstructionRules.HOST_FIELD, InstructionRules.APPLICATION_FIELD,
                InstructionRules.OBJECT_FIELD)) {
            timelineFields.find(context.session(), doc, what, List.of(), name -> countable(fields.get(name), elastic))
                    .map(TimelineFields.Found::field)
                    .filter(field -> !field.equals(personField) && !found.contains(field))
                    .ifPresent(found::add);
        }
        return found;
    }

    // A field whose values can be counted distinct and grouped by: not analysed text on Elasticsearch, nor a date.
    private static boolean countable(final QueryField field, final boolean elastic) {
        if (field == null) {
            return false;
        }
        final FieldType type = field.getFldType();
        return type != FieldType.DATE && type != FieldType.DENSE_VECTOR && !(elastic && type == FieldType.TEXT);
    }

    private void audit(final ToolCallContext context, final Planned source, final List<Table> tables,
                       final int queries) {
        final String first = tables.stream()
                .map(Table::text)
                .filter(NullSafe::isNonBlankString)
                .findFirst()
                .orElse("");
        final long rows = tables.stream().mapToLong(table -> table.rows().size()).sum();
        auditLog.searched(context.session(), source.doc(), first + (queries > 1
                ? "\n(and " + (queries - 1) + " more aggregates of the same person and period)"
                : ""), rows, "Aggregates of " + source.label() + " by the " + NAME + " tool");
    }

    // Past the cap: asks whether to read the first people's groups; over MCP, or with no form, reads them and says
    // so.
    private boolean askedToReadFirst(final ToolCall request,
                                     final ToolCallContext context,
                                     final Planned source,
                                     final int active,
                                     final int cap,
                                     final List<String> notes) {
        if (!context.session().isChat() || !context.userQuestions().canAsk()) {
            notes.add(source.source().name() + ": " + active + " people were active, more than the " + MAX_PEOPLE
                      + " whose " + "groups are read in a call, and no form could ask: only the first " + cap
                      + " were read, so the peer group may be missing members.");
            return true;
        }
        final Map<String, Object> form = new LinkedHashMap<>();
        form.put("type", "object");
        form.put("properties", Map.of("choice", Map.of("type", "string", "title", "Peers", "enum",
                List.of(READ_FIRST, NO_PEERS), "default", READ_FIRST)));
        form.put("required", List.of("choice"));
        final ElicitResult answer = context.userQuestions().ask(NAME, UserQuestions.formCallId(request.id(),
                PEERS_FORM), active + " people were active in " + source.source().name() + ". Finding the "
                             + "person's peers reads one event of each for their group, at most " + MAX_PEOPLE
                             + " a call.", form);
        final boolean read = answer.action() == ElicitResult.Action.ACCEPT
                             && READ_FIRST.equals(String.valueOf(NullSafe.map(answer.content()).get("choice")));
        notes.add(read
                ? source.source().name() + ": the groups of the first " + cap + " of " + active + " active people "
                  + "were read, as the user chose: the peer group may be missing members."
                : "Compared without peers, as the user chose.");
        return read;
    }

    // --------------------------------------------------------------------------------
    // Arguments

    private Periods periods(final ToolCall request, final ToolTimeZone zone, final long now) {
        final LocalDate windowTo = endDate(stringArg(request, WINDOW_TO_ARG), zone, now, null);
        final LocalDate windowFrom = startDate(stringArg(request, WINDOW_FROM_ARG), zone, now,
                windowTo.minusDays(DEFAULT_WINDOW_DAYS));
        final LocalDate baselineTo = endDate(stringArg(request, BASELINE_TO_ARG), zone, now, windowFrom);
        final LocalDate baselineFrom = startDate(stringArg(request, BASELINE_FROM_ARG), zone, now,
                baselineTo.minusDays(DEFAULT_BASELINE_DAYS));
        if (!windowFrom.isBefore(windowTo)) {
            throw new WorkspaceException("The window must start before it ends (" + windowFrom + " to " + windowTo
                                         + ").");
        }
        if (!baselineFrom.isBefore(baselineTo)) {
            throw new WorkspaceException("The baseline must start before it ends (" + baselineFrom + " to "
                                         + baselineTo + ").");
        }
        if (baselineTo.isAfter(windowFrom)) {
            throw new WorkspaceException("The baseline must end by the window's start (" + baselineTo + " is after "
                                         + windowFrom + ").");
        }
        if (baselineFrom.plusDays(MAX_DAYS).isBefore(windowTo)) {
            throw new WorkspaceException("The baseline and window together may be at most " + MAX_DAYS + " days.");
        }
        return new Periods(baselineFrom, baselineTo, windowFrom, windowTo);
    }

    /// A start: a date as given, or the day a time falls on in the zone.
    ///
    /// @param value  The argument, or null.
    /// @param zone   The person's zone.
    /// @param now    What now is.
    /// @param other  The default.
    /// @return The day.
    static LocalDate startDate(final String value, final ToolTimeZone zone, final long now, final LocalDate other) {
        if (NullSafe.isBlankString(value)) {
            return other;
        }
        return ZonedDateTime.ofInstant(instant(value, zone, now), zone.zone()).toLocalDate();
    }

    /// An exclusive end: a date as given, or the day after the one a time falls in (its own day if at midnight).
    ///
    /// @param value The argument, or null.
    /// @param zone  The person's zone.
    /// @param now   What now is.
    /// @param other The default, or null for the day after today.
    /// @return The day after the last included.
    static LocalDate endDate(final String value, final ToolTimeZone zone, final long now, final LocalDate other) {
        if (NullSafe.isBlankString(value)) {
            return other != null
                    ? other
                    : ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), zone.zone()).toLocalDate().plusDays(1);
        }
        final ZonedDateTime time = ZonedDateTime.ofInstant(instant(value, zone, now), zone.zone());
        return time.toLocalTime().toSecondOfDay() == 0
                ? time.toLocalDate()
                : time.toLocalDate().plusDays(1);
    }

    private static Instant instant(final String value, final ToolTimeZone zone, final long now) {
        final String trimmed = value.strip();
        if (DATE.matcher(trimmed).matches()) {
            return LocalDate.parse(trimmed).atStartOfDay(zone.zone()).toInstant();
        }
        final String time = StroomQlDatePoints.timeInZone(trimmed, zone, now);
        try {
            return OffsetDateTime.parse(time).toInstant();
        } catch (final DateTimeParseException e) {
            try {
                return Instant.parse(time);
            } catch (final DateTimeParseException e2) {
                throw new WorkspaceException("The time '" + value + "' can't be read: give a date (yyyy-MM-dd), an "
                                             + "ISO 8601 time or a date expression such as now()-30d.");
            }
        }
    }

    /// @param value The unit as given, or null.
    /// @return It; a week by default.
    static Unit unit(final String value) {
        if (NullSafe.isBlankString(value)) {
            return Unit.WEEK;
        }
        try {
            return Unit.valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException e) {
            throw new WorkspaceException("unit must be day, week or month, not '" + value + "'.");
        }
    }

    /// @param values The leave as given, each a date or `from..to`.
    /// @return Each range, `{from, to}` with to inclusive.
    static List<LocalDate[]> leave(final List<String> values) {
        final List<LocalDate[]> ranges = new ArrayList<>();
        for (final String value : NullSafe.list(values)) {
            final Matcher matcher = RANGE.matcher(NullSafe.string(value));
            if (!matcher.matches()) {
                throw new WorkspaceException("Leave '" + value + "' can't be read: give yyyy-MM-dd..yyyy-MM-dd or a "
                                             + "date.");
            }
            final LocalDate start = LocalDate.parse(matcher.group(1));
            final LocalDate end = matcher.group(2) == null
                    ? start
                    : LocalDate.parse(matcher.group(2));
            if (end.isBefore(start)) {
                throw new WorkspaceException("Leave '" + value + "' ends before it starts.");
            }
            ranges.add(new LocalDate[]{start, end});
        }
        return ranges;
    }

    /// @param values The approved exceptions as given, each `from..to: reason` (or a date and a reason).
    /// @return Each.
    static List<ActivityScoring.Excepted> exceptions(final List<String> values) {
        final List<ActivityScoring.Excepted> exceptions = new ArrayList<>();
        for (final String value : NullSafe.list(values)) {
            final String text = NullSafe.string(value);
            final int colon = text.indexOf(':');
            final String reason = colon < 0
                    ? ""
                    : text.substring(colon + 1).strip();
            final Matcher matcher = RANGE.matcher(colon < 0
                    ? text
                    : text.substring(0, colon));
            if (!matcher.matches() || reason.isEmpty()) {
                throw new WorkspaceException("Exception '" + value + "' can't be read: give yyyy-MM-dd..yyyy-MM-dd: "
                                             + "the reason.");
            }
            final LocalDate start = LocalDate.parse(matcher.group(1));
            final LocalDate end = matcher.group(2) == null
                    ? start
                    : LocalDate.parse(matcher.group(2));
            exceptions.add(new ActivityScoring.Excepted(start, end, reason));
        }
        return exceptions;
    }

    /// @param value The element as given, or null.
    /// @return It, if it is one peers may be grouped by; Organisation by default.
    static String peerElement(final String value) {
        if (NullSafe.isBlankString(value)) {
            return PeerGroups.DEFAULT_ELEMENT;
        }
        for (final String element : PeerGroups.ELEMENTS) {
            if (element.equalsIgnoreCase(value.strip())) {
                return element;
            }
        }
        throw new WorkspaceException("Peers are grouped only by where people work: " + String.join(", ",
                PeerGroups.ELEMENTS.stream().sorted().toList()) + " (never by a personal characteristic such as "
                                     + "Nationality).");
    }

    private static List<String> optionalList(final String value) {
        return NullSafe.isBlankString(value)
                ? List.of()
                : List.of(value.strip());
    }

    /// @param value A person id or logon ids.
    /// @return It as a workspace folder's name.
    static String safe(final String value) {
        final String safe = UNSAFE.matcher(NullSafe.string(value)).replaceAll("_");
        final String trimmed = safe.replaceAll("^[._-]+", "");
        final String name = trimmed.isEmpty()
                ? "person"
                : trimmed;
        return name.length() > 100
                ? name.substring(0, 100)
                : name;
    }


    // --------------------------------------------------------------------------------


    /// The days compared.
    ///
    /// @param baselineFrom The baseline's first day.
    /// @param baselineTo   The day after its last.
    /// @param windowFrom   The window's first day.
    /// @param windowTo     The day after its last.
    record Periods(LocalDate baselineFrom, LocalDate baselineTo, LocalDate windowFrom, LocalDate windowTo) {

        /// @return The periods in words.
        String describe() {
            return "the window " + windowFrom + " to " + windowTo.minusDays(1) + " against the baseline "
                   + baselineFrom + " to " + baselineTo.minusDays(1);
        }
    }


    // --------------------------------------------------------------------------------


    /// One source planned.
    ///
    /// @param doc         Its index.
    /// @param source      It, for the aggregates; null if left out.
    /// @param scope       Whose events: the field and values; null if left out.
    /// @param fields      The sum and breadth fields; null if left out.
    /// @param label       Whose events, as the result says it.
    /// @param hasEventIds   True if it has StreamId and EventId fields.
    /// @param leftOut       Why it can't be read, or null.
    /// @param categoryField The index's category field, or null.
    record Planned(DocRef doc,
                   Source source,
                   Scope scope,
                   Fields fields,
                   String label,
                   boolean hasEventIds,
                   String leftOut,
                   String categoryField) {

        /// @param doc The index.
        /// @param why Why it can't be read.
        /// @return It, left out.
        static Planned leftOut(final DocRef doc, final String why) {
            return new Planned(doc, null, null, null, null, false, why, null);
        }

        /// @return True if it can be read.
        boolean usable() {
            return leftOut == null;
        }
    }
}
