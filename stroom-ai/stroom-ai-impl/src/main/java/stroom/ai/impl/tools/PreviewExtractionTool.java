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
import stroom.ai.impl.TabSnapshots;
import stroom.ai.impl.TabSnapshots.StoredTabSnapshot;
import stroom.ai.impl.extraction.ExtractionChange;
import stroom.ai.impl.extraction.ExtractionField;
import stroom.ai.impl.extraction.ExtractionPipelines;
import stroom.ai.impl.extraction.ExtractionPreview;
import stroom.ai.impl.extraction.ExtractionPreview.Preview;
import stroom.ai.impl.extraction.ExtractionPreview.Sample;
import stroom.ai.impl.extraction.ExtractionXslt;
import stroom.ai.impl.tools.ToolSchema.ArraySchema;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.impl.workspace.ChatWorkspace;
import stroom.ai.impl.workspace.SessionWorkspace;
import stroom.ai.impl.workspace.TableEngine;
import stroom.ai.impl.workspace.TableEngine.Format;
import stroom.ai.impl.workspace.TableEngine.Query;
import stroom.ai.impl.workspace.TableEngine.Result;
import stroom.ai.impl.workspace.WorkspaceException;
import stroom.ai.impl.workspace.WorkspacePath;
import stroom.ai.shared.AiTabSearch;
import stroom.ai.shared.AiToolCategory;
import stroom.ai.shared.AskStroomAiContext;
import stroom.dashboard.shared.DashboardDoc;
import stroom.docref.DocRef;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.XsltDoc;
import stroom.pipeline.shared.stepping.StepLocation;
import stroom.pipeline.shared.stepping.SteppingResult;
import stroom.pipeline.xslt.XsltStore;
import stroom.query.api.TableSettings;
import stroom.query.api.datasource.FindFieldCriteria;
import stroom.query.api.datasource.QueryField;
import stroom.query.impl.QueryService;
import stroom.security.api.SecurityContext;
import stroom.security.shared.AppPermission;
import stroom.security.shared.DocumentPermission;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/// `preview_extraction` - tries a draft search extraction for a dashboard table on some of its events, before
/// anything is made: the fields to extract (each a name, the XPath of its value in the event, and a type), from
/// which Stroom writes the XSLT (see [ExtractionXslt]), stepped through the table's extraction pipeline (or, if it
/// has none, a pipeline as the Search Extraction template makes, unsaved) with Stroom's stepping, as the user. It
/// returns a table of the values extracted from each event, and what to look at before using it (see
/// [ExtractionPreview]), and writes the XSLT to the workspace. It changes nothing.
///
/// For a table with an extraction pipeline already, the extraction is tried as a change to its XSLT (see
/// [ExtractionChange]): written again from the fields, if Stroom AI wrote it; with the fields added, if it was
/// written by hand.
///
/// The events are the rows of a snapshot of the table with their StreamId and EventId (current_results with
/// withEventIds), so the values the table shows now can be compared with those extracted. Stepping needs the
/// Stepping permission.
class PreviewExtractionTool extends AbstractWorkspaceTool {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(PreviewExtractionTool.class);

    static final String NAME = "preview_extraction";
    static final String TABLE_ARG = "table";
    static final String FIELDS_ARG = "fields";
    static final String LIMIT_ARG = "limit";
    /// Events tried by default.
    static final int DEFAULT_EVENTS = 10;
    /// The most events tried.
    static final int MAX_EVENTS = 50;
    /// Where the XSLT is written.
    static final String XSLT_OUT = "/outputs/extraction.xsl";
    static final String STREAM_ID_COLUMN = "StreamId";
    static final String EVENT_ID_COLUMN = "EventId";
    // The longest one step may take.
    private static final Duration STEP_TIME_LIMIT = Duration.ofSeconds(60);

    private final TabSnapshots tabSnapshots;
    private final DocumentAccess documentAccess;
    private final DataSourceAccess dataSourceAccess;
    private final Provider<QueryService> queryServiceProvider;
    private final Provider<XsltStore> xsltStoreProvider;
    private final Provider<SecurityContext> securityContextProvider;
    private final SteppingWalks steppingWalks;
    private final AiAuditLog auditLog;

