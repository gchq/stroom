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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// `compare_activity` - compares one person's activity in a window with their own baseline (and, when asked, with
/// their peers'), from
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
    static final String ACCESS_FIELD_ARG = "accessField";
    static final String COUNTRY_FIELD_ARG = "countryField";
    static final String LOCATION_ARG = "location";
    /// The end of a field holding a country code, as the ECS geo fields are named (`source.geo.country_iso_code`).
    static final String COUNTRY_SUFFIX = ".geo.country_iso_code";
    /// The end of a geo point field, as the ECS geo fields are named (`source.geo.location`).
    static final String POINT_SUFFIX = ".geo.location";
    /// The flat profile's country field (a country code, e.g. where a sign-in came from).
    static final String FLAT_COUNTRY = "Country";
    /// The flat profile's category field (the source's own category of what an event reached).
    static final String FLAT_CATEGORY = "Category";
    /// The end of an ECS-style category field (`url.category`).
    static final String CATEGORY_SUFFIX = ".category";
    /// ECS's category of the event itself (authentication, network...), not of what it reached: never a category
    /// field here.
    static final String EVENT_CATEGORY = "event.category";
    static final String PEER_ELEMENT_ARG = "peerElement";
    static final String INCLUDE_TARGETS_ARG = "includeTargets";
    static final String TARGET_PERSON_FIELD_ARG = "targetPersonField";
    static final String TARGET_USER_FIELD_ARG = "targetUserField";
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
    private static final Pattern RANGE = Pattern.compile("\\s*(\\d{4}-\\d{2}-\\d{2})\\s*(?:(?:\\.\\.|/|\\s+to\\s+|"
                                                         + "\\s+-\\s+)\\s*(\\d{4}-\\d{2}-\\d{2}))?\\s*");
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
                        .general("Compares one person's activity in a window with their own baseline, from "
                                 + "index aggregates only, never the events: volume (events, active hours, sums), "
                                 + "rhythm (first and last of the day, hour of the week), breadth (distinct "
                                 + "values) and novelty (values first seen). Scored in code: robust z-scores, "
                                 + "change points with their date, drifts, values started and stopped; coverage "
                                 + "gaps, leave and source-wide shifts are checked first. Gives at most "
                                 + MAX_FINDINGS + " ranked findings, each an indicator with its evidence and an "
                                 + "alternative explanation (never a conclusion about the person: say so), a line "
                                 + "a month, and CSV files of the daily profile and monthly rollup. Give person "
                                 + "(the person id) or logonIds where there is none. includeTargets scores what was "
                                 + "done to the person apart (a group granted, a reset, MFA changed, failed "
                                 + "logons against them; about 2 more queries a month per source). Categories and "
                                 + "location are on by default where a source has a category or country field "
                                 + "(about 1 more query a month each per source). peers (only when the question "
                                 + "compares them with their team or organisation) also compares them with the "
                                 + "others of their UserDetails Organisation, read from one event each (about 1 "
                                 + "more query a month per source); accessField is asked for too. A date alone is a whole day in timeZone: windowFrom 2026-06-01 "
                                 + "and windowTo 2026-06-30 are all of June.")
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
                        .addStringProperty(WINDOW_FROM_ARG, "The window's first day: a date (yyyy-MM-dd, from "
                                                            + "the start of that day), an ISO 8601 time or "
                                                            + "Stroom's now()-30d (default " + DEFAULT_WINDOW_DAYS
                                                            + " days before windowTo).")
                        .addStringProperty(WINDOW_TO_ARG, "The window's last day: a date (yyyy-MM-dd) is "
                                                          + "included, to the end of that day; a time or "
                                                          + "expression is the exclusive end (default now()).")
                        .addStringProperty(BASELINE_FROM_ARG, "The baseline's first day, likewise (default "
                                                              + DEFAULT_BASELINE_DAYS + " days before "
                                                              + "baselineTo).")
                        .addStringProperty(BASELINE_TO_ARG, "The baseline's last day, as windowTo (default the "
                                                            + "day before windowFrom; windowFrom's own date is "
                                                            + "read as ending there).")
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
                        .addBooleanProperty(PEERS_ARG, "Also compare with peers, the others of their Organisation "
                                + "(default false).")
                        .addBooleanProperty(CATEGORIES_ARG, "Add events a day by category (personal cloud "
                                                            + "storage, webmail, job search...): from the index's "
                                                            + "category field (categoryField, AGENTS 'category:', "
                                                            + "Category or *.category), on by default where it has "
                                                            + "one. true with no such field: the first breadth "
                                                            + "field's values classified by the model (marked "
                                                            + "inferred; model calls). false: off.")
                        .addStringProperty(CATEGORY_FIELD_ARG, "Optional: the index's own category field, e.g. a "
                                                               + "proxy's URL category, where it isn't found.")
                        .addProperty(CATEGORY_LIST_ARG, stringArraySchema("The site's own categories, each "
                                                                          + "value=category (from the AGENTS doc "
                                                                          + "or skill): override the rest."))
                        .addStringProperty(ACCESS_FIELD_ARG, "Optional: a field of the resources people reach "
                                                             + "(shares, repositories, sites, record types): the "
                                                             + "baseline's people and resources are clustered, and "
                                                             + "the window's access outside the person's cluster "
                                                             + "found.")
                        .addBooleanProperty(LOCATION_ARG, "Look for new countries, impossible travel and activity "
                                                          + "abroad on leave, from the index's country field "
                                                          + "(countryField, AGENTS 'country:', "
                                                          + "*.geo.country_iso_code or Country): on by default "
                                                          + "where it has one; false: off.")
                        .addStringProperty(COUNTRY_FIELD_ARG, "Optional: the country field, where it isn't found.")
                        .addProperty(CATEGORY_CORRECTIONS_ARG, stringArraySchema("Corrections of categories "
                                                                                 + "inferred, each "
                                                                                 + "value=category: kept for "
                                                                                 + "re-runs."))
                        .addBooleanProperty(INCLUDE_TARGETS_ARG, "Also score what was done to the person (their "
                                                                 + "target fields), as its own findings group, "
                                                                 + "never in their volume, rhythm, breadth or peers "
                                                                 + "(default false).")
                        .addStringProperty(TARGET_PERSON_FIELD_ARG, "Optional: the field holding the person an "
                                                                    + "event was done to (AGENTS 'target person:', "
                                                                    + "ECS user.target.id).")
                        .addStringProperty(TARGET_USER_FIELD_ARG, "Optional: the field holding the account an "
                                                                  + "event was done to (AGENTS 'target user:', ECS "
                                                                  + "user.target.name).")
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
        final boolean peers = booleanArg(request, PEERS_ARG, false);
        // Categories and location: as asked, else on where the source has the field (said in the notes); the model
        // classifies values only when categories are asked for.
        final Boolean categoriesAsked = optionalBooleanArg(request, CATEGORIES_ARG);
        final Boolean locationAsked = optionalBooleanArg(request, LOCATION_ARG);
        final boolean targets = booleanArg(request, INCLUDE_TARGETS_ARG, false);
        final Map<String, String> siteList = pairs(stringListArg(request, CATEGORY_LIST_ARG), CATEGORY_LIST_ARG);
        final Map<String, String> corrections = pairs(stringListArg(request, CATEGORY_CORRECTIONS_ARG),
                CATEGORY_CORRECTIONS_ARG);

        final List<String> notes = new ArrayList<>(periods.notes());
        if (!peers) {
            notes.add("Peers not compared: peers: true compares them with the others of their " + element + ".");
        }
        final List<Planned> planned = plan(request, context, person, logonIds, targets, categoriesAsked,
                locationAsked);
        planned.stream().filter(Planned::usable).forEach(source -> notes.addAll(source.defaults()));
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
        final List<Map<String, Integer>> offsetsBySource = new ArrayList<>();
        for (final Planned source : planned.stream().filter(Planned::usable).toList()) {
            final int start = queries.size();
            queries.add(ActivityProfiles.daily(source.source(), source.scope(), source.fields(), from, to));
            queries.add(ActivityProfiles.hourly(source.source(), source.scope(), from, to));
            queries.add(ActivityProfiles.population(source.source(), source.scope().field(), from, to));
            for (final String field : source.fields().breadth()) {
                queries.add(ActivityProfiles.seen(source.source(), source.scope(), field, from, to));
            }
            final Map<String, Integer> offsets = new LinkedHashMap<>();
            if (source.categories() && (source.categoryField() != null || !source.fields().breadth().isEmpty())) {
                offsets.put(CATEGORIES_ARG, queries.size() - start);
                queries.add(categoryQuery(source, from, to));
            }
            if (source.accessField() != null) {
                offsets.put(ACCESS_FIELD_ARG, queries.size() - start);
                queries.add(ActivityAccess.graph(source.source(), source.scope().field(), source.accessField(), from,
                        periods.baselineTo().atStartOfDay(zone.zone()).toInstant()));
                queries.add(ActivityAccess.window(source.source(), source.scope(), source.accessField(),
                        periods.windowFrom().atStartOfDay(zone.zone()).toInstant(), to));
            }
            if (source.location() && source.countryField() != null) {
                offsets.put(LOCATION_ARG, queries.size() - start);
                queries.add(ActivityLocation.hourly(source.source(), source.scope(), source.countryField(),
                        source.pointField(), from, to));
            }
            if (source.targeted() != null && source.targeted().usable()) {
                // What was done to the person: by day (and type), the accounts acting on them, and everyone's
                // (Elastic only), kept apart from every feature of their own.
                final Targeted targeted = source.targeted();
                offsets.put(INCLUDE_TARGETS_ARG, queries.size() - start);
                queries.add(ActivityProfiles.targetDaily(source.source(), targeted.scope(), targeted.typeField(),
                        from, to));
                if (targeted.actorField() != null) {
                    offsets.put(INCLUDE_TARGETS_ARG + ":actors", queries.size() - start);
                    queries.add(ActivityProfiles.targetActors(source.source(), targeted.scope(),
                            targeted.actorField(), from, to));
                }
                if (source.source().elastic()) {
                    offsets.put(INCLUDE_TARGETS_ARG + ":population", queries.size() - start);
                    queries.add(ActivityProfiles.targetPopulation(source.source(), targeted.scope().field(),
                            targeted.sameKind()
                                    ? targeted.scope().actorField()
                                    : null, from, to));
                }
            }
            offsetsBySource.add(offsets);
            final boolean peersHere = peers && source.hasEventIds();
            if (peersHere) {
                queries.add(ActivityProfiles.active(source.source(), source.scope().field(), from, to,
                        ActivityProfiles.MAX_ROWS));
            }
            at.add(new int[]{start, queries.size() - start});
        }
        final List<Table> tables = runner.run(queries, runContext);
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
            if (source.categories()) {
                if (source.categoryField() == null && source.fields().breadth().isEmpty()) {
                    notes.add(source.source().name() + ": no breadth field whose values to put in categories.");
                } else {
                    extra = categorised(context, workspace, source,
                            own.get(offsetsBySource.get(index).get(CATEGORIES_ARG)), siteList, corrections, folder,
                            notes);
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
        final List<Scored> scored = new ArrayList<>();
        final List<TargetScoring.Scored> doneToThem = new ArrayList<>();
        for (int i = 0; i < data.size(); i++) {
            final Scored one = ActivityScoring.score(spec, data.get(i));
            final List<ActivityScoring.Finding> more = new ArrayList<>(one.findings());
            final Planned source = usable.get(i);
            final Map<String, Integer> offsets = offsetsBySource.get(i);
            final List<Table> own = tables.subList(at.get(i)[0], at.get(i)[0] + at.get(i)[1]);
            if (offsets.containsKey(ACCESS_FIELD_ARG)) {
                final ActivityAccess.Compared access = ActivityAccess.compare(source.source().name(),
                        source.accessField(), source.scope().values(), own.get(offsets.get(ACCESS_FIELD_ARG)),
                        own.get(offsets.get(ACCESS_FIELD_ARG) + 1), periods.windowFrom() + " to "
                                                                    + periods.windowTo().minusDays(1));
                access.finding().ifPresent(more::add);
                notes.addAll(access.notes());
            }
            if (offsets.containsKey(LOCATION_ARG)) {
                final ActivityLocation.Located located = ActivityLocation.locate(source.source().name(), spec,
                        own.get(offsets.get(LOCATION_ARG)), source.pointField() != null);
                more.addAll(located.findings());
                notes.addAll(located.notes());
            } else if (Boolean.TRUE.equals(locationAsked)) {
                notes.add(source.source().name() + ": location isn't available: it has no country field "
                          + "(countryField, or a ## Fields 'country:' line, names one).");
            }
            more.sort(Comparator.comparingDouble(ActivityScoring.Finding::score).reversed());
            scored.add(new Scored(one.source(), more, one.status(), one.features(), one.months(), one.notes(),
                    one.values()));
            if (offsets.containsKey(INCLUDE_TARGETS_ARG)) {
                final Table daily = own.get(offsets.get(INCLUDE_TARGETS_ARG));
                final Integer actorsAt = offsets.get(INCLUDE_TARGETS_ARG + ":actors");
                final Integer populationAt = offsets.get(INCLUDE_TARGETS_ARG + ":population");
                if (daily.cutShort()) {
                    notes.add(source.source().name() + ": what was done to them was cut short at "
                              + ActivityProfiles.MAX_ROWS + " rows a month: approximate.");
                }
                doneToThem.add(TargetScoring.score(spec, new TargetScoring.TargetData(source.source().name(),
                        source.targeted().label(), ActivityProfiles.targetDays(daily,
                        source.targeted().typeField() != null), actorsAt == null
                        ? null
                        : ActivityProfiles.seen(own.get(actorsAt)), populationAt == null
                        ? new TreeMap<>()
                        : ActivityProfiles.population(own.get(populationAt)), data.get(i).population())));
            }
        }
        if (targets) {
            planned.stream()
                    .filter(p -> p.usable() && p.targeted() != null && !p.targeted().usable())
                    .forEach(p -> notes.add(p.doc().getName() + ": what was done to them isn't read: "
                                            + p.targeted().leftOut() + "."));
        }
        final CompareResult.Run run = new CompareResult.Run(person, logonIds, periods, unit, zone, planned, scored,
                notes, queriesRun, folder, element, targets
                        ? doneToThem
                        : null);
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
                               final List<String> logonIds,
                               final boolean targets,
                               final Boolean categoriesAsked,
                               final Boolean locationAsked) {
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
        final List<String> categoryFields = optionalList(stringArg(request, CATEGORY_FIELD_ARG));
        final String accessField = stringArg(request, ACCESS_FIELD_ARG);
        final List<String> countryFields = optionalList(stringArg(request, COUNTRY_FIELD_ARG));
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
            final Targeted targeted = targets
                    ? targeted(request, context, doc, person, logonIds, scope, fields, userFields, personFields)
                    : null;
            final Optional<TimelineFields.Found> category = categoryField(context, doc, categoryFields, fields,
                    elastic);
            final Optional<TimelineFields.Found> country = countryField(context, doc, countryFields, fields);
            final List<String> defaults = new ArrayList<>();
            final boolean categories = onByDefault(categoriesAsked, category, doc.getName(), "categories",
                    "category", CATEGORIES_ARG, defaults);
            final boolean location = onByDefault(locationAsked, country, doc.getName(), "location", "country",
                    LOCATION_ARG, defaults);
            planned.add(new Planned(doc, source, new Scope(scope.field().field(), scope.values()),
                    new Fields(sumFields, breadthFields), scope.by().label() + " (" + scope.field().field() + " from "
                                                          + scope.field().from() + ")", hasIds, null,
                    category.map(TimelineFields.Found::field).orElse(null),
                    accessField != null && fields.containsKey(accessField)
                            ? accessField
                            : null, country.map(TimelineFields.Found::field).orElse(null), elastic
                            ? fields.keySet().stream().filter(name -> name.endsWith(POINT_SUFFIX)).sorted()
                                    .findFirst().orElse(null)
                            : null, targeted, categories, location, defaults));
        }
        if (planned.stream().noneMatch(Planned::usable)) {
            throw new WorkspaceException("No source can be compared: " + String.join("; ", planned.stream()
                    .map(p -> p.doc().getName() + ": " + p.leftOut())
                    .toList()) + ".");
        }
        return planned;
    }

    // How what was done to the person is read in one index: its target field (the call's, the AGENTS doc's ## Fields
    // 'target person:' or 'target user:', the convention's), their own acts on themselves left out by the actor's
    // field; the type by the Action, element or event code field; the actor's logon (else person) field for the
    // accounts acting on them.
    private Targeted targeted(final ToolCall request,
                              final ToolCallContext context,
                              final DocRef doc,
                              final String person,
                              final List<String> logonIds,
                              final PersonScope.Scope scope,
                              final Map<String, QueryField> fields,
                              final List<String> userFields,
                              final List<String> personFields) {
        final PersonScope.Target target = PersonScope.target(timelineFields, context.session(), doc, person,
                logonIds, optionalList(stringArg(request, TARGET_PERSON_FIELD_ARG)),
                optionalList(stringArg(request, TARGET_USER_FIELD_ARG)), scope, fields::containsKey);
        if (!target.usable()) {
            return new Targeted(null, null, null, false, null, target.leftOut());
        }
        String type = null;
        for (final String what : List.of(InstructionRules.ACTION_FIELD, InstructionRules.ELEMENT_FIELD,
                InstructionRules.EVENT_TYPE_FIELD)) {
            if (type == null) {
                type = timelineFields.find(context.session(), doc, what, List.of(), fields::containsKey)
                        .map(TimelineFields.Found::field)
                        .orElse(null);
            }
        }
        final String actor = timelineFields.find(context.session(), doc, InstructionRules.USER_FIELD, userFields,
                        fields::containsKey)
                .or(() -> timelineFields.find(context.session(), doc, InstructionRules.PERSON_FIELD, personFields,
                        fields::containsKey))
                .map(TimelineFields.Found::field)
                .orElse(null);
        return new Targeted(new ActivityProfiles.TargetScope(target.field().field(), target.values(),
                target.actorField(), target.actorValues()), type, actor, target.by() == scope.by(),
                target.describe(), null);
    }

    // The person's events by day and value of the first breadth field (and the index's category field, where it
    // has one; by that alone where there is no breadth field).
    private static AggregateQuery categoryQuery(final Planned source, final Instant from, final Instant to) {
        final List<String> groups = new ArrayList<>();
        if (!source.fields().breadth().isEmpty()) {
            groups.add(source.fields().breadth().getFirst());
        }
        if (source.categoryField() != null) {
            groups.add(source.categoryField());
        }
        return new AggregateQuery(source.source(), source.scope().filter(), from, to, AggregateQuery.Bucket.DAY,
                groups, List.of(new AggregateQuery.Metric(AggregateQuery.Kind.COUNT, null, ActivityProfiles.EVENTS)),
                ActivityProfiles.MAX_ROWS);
    }

    // Each category's events a day: from the index's category field, else the values' categories by the model; the
    // site's list and the corrections over both. The categories are written to a file, said in the notes.
    private Map<String, SortedMap<LocalDate, Double>> categorised(final ToolCallContext context,
                                                                           final SessionWorkspace workspace,
                                                                           final Planned source,
                                                                           final Table table,
                                                                           final Map<String, String> siteList,
                                                                           final Map<String, String> corrections,
                                                                           final String folder,
                                                                           final List<String> notes) {
        if (table.cutShort()) {
            notes.add(source.source().name() + ": the events by " + (source.fields().breadth().isEmpty()
                    ? source.categoryField()
                    : source.fields().breadth().getFirst()) + " were cut short at " + ActivityProfiles.MAX_ROWS
                      + " rows a month: the categories' counts are approximate.");
        }
        final List<List<String>> rows = new ArrayList<>();
        final Map<String, SortedMap<LocalDate, Double>> daily = source.categoryField() != null
                ? fromField(source, table, siteList, corrections, rows, notes)
                : classified(context, workspace, source, table, siteList, corrections, rows, notes);
        final WorkspacePath path = WorkspacePath.parse(folder + safe(source.source().name()) + "-" + CATEGORIES_FILE);
        ToolTables.write(workspace, path, List.of("field", "value", "category", "origin", "events"), rows,
                source.doc().getUuid());
        notes.add(source.source().name() + ": each value's category is in " + path.path() + ".");
        final Map<String, SortedMap<LocalDate, Double>> extra = new LinkedHashMap<>();
        daily.forEach((category, days) -> extra.put(ActivityScoring.CATEGORY + category, days));
        return extra;
    }

    // Each category's events a day from the index's category field, row by row (a value may be of more than one
    // category), the site's list and the corrections of a value over its field's category; the categories file's
    // rows added, a value and category a row.
    private static Map<String, SortedMap<LocalDate, Double>> fromField(final Planned source,
                                                                       final Table table,
                                                                       final Map<String, String> siteList,
                                                                       final Map<String, String> corrections,
                                                                       final List<List<String>> rows,
                                                                       final List<String> notes) {
        final boolean byValue = !source.fields().breadth().isEmpty();
        final String field = byValue
                ? source.fields().breadth().getFirst()
                : source.categoryField();
        final Map<String, SortedMap<LocalDate, Double>> daily = new TreeMap<>();
        final Map<List<String>, Long> counts = new TreeMap<>(Comparator.comparing((List<String> key) -> key.get(0))
                .thenComparing(key -> key.get(1)).thenComparing(key -> key.get(2)));
        for (final Map<String, String> row : table.rows()) {
            final String bucket = row.get(AggregateQuery.BUCKET_COLUMN);
            final String value = row.get(AggregateQuery.groupColumn(0));
            final String fieldCategory = byValue
                    ? row.get(AggregateQuery.groupColumn(1))
                    : value;
            if (NullSafe.isBlankString(bucket)) {
                continue;
            }
            // A row with no value but a category (an event without the breadth field) still counts in it.
            final String key = NullSafe.string(value).toLowerCase(Locale.ROOT);
            final ActivityCategories.Category category;
            if (byValue && siteList.containsKey(key)) {
                category = new ActivityCategories.Category(siteList.get(key), ActivityCategories.Origin.SITE);
            } else if (byValue && corrections.containsKey(key)) {
                category = new ActivityCategories.Category(corrections.get(key), ActivityCategories.Origin.CORRECTED);
            } else if (NullSafe.isNonBlankString(fieldCategory)) {
                category = new ActivityCategories.Category(fieldCategory, ActivityCategories.Origin.FIELD);
            } else {
                continue;
            }
            final double events = ActivityProfiles.number(row.get(ActivityProfiles.EVENTS));
            counts.merge(List.of(NullSafe.string(value), category.name(),
                            category.origin().name().toLowerCase(Locale.ROOT)),
                    (long) events, Long::sum);
            if (!ActivityCategories.OTHER.equals(category.name())) {
                daily.computeIfAbsent(category.name(), k -> new TreeMap<>()).merge(LocalDate.parse(bucket), events,
                        Double::sum);
            }
        }
        counts.forEach((key, events) -> rows.add(List.of(field, key.get(0), key.get(1), key.get(2),
                String.valueOf(events))));
        notes.add(source.source().name() + ": categories from its field " + source.categoryField()
                  + (byValue && !(siteList.isEmpty() && corrections.isEmpty())
                ? ", the site's list and corrections over it"
                : "") + ".");
        return daily;
    }

    // Each category's events a day, the first breadth field's values classified by the model (the site's list and
    // corrections over it); the categories file's rows added, a value a row.
    private static Map<String, SortedMap<LocalDate, Double>> classified(final ToolCallContext context,
                                                                        final SessionWorkspace workspace,
                                                                        final Planned source,
                                                                        final Table table,
                                                                        final Map<String, String> siteList,
                                                                        final Map<String, String> corrections,
                                                                        final List<List<String>> rows,
                                                                        final List<String> notes) {
        final String field = source.fields().breadth().getFirst();
        final SortedMap<LocalDate, Map<String, Double>> byDay = new TreeMap<>();
        final Map<String, Long> counts = new LinkedHashMap<>();
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
        }
        final ActivityCategories.Classified classified = ActivityCategories.classify(workspace, field, counts,
                siteList, corrections, context.sideCalls());
        final Map<String, ActivityCategories.Category> categories = new TreeMap<>(classified.categories());
        classified.notes().forEach(note -> notes.add(source.source().name() + ": " + note));
        categories.forEach((value, category) -> rows.add(List.of(field, value, category.name(),
                category.origin().name().toLowerCase(Locale.ROOT), String.valueOf(counts.getOrDefault(value, 0L)))));
        return ActivityCategories.daily(byDay, categories);
    }

    // The category field: the call's, the AGENTS doc's ## Fields 'category:', else the flat profile's Category or
    // an ECS-style *.category field (not ECS's event.category, the event's own kind), of a type that can be grouped
    // by.
    private Optional<TimelineFields.Found> categoryField(final ToolCallContext context,
                                                         final DocRef doc,
                                                         final List<String> given,
                                                         final Map<String, QueryField> fields,
                                                         final boolean elastic) {
        final Predicate<String> usable = name -> countable(fields.get(name), elastic);
        return timelineFields.find(context.session(), doc, InstructionRules.CATEGORY_FIELD, given, usable)
                .or(() -> fields.keySet().stream()
                        .filter(usable)
                        .filter(name -> name.equals(FLAT_CATEGORY)
                                        || (name.endsWith(CATEGORY_SUFFIX) && !name.equals(EVENT_CATEGORY)))
                        .min(Comparator.comparing((String name) -> !name.equals(FLAT_CATEGORY))
                                .thenComparing(name -> name))
                        .map(name -> new TimelineFields.Found(name, TimelineFields.Origin.CONVENTION,
                                name.equals(FLAT_CATEGORY)
                                        ? "flat"
                                        : "*" + CATEGORY_SUFFIX)));
    }

    // The country field: the call's, the AGENTS doc's ## Fields 'country:', else a *.geo.country_iso_code field
    // (source.* first, as sign-ins record where they came from there), else the flat profile's Country.
    private Optional<TimelineFields.Found> countryField(final ToolCallContext context,
                                                        final DocRef doc,
                                                        final List<String> given,
                                                        final Map<String, QueryField> fields) {
        return timelineFields.find(context.session(), doc, InstructionRules.COUNTRY_FIELD, given,
                        fields::containsKey)
                .or(() -> fields.keySet().stream()
                        .filter(name -> name.endsWith(COUNTRY_SUFFIX))
                        .min(Comparator.comparing((String name) -> !name.startsWith("source."))
                                .thenComparing(name -> name))
                        .map(name -> new TimelineFields.Found(name, TimelineFields.Origin.CONVENTION,
                                "ECS *" + COUNTRY_SUFFIX)))
                .or(() -> fields.containsKey(FLAT_COUNTRY)
                        ? Optional.of(new TimelineFields.Found(FLAT_COUNTRY, TimelineFields.Origin.CONVENTION,
                        "flat"))
                        : Optional.empty());
    }

    /// Whether a feature that needs a field (categories, location) is on for a source: as asked, else on where the
    /// source has the field. What was on by default, and why, is added to the notes.
    ///
    /// @param asked    The call's true or false, or null where it wasn't given.
    /// @param field    The source's field for it, if it has one.
    /// @param source   The source's name.
    /// @param feature  The feature, as the notes say it (`categories`).
    /// @param what     What the field holds, as the notes say it (`category`).
    /// @param arg      The argument that turns it off.
    /// @param defaults The notes of what was on by default, added to.
    /// @return True if it is on.
    static boolean onByDefault(final Boolean asked,
                               final Optional<TimelineFields.Found> field,
                               final String source,
                               final String feature,
                               final String what,
                               final String arg,
                               final List<String> defaults) {
        if (asked != null) {
            return asked;
        }
        field.ifPresent(found -> defaults.add(source + ": " + feature + " on by default, as it has a " + what
                                              + " field (" + found.field() + " from " + found.from() + "); "
                                              + arg + ": false turns it off."));
        return field.isPresent();
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
        final String baselineToArg = stringArg(request, BASELINE_TO_ARG);
        LocalDate baselineTo = endDate(baselineToArg, zone, now, windowFrom);
        final List<String> notes = new ArrayList<>();
        if (isDate(baselineToArg) && baselineTo.equals(windowFrom.plusDays(1))) {
            // The window's own first day given as the baseline's end: read as the end the window starts at.
            baselineTo = windowFrom;
            notes.add("baselineTo " + baselineToArg.strip() + " is the window's first day, so the baseline ends "
                      + "the day before.");
        }
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
        return new Periods(baselineFrom, baselineTo, windowFrom, windowTo, notes);
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

    /// An exclusive end: the day after a date given alone (the date is included, to its end), or the day after the
    /// one a time falls in (its own day if at midnight: a time is the exclusive end).
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
        if (isDate(value)) {
            return LocalDate.parse(value.strip()).plusDays(1);
        }
        final ZonedDateTime time = ZonedDateTime.ofInstant(instant(value, zone, now), zone.zone());
        return time.toLocalTime().toSecondOfDay() == 0
                ? time.toLocalDate()
                : time.toLocalDate().plusDays(1);
    }

    // True for a date given alone (yyyy-MM-dd).
    private static boolean isDate(final String value) {
        return value != null && DATE.matcher(value.strip()).matches();
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

    // A true/false argument, or null where it wasn't given.
    private Boolean optionalBooleanArg(final ToolCall request, final String name) {
        return NullSafe.isBlankString(stringArg(request, name))
                ? null
                : booleanArg(request, name, false);
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
    /// @param notes        What the result says of how the periods were read.
    record Periods(LocalDate baselineFrom,
                   LocalDate baselineTo,
                   LocalDate windowFrom,
                   LocalDate windowTo,
                   List<String> notes) {

        /// Copies the list.
        Periods {
            notes = NullSafe.list(notes).stream().toList();
        }

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
    /// @param accessField   The field of the resources people reach, or null.
    /// @param countryField  The country field, or null.
    /// @param pointField    The geo point field (Elastic only), or null.
    /// @param targeted      What was done to the person, how it is read (`includeTargets`); null if not asked.
    /// @param categories    True if its events are counted by category.
    /// @param location      True if its countries are looked at.
    /// @param defaults      What was on by default, and why, as the notes say it.
    record Planned(DocRef doc,
                   Source source,
                   Scope scope,
                   Fields fields,
                   String label,
                   boolean hasEventIds,
                   String leftOut,
                   String categoryField,
                   String accessField,
                   String countryField,
                   String pointField,
                   Targeted targeted,
                   boolean categories,
                   boolean location,
                   List<String> defaults) {

        /// Copies the list.
        Planned {
            defaults = NullSafe.list(defaults).stream().toList();
        }

        /// @param doc The index.
        /// @param why Why it can't be read.
        /// @return It, left out.
        static Planned leftOut(final DocRef doc, final String why) {
            return new Planned(doc, null, null, null, null, false, why, null, null, null, null, null, false, false,
                    List.of());
        }

        /// @return True if it can be read.
        boolean usable() {
            return leftOut == null;
        }
    }


    // --------------------------------------------------------------------------------


    /// How what was done to the person is read in one index.
    ///
    /// @param scope     The target field and values, the actor's own acts left out; null if it can't be read.
    /// @param typeField The field holding the type (the Action, else the element or event code), or null.
    /// @param actorField The field holding who acted (their logon id, else person id), or null.
    /// @param sameKind  True if the target and actor fields hold the same kind of id (both person ids or both
    ///                  logon ids), so everyone's own acts on themselves can be left out of the source-wide check.
    /// @param label     How the target field was found, as the result says it.
    /// @param leftOut   Why it can't be read, or null.
    record Targeted(ActivityProfiles.TargetScope scope,
                    String typeField,
                    String actorField,
                    boolean sameKind,
                    String label,
                    String leftOut) {

        /// @return True if it can be read.
        boolean usable() {
            return leftOut == null;
        }
    }
}