    @Inject
    PreviewExtractionTool(final ChatWorkspace workspace,
                          final TabSnapshots tabSnapshots,
                          final DocumentAccess documentAccess,
                          final DataSourceAccess dataSourceAccess,
                          final Provider<QueryService> queryServiceProvider,
                          final Provider<XsltStore> xsltStoreProvider,
                          final Provider<SecurityContext> securityContextProvider,
                          final SteppingWalks steppingWalks,
                          final AiAuditLog auditLog) {
        super(workspace);
        this.tabSnapshots = tabSnapshots;
        this.documentAccess = documentAccess;
        this.dataSourceAccess = dataSourceAccess;
        this.queryServiceProvider = queryServiceProvider;
        this.xsltStoreProvider = xsltStoreProvider;
        this.securityContextProvider = securityContextProvider;
        this.steppingWalks = steppingWalks;
        this.auditLog = auditLog;
    }

    @Override
    public ToolPermissions permissions() {
        return ToolPermissions.builder()
                .requires(AppPermission.STEPPING_PERMISSION)
                .requires(AppPermission.VIEW_DATA_PERMISSION)
                .checksDocuments(DocumentPermission.VIEW)
                .build();
    }

    @Override
    public boolean usesRunSlot() {
        // It steps streams.
        return true;
    }

    @Override
    public boolean readsUntrustedContent() {
        // The values are data from anywhere.
        return true;
    }

    @Override
    public AiToolCategory category() {
        return AiToolCategory.CONTENT;
    }

    @Override
    protected Duration timeLimit(final ToolCallContext context) {
        // Stepping streams can take longer than a workspace step.
        return limits(context).getQueryTimeLimit().getDuration();
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(NAME)
                .description(ToolDescription.builder()
                        .general("Tries a search extraction for a dashboard table on some of its events, before "
                                 + "one is made, to add values from the events (not indexed) to the table: give the "
                                 + "fields to extract, each a name, the XPath of its value from the event element "
                                 + "(unprefixed, e.g. EventSource/User/Id or @Id), and a type (text, number or "
                                 + "date). Stroom writes the XSLT from them; it gives each event's StreamId and "
                                 + "EventId too. Turning extraction on fills every value in the table from it, so "
                                 + "give every field the table's columns use (their XPaths, e.g. from the XSLT that "
                                 + "indexes them); the result says which are missing. For a table with an "
                                 + "extraction pipeline already, it is tried as a change to that pipeline's XSLT: "
                                 + "if Stroom AI wrote it, give every field it is to give (it is written again from "
                                 + "them, so fields can be removed or changed); if it was written by hand, give "
                                 + "only the fields to add. It returns a table of the "
                                 + "values extracted from each event, with what to check (a field the data source "
                                 + "has too, values not found, values of the wrong type, values that differ from "
                                 + "those the table shows now). It changes nothing. XPaths may use only plain string "
                                 + "and number functions.")
                        .chatOnly(" It writes the XSLT to " + XSLT_OUT + ". The events are the rows of a "
                                  + "snapshot of the table with StreamId and EventId: current_results with "
                                  + "withEventIds first (ask the user, as it runs the "
                                  + "search again), and give its path. Show the user the preview table, and agree "
                                  + "the fields with them, before offering to make the extraction.")
                        .build())
                .parameters(ObjectSchema.builder()
                        .addStringProperty(TABLE_ARG, "The dashboard table, by its name as the user sees it.")
                        .addStringProperty(PATH_ARG, ToolDescription.builder()
                                .general("The snapshot of the table's rows with StreamId and EventId")
                                .chatOnly(", from current_results with withEventIds")
                                .general(".")
                                .build())
                        .addProperty(FIELDS_ARG, ArraySchema.builder()
                                .description("The fields to extract, besides StreamId and EventId.")
                                .items(ObjectSchema.builder()
                                        .addStringProperty("name", "The field's name, as columns name it in "
                                                                   + "${...}.")
                                        .addStringProperty("xpath", "The XPath of its value, from the event "
                                                                    + "element, unprefixed; the first value it "
                                                                    + "finds is taken.")
                                        .addEnumProperty("type", ExtractionField.TYPES, "Its type (default text).")
                                        .required("name", "xpath")
                                        .build())
                                .build())
                        .addIntegerProperty(LIMIT_ARG, "How many events to try it on, from the first rows "
                                                       + "(default " + DEFAULT_EVENTS + ", at most " + MAX_EVENTS
                                                       + ").")
                        .required(TABLE_ARG, PATH_ARG, FIELDS_ARG)
                        .build())
                .build();
    }

    @Override
    protected ToolResult run(final ToolCall request, final ToolCallContext context) {
        final List<ExtractionField> fields = ExtractionField.parse(argsMap(request).get(FIELDS_ARG));
        final String tableName = stringArg(request, TABLE_ARG);
        if (tableName == null) {
            throw new WorkspaceException("Give the table, by its name as the user sees it.");
        }
        final Integer limitArg = intArg(request, LIMIT_ARG);
        final int limit = limitArg == null
                ? DEFAULT_EVENTS
                : Math.max(1, Math.min(MAX_EVENTS, limitArg));
        if (!securityContextProvider.get().hasAppPermission(AppPermission.STEPPING_PERMISSION)) {
            return ToolResult.text("You don't have the Stepping permission, so an extraction can't be tried on "
                                   + "events. Ask an administrator for it.");
        }
        if (!securityContextProvider.get().hasAppPermission(AppPermission.VIEW_DATA_PERMISSION)) {
            return ToolResult.text("You don't have permission to view data, so an extraction can't be tried on "
                                   + "events.");
        }

        // The dashboard the user attached, and its table.
        final int chatId = context.session().requireChatId(NAME);
        final StoredTabSnapshot tab = tabSnapshots.forLatestQuestion(chatId).orElse(null);
        final DocRef dashboard = NullSafe.get(tab, StoredTabSnapshot::docRef);
        if (dashboard == null || !DashboardDoc.TYPE.equals(dashboard.getType())) {
            return ToolResult.text(TabSnapshots.notAttached(tab,
                    tabSnapshots.latestOfType(chatId, Set.of(DashboardDoc.TYPE)).orElse(null), "dashboard",
                    "Nothing was tried."));
        }
        if (!documentAccess.canView(dashboard)) {
            return ToolResult.text(DocumentAccess.NO_PERMISSION);
        }
        final AiTabSearch table = CurrentResultsTool.chooseTable(NullSafe.list(tab.searches()), tableName);
        final AskStroomAiContext search = table == null
                ? null
                : table.getLive() != null
                        ? table.getLive()
                        : table.getResults();
        if (search == null || ResultsRequests.tableSettings(search) == null) {
            final List<String> tables = NullSafe.list(tab.searches()).stream()
                    .map(each -> "'" + NullSafe.nonBlankStringElse(each.getName(), each.getComponentId()) + "'")
                    .toList();
            return ToolResult.text("The dashboard has no table '" + tableName + "' whose search is known. "
                                   + (tables.isEmpty()
                    ? "It has no tables."
                    : "Its tables are: " + String.join(", ", tables) + "."));
        }
        final Map<String, String> columns = ResultsRequests.columnExpressions(search);
        final List<String> dataSourceFields = fieldsOf(ResultsRequests.dashboardDataSource(search));

        // The events, from the snapshot.
        final SessionWorkspace workspace = sessionWorkspace(context);
        final WorkspacePath path = pathArg(request, PATH_ARG);
        final List<Sample> samples = samples(TableEngine.run(workspace.localFile(path), Format.forPath(path),
                new Query(null, null, null, null, false, false, null, null)), limit);
        if (samples.isEmpty()) {
            return ToolResult.text("The snapshot has no rows with a StreamId and EventId, so the extraction can't "
                                   + "be tried. Take one with current_results and withEventIds.");
        }

        // The pipeline it is tried in: the table's own extraction pipeline, else one as the template makes.
        final Trial trial = trial(ResultsRequests.tableSettings(search));
        if (trial == null) {
            return ToolResult.text("There is no XSLT you can view to try the extraction with (stepping only takes "
                                   + "an XSLT for an element that has one set), so it can't be tried.");
        }
        if (trial.unreadable() != null) {
            return ToolResult.text(trial.unreadable());
        }
        // A change to an XSLT written by hand that can't be made says why before anything is stepped.
        final ExtractionChange.Changed changed;
        try {
            changed = trial.existingCode() == null
                    ? null
                    : ExtractionChange.change(trial.existingCode(), "", fields);
        } catch (final IllegalArgumentException e) {
            return ToolResult.text(e.getMessage() + " Nothing was tried.");
        }
        final List<Sample> tried = new ArrayList<>();
        final Set<Long> streams = new LinkedHashSet<>();
        String xslt = null;
        String namespace = null;
        try {
            for (final Long streamId : samples.stream().map(Sample::streamId).distinct().toList()) {
                streams.add(streamId);
                try (final SteppingWalks.Walk walk = steppingWalks.open(trial.pipeline(), streamId,
                        Map.of(), STEP_TIME_LIMIT)) {
                    for (final Sample sample : samples.stream().filter(s -> s.streamId() == streamId).toList()) {
                        final StepLocation location = new StepLocation(streamId, 0L, sample.eventId() - 1);
                        if (xslt == null) {
                            // The XPaths are in the events' namespace, which the XSLT declares.
                            final SteppingResult probe = walk.refresh(location,
                                    Map.of(trial.element(), ExtractionXslt.NAMESPACE_PROBE));
                            namespace = NullSafe.string(ExtractionPreview.values(
                                    SteppingWalks.output(probe, trial.element()))
                                    .get(ExtractionXslt.PROBE_NAMESPACE));
                            xslt = changed == null
                                    ? ExtractionXslt.xslt(namespace, fields)
                                    : ExtractionChange.change(trial.existingCode(), namespace, fields).code();
                        }
                        tried.add(step(walk, location, sample, trial.element(), xslt));
                    }
                } catch (final IllegalStateException e) {
                    LOGGER.debug(() -> "Unable to step stream " + streamId, e);
                    samples.stream()
                            .filter(sample -> sample.streamId() == streamId)
                            .filter(sample -> tried.stream().noneMatch(done -> done.streamId() == streamId
                                                                               && done.eventId() == sample.eventId()))
                            .forEach(sample -> tried.add(new Sample(sample.streamId(), sample.eventId(),
                                    sample.columnValues(), null, List.of(NullSafe.nonBlankStringElse(
                                    e.getMessage(), "it couldn't be stepped")))));
                }
            }
        } finally {
            auditLog.streamsViewed(context.session(), streams, "Tried an extraction for table '" + tableName
                                                               + "' on dashboard '" + dashboard.getName()
                                                               + "' with the " + NAME + " tool");
        }
        if (xslt != null) {
            workspace.write(WorkspacePath.parse(XSLT_OUT), xslt, null);
        }
        final Set<String> alsoGiven = new LinkedHashSet<>(NullSafe.getOrElse(changed,
                ExtractionChange.Changed::alsoGiven, Set.of()));
        if (changed != null && changed.namesAsItRuns()) {
            // The fields it names as it runs, as it gave them the events tried.
            tried.stream()
                    .filter(sample -> sample.extracted() != null)
                    .forEach(sample -> alsoGiven.addAll(sample.extracted().keySet()));
            fields.forEach(field -> alsoGiven.remove(field.name()));
        }
        final Preview preview = ExtractionPreview.preview(fields, alsoGiven, tried, columns, dataSourceFields);
        return ToolResult.text(describe(tableName, trial, changed, preview, namespace));
    }

    // Steps one event with the extraction's XSLT.
    private static Sample step(final SteppingWalks.Walk walk,
                               final StepLocation location,
                               final Sample sample,
                               final String element,
                               final String xslt) {
        final SteppingResult result = walk.refresh(location, Map.of(element, xslt));
        final List<String> errors = SteppingWalks.markers(result, null).stream()
                .filter(marker -> element.equals(marker.element()) || SteppingWalks.PIPELINE.equals(marker.element()))
                .map(marker -> NullSafe.string(marker.message()))
                .distinct()
                .toList();
        return new Sample(sample.streamId(), sample.eventId(), sample.columnValues(),
                ExtractionPreview.values(SteppingWalks.output(result, element)), errors);
    }

    // The table's own extraction pipeline, if it extracts with one whose records an XSLT writes; else a pipeline
    // as the Search Extraction template makes, unsaved; null if there is no XSLT to try it with.
    private Trial trial(final TableSettings settings) {
        final DocRef pipeline = settings != null && settings.extractValues()
                ? settings.getExtractionPipeline()
                : null;
        if (pipeline != null) {
            final MergedPipeline merged = MergedPipeline.load(documentAccess, pipeline.getUuid()).orElse(null);
            final String element = NullSafe.get(merged, m -> ExtractionPipelines.outputXslt(m).orElse(null));
            final DocRef xslt = element == null
                    ? null
                    : ExtractionPipelines.xsltOf(merged, element).orElse(null);
            if (xslt != null) {
                final String description = "the table's extraction pipeline '"
                                           + NullSafe.nonBlankStringElse(pipeline.getName(), pipeline.getUuid())
                                           + "'";
                final String unchangeable = ExtractionPipelines.unchangeable(merged, element).orElse(null);
                if (unchangeable != null) {
                    return new Trial(merged.doc(), element, description, null, "The extraction of "
                                                                               + description + " can't be changed "
                                                                               + "this way. " + unchangeable);
                }
                final String code = documentAccess.read(xslt, XsltDoc.class).map(XsltDoc::getData).orElse(null);
                return new Trial(merged.doc(), element, description, code, code != null
                        ? null
                        : "The XSLT of " + description + " ('" + NullSafe.nonBlankStringElse(xslt.getName(),
                        xslt.getUuid()) + "') isn't one you can view, so a change to it can't be tried.");
            }
        }
        final DocRef placeholder = placeholderXslt();
        return placeholder == null
                ? null
                : new Trial(ExtractionPipelines.trial(placeholder), ExtractionPipelines.XSLT_ELEMENT,
                        "a pipeline as the Search Extraction template makes (the table has no extraction pipeline "
                        + "of its own)", null, null);
    }

    // An XSLT the user can view, whose code is replaced by the extraction's as it is stepped: the first by name,
    // checking them in turn until one is found.
    private DocRef placeholderXslt() {
        return NullSafe.list(xsltStoreProvider.get().list()).stream()
                .filter(ref -> XsltDoc.TYPE.equals(ref.getType()))
                .sorted(Comparator.comparing(ref -> NullSafe.string(ref.getName())))
                .filter(documentAccess::canView)
                .findFirst()
                .orElse(null);
    }

    /// Reads the events to try an extraction on from a snapshot of a table: its rows' stream and event ids, each
    /// event once, up to a limit, with the values the table shows for it.
    ///
    /// @param result The snapshot's rows.
    /// @param limit  The most events.
    /// @return The events, in the order of their rows.
    /// @throws WorkspaceException if the snapshot has no StreamId and EventId columns.
    static List<Sample> samples(final Result result, final int limit) {
        final List<String> header = result.columns();
        final int streamColumn = indexOf(header, STREAM_ID_COLUMN);
        final int eventColumn = indexOf(header, EVENT_ID_COLUMN);
        if (streamColumn < 0 || eventColumn < 0) {
            throw new WorkspaceException("The snapshot has no " + STREAM_ID_COLUMN + " and " + EVENT_ID_COLUMN
                                         + " columns: take one with current_results and withEventIds.");
        }
        final Map<List<Long>, Sample> samples = new LinkedHashMap<>();
        for (final List<String> row : result.rows()) {
            if (samples.size() >= limit) {
                break;
            }
            final Long streamId = id(row.get(streamColumn));
            final Long eventId = id(row.get(eventColumn));
            if (streamId == null || eventId == null) {
                continue;
            }
            final Map<String, String> values = new LinkedHashMap<>();
            for (int i = 0; i < header.size() && i < row.size(); i++) {
                if (i != streamColumn && i != eventColumn) {
                    values.put(header.get(i), row.get(i));
                }
            }
            samples.putIfAbsent(List.of(streamId, eventId), new Sample(streamId, eventId, values, null, null));
        }
        return new ArrayList<>(samples.values());
    }

    private static String describe(final String tableName,
                                   final Trial trial,
                                   final ExtractionChange.Changed changed,
                                   final Preview preview,
                                   final String namespace) {
        final StringBuilder sb = new StringBuilder();
        sb.append("Tried the extraction for table '").append(tableName).append("' on ").append(preview.stepped())
                .append(" event(s), in ").append(trial.description()).append(". Nothing was changed.");
        if (changed != null && changed.generated()) {
            sb.append(" Its XSLT was written by Stroom AI, so it was written again from the fields given, which "
                      + "are all it would give.");
        } else if (changed != null) {
            sb.append(" Its XSLT was written by hand, so the fields given were added to it; it gives ")
                    .append(String.join(", ", changed.alsoGiven())).append(" already.");
            if (changed.namesAsItRuns()) {
                sb.append(" It names some fields as it runs, so those it gave the events tried count as given too; "
                          + "a field added that it gives already isn't caught.");
            }
        }
        if (namespace != null) {
            sb.append(" The XSLT Stroom wrote from the fields is in ").append(XSLT_OUT).append("; the events' "
                                                                                          + "namespace is '")
                    .append(namespace).append("'.");
        }
        sb.append("\n\n").append(preview.table());
        if (!preview.problems().isEmpty()) {
            sb.append("\n\nIt can't be used as it is:\n- ").append(String.join("\n- ", preview.problems()));
        }
        if (!preview.notes().isEmpty()) {
            sb.append("\n\nTo check:\n- ").append(String.join("\n- ", preview.notes()));
        }
        if (preview.problems().isEmpty()) {
            sb.append("\n\nShow the user the table and anything to check, and agree the fields with them; then "
                      + "offer to make it with propose_extraction_pipeline, with these fields and the namespace")
                    .append(changed == null
                            ? "."
                            : " (it offers to change the pipeline in place, or to save a new one).");
        }
        return sb.toString();
    }

    // The names of a data source's fields, or null if they can't be read.
    private List<String> fieldsOf(final DocRef dataSource) {
        if (dataSource == null || !dataSourceAccess.canUse(dataSource)) {
            return null;
        }
        try {
            final ResultPage<QueryField> page = queryServiceProvider.get().findFields(new FindFieldCriteria(
                    new PageRequest(0, 10_000), FindFieldCriteria.DEFAULT_SORT_LIST, dataSource, null, null));
            final List<String> names = NullSafe.list(NullSafe.get(page, ResultPage::getValues)).stream()
                    .map(QueryField::getFldName)
                    .filter(Objects::nonNull)
                    .toList();
            return names.isEmpty()
                    ? null
                    : names;
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "Unable to read the fields of " + dataSource, e);
            return null;
        }
    }

    private static int indexOf(final List<String> columns, final String name) {
        for (int i = 0; i < columns.size(); i++) {
            if (columns.get(i).strip().equalsIgnoreCase(name)) {
                return i;
            }
        }
        return -1;
    }

    private static Long id(final String value) {
        try {
            final long id = Long.parseLong(NullSafe.string(value).strip().replace(",", ""));
            return id > 0
                    ? id
                    : null;
        } catch (final NumberFormatException e) {
            return null;
        }
    }


    // --------------------------------------------------------------------------------


    /// The pipeline an extraction is tried in, the element given its XSLT, and how to say which it is.
    ///
    /// @param pipeline     The pipeline.
    /// @param element      The element given the extraction's XSLT.
    /// @param description  Which pipeline it is, in words.
    /// @param existingCode The code of the table's own extraction XSLT, which the extraction changes; null for a
    ///                     table with none (or if it can't be read).
    /// @param unreadable   Why the table's own extraction XSLT can't be read, or null.
    private record Trial(PipelineDoc pipeline,
                         String element,
                         String description,
                         String existingCode,
                         String unreadable) {

    }
}
