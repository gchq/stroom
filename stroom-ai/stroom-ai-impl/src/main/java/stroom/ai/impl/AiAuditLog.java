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

package stroom.ai.impl;

import stroom.ai.impl.tools.ToolSession;
import stroom.ai.shared.DocumentReferenceContext;
import stroom.docref.DocRef;
import stroom.event.logging.api.StroomEventLoggingService;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import event.logging.BaseObject;
import event.logging.Data;
import event.logging.DataSources;
import event.logging.EventAction;
import event.logging.MultiObject;
import event.logging.OtherObject;
import event.logging.Outcome;
import event.logging.ProcessAction;
import event.logging.ProcessEventAction;
import event.logging.ProcessType;
import event.logging.Query;
import event.logging.SearchEventAction;
import event.logging.ViewEventAction;
import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.math.BigInteger;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/// Records what the 'Ask Stroom AI' agent does for a user in the audit log: every tool call (with its arguments,
/// shortened, and whether it was run, refused or failed) and every answer. Events from a run while the user was
/// away say so.
///
/// Each event names the session it was in ([ToolSession]): a chat, by its id, as it always has; another kind of
/// session by its id and caller.
public class AiAuditLog {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AiAuditLog.class);

    static final String TOOL_CALL_TYPE_ID = "AskStroomAi.toolCall";
    static final String ANSWER_TYPE_ID = "AskStroomAi.answer";
    static final String USER_ACTION_TYPE_ID = "AskStroomAi.userAction";
    static final String DOCUMENT_VIEW_TYPE_ID = "AskStroomAi.viewDocument";
    static final String SEARCH_TYPE_ID = "AskStroomAi.search";
    static final String PROCESS_TYPE_ID = "AskStroomAi.process";
    static final String DATA_VIEW_TYPE_ID = "AskStroomAi.viewData";
    static final String PROPERTY_VIEW_TYPE_ID = "AskStroomAi.viewProperties";
    /// The type of the objects named in events for streams.
    static final String STREAM_TYPE = "Stream";
    /// The type of the objects named in events for system properties, as the Properties screen's events name them.
    static final String CONFIG_PROPERTY_TYPE = "ConfigProperty";
    /// The most documents named in one View event.
    static final int MAX_LOGGED_DOCUMENTS = 100;
    static final int MAX_TEXT = 1_000;

    private final Provider<StroomEventLoggingService> eventLoggingServiceProvider;

    /// @param eventLoggingServiceProvider Where events are logged.
    @Inject
    public AiAuditLog(final Provider<StroomEventLoggingService> eventLoggingServiceProvider) {
        this.eventLoggingServiceProvider = eventLoggingServiceProvider;
    }

    /// Records a tool call.
    ///
    /// @param session    The session, e.g. the chat.
    /// @param tool       The tool, as the model named it.
    /// @param arguments  Its arguments.
    /// @param outcome    What happened, e.g. "Run", "Denied by the user".
    /// @param success    False if it failed or was not run.
    /// @param unattended True if the user was away.
    public void toolCall(final ToolSession session,
                         final String tool,
                         final String arguments,
                         final String outcome,
                         final boolean success,
                         final boolean unattended) {
        logToolCall(session, tool, arguments, null, outcome, success, unattended);
    }

    /// Records a tool call whose arguments named workspace files, whose content was put in before it was sent.
    ///
    /// @param session               The session, e.g. the chat.
    /// @param tool                  The tool, as the model named it.
    /// @param arguments             Its arguments, as the model wrote them (naming the files, not with their
    ///                              content).
    /// @param expandedArgumentChars How many characters the arguments sent were, with the files' content, or null
    ///                              if nothing was put in.
    /// @param outcome               What happened, e.g. "Run", "Denied by the user".
    /// @param success               False if it failed or was not run.
    /// @param unattended            True if the user was away.
    public void toolCall(final ToolSession session,
                         final String tool,
                         final String arguments,
                         final Long expandedArgumentChars,
                         final String outcome,
                         final boolean success,
                         final boolean unattended) {
        logToolCall(session, tool, arguments, expandedArgumentChars, outcome, success, unattended);
    }

    private void logToolCall(final ToolSession session,
                             final String tool,
                             final String arguments,
                             final Long expandedArgumentChars,
                             final String outcome,
                             final boolean success,
                             final boolean unattended) {
        final ProcessEventAction.Builder<Void> action = ProcessEventAction.builder()
                .withType(ProcessType.APPLICATION)
                .withAction(ProcessAction.CALL)
                .withCommand(tool)
                .withArguments(shorten(arguments))
                .withOutcome(Outcome.builder()
                        .withSuccess(success)
                        .withDescription(outcome)
                        .build())
                .addData(sessionData(session))
                .addData(data("Unattended", String.valueOf(unattended)));
        if (expandedArgumentChars != null) {
            action.addData(data("ExpandedArgumentsChars", String.valueOf(expandedArgumentChars)));
        }
        log(TOOL_CALL_TYPE_ID, "Ask Stroom AI called tool " + tool + " in " + where(session), action.build());
    }

    /// Records an answer given by the agent.
    ///
    /// @param session    The session, e.g. the chat.
    /// @param question   The question.
    /// @param answer     The answer.
    /// @param unattended True if the user was away when it finished.
    public void answer(final ToolSession session,
                       final String question,
                       final String answer,
                       final boolean unattended) {
        log(ANSWER_TYPE_ID, "Ask Stroom AI answered a question in " + where(session),
                ProcessEventAction.builder()
                        .withType(ProcessType.APPLICATION)
                        .withAction(ProcessAction.EXECUTE)
                        .withCommand("Ask Stroom AI")
                        .withArguments(shorten(question))
                        .withOutcome(Outcome.builder()
                                .withSuccess(true)
                                .withDescription(shorten(answer))
                                .build())
                        .addData(sessionData(session))
                        .addData(data("Unattended", String.valueOf(unattended)))
                        .build());
    }

    /// Records the user clicking a button that an MCP tool's result suggested, e.g. to enable a processor filter.
    ///
    /// @param session The session, e.g. the chat.
    /// @param action  The kind of action, e.g. enable_processor_filter.
    /// @param label   What the button said, as Stroom wrote it.
    /// @param outcome What happened.
    /// @param success False if it was not done.
    public void userAction(final ToolSession session,
                           final String action,
                           final String label,
                           final String outcome,
                           final boolean success) {
        log(USER_ACTION_TYPE_ID, "User did a step suggested in Ask Stroom AI " + where(session) + ": " + label,
                ProcessEventAction.builder()
                        .withType(ProcessType.APPLICATION)
                        .withAction(ProcessAction.EXECUTE)
                        .withCommand(action)
                        .withArguments(shorten(label))
                        .withOutcome(Outcome.builder()
                                .withSuccess(success)
                                .withDescription(shorten(outcome))
                                .build())
                        .addData(sessionData(session))
                        .addData(data("Source", "A suggestion in the chat, from an MCP tool"))
                        .build());
    }

    /// Records that a Stroom document was viewed for the chat: read by one of the agent's document tools, or
    /// attached to the chat by the user. Logged as well as the tool call, as the document's own resource logs a
    /// View when the UI fetches it.
    ///
    /// @param session The session, e.g. the chat.
    /// @param docRef  The document.
    /// @param purpose Why it was viewed, e.g. "Read by the read_document tool".
    public void documentViewed(final ToolSession session, final DocRef docRef, final String purpose) {
        log(DOCUMENT_VIEW_TYPE_ID, "Ask Stroom AI viewed " + docRef.getType() + " '"
                                   + NullSafe.nonBlankStringElse(docRef.getName(), docRef.getUuid())
                                   + "' in " + where(session),
                ViewEventAction.builder()
                        .addObject(OtherObject.builder()
                                .withType(docRef.getType())
                                .withId(docRef.getUuid())
                                .withName(docRef.getName())
                                .addData(sessionData(session))
                                .addData(data("Purpose", purpose))
                                .build())
                        .withOutcome(Outcome.builder()
                                .withSuccess(true)
                                .withDescription(purpose)
                                .build())
                        .build());
    }

    /// Records that several Stroom documents were viewed for the chat, e.g. the data sources a tool listed, as one
    /// View event naming each (up to [#MAX_LOGGED_DOCUMENTS]).
    ///
    /// @param session The session, e.g. the chat.
    /// @param docRefs The documents.
    /// @param purpose Why they were viewed, e.g. "Listed by the list_data_sources tool".
    public void documentsViewed(final ToolSession session, final List<DocRef> docRefs, final String purpose) {
        if (docRefs == null || docRefs.isEmpty()) {
            return;
        }
        final ViewEventAction.Builder<Void> builder = ViewEventAction.builder();
        docRefs.stream()
                .limit(MAX_LOGGED_DOCUMENTS)
                .forEach(docRef -> builder.addObject(OtherObject.builder()
                        .withType(docRef.getType())
                        .withId(docRef.getUuid())
                        .withName(docRef.getName())
                        .build()));
        final String more = docRefs.size() > MAX_LOGGED_DOCUMENTS
                ? " (the first " + MAX_LOGGED_DOCUMENTS + " of " + docRefs.size() + " are named)"
                : "";
        log(DOCUMENT_VIEW_TYPE_ID, "Ask Stroom AI viewed " + docRefs.size() + " document(s) in " + where(session),
                builder.withOutcome(Outcome.builder()
                                .withSuccess(true)
                                .withDescription(purpose + more)
                                .build())
                        .addData(sessionData(session))
                        .addData(data("Purpose", purpose))
                        .build());
    }

    /// Records that the agent read the results of a search for the chat, e.g. a snapshot of the rows a query tab
    /// shows, as a Search event naming the data source.
    ///
    /// @param session    The session, e.g. the chat.
    /// @param dataSource The data source searched, or null if not known.
    /// @param query      The query, e.g. its StroomQL or expression, or null.
    /// @param rows       How many rows were read, or null if not known.
    /// @param purpose    Why, e.g. "Snapshot of the results of Query 'Logons', taken by the current_results tool".
    public void searched(final ToolSession session,
                         final DocRef dataSource,
                         final String query,
                         final Long rows,
                         final String purpose) {
        final SearchEventAction.Builder<Void> builder = SearchEventAction.builder()
                .withDataSources(DataSources.builder()
                        .addDataSource(dataSource == null
                                ? "Unknown"
                                : DocumentReferenceContext.describe(dataSource))
                        .build())
                .withQuery(Query.builder().withRaw(shorten(query)).build())
                .addData(sessionData(session))
                .addData(data("Purpose", purpose));
        if (dataSource != null) {
            builder.addData(data("DataSourceUuid", dataSource.getUuid()));
        }
        if (rows != null) {
            builder.withTotalResults(BigInteger.valueOf(rows));
        }
        log(SEARCH_TYPE_ID, "Ask Stroom AI read search results in " + where(session) + ": " + purpose,
                builder.build());
    }

    /// Records that the agent read streams' data for the chat, e.g. the events a tool fetched, as one View event
    /// naming each stream (up to [#MAX_LOGGED_DOCUMENTS]).
    ///
    /// @param session   The session, e.g. the chat.
    /// @param streamIds The streams read.
    /// @param purpose   What was read and why, e.g. "Read 12 event(s) with the fetch_events tool".
    public void streamsViewed(final ToolSession session, final Collection<Long> streamIds, final String purpose) {
        if (streamIds == null || streamIds.isEmpty()) {
            return;
        }
        final ViewEventAction.Builder<Void> builder = ViewEventAction.builder();
        streamIds.stream()
                .limit(MAX_LOGGED_DOCUMENTS)
                .forEach(streamId -> builder.addObject(streamObject(streamId)));
        log(DATA_VIEW_TYPE_ID, "Ask Stroom AI read the data of " + streamIds.size() + " stream(s) in "
                               + where(session),
                builder.withOutcome(Outcome.builder()
                                .withSuccess(true)
                                .withDescription(purpose + more(streamIds.size()))
                                .build())
                        .addData(sessionData(session))
                        .addData(data("Purpose", purpose))
                        .build());
    }

    /// Records that the agent searched Stroom's documents for the chat, e.g. following their dependencies or
    /// finding text in them, as a Search event naming the documents found (up to [#MAX_LOGGED_DOCUMENTS]).
    ///
    /// @param session The session, e.g. the chat.
    /// @param query   The search, in words, e.g. "dependants of Feed 'X', depth 2".
    /// @param found   The documents found and given to the agent.
    /// @param data    Anything else to record, e.g. how many documents were read, by name.
    /// @param purpose Why, e.g. "Search by the find_in_content tool".
    public void documentsSearched(final ToolSession session,
                                  final String query,
                                  final List<DocRef> found,
                                  final Map<String, String> data,
                                  final String purpose) {
        final List<BaseObject> objects = NullSafe.list(found).stream()
                .limit(MAX_LOGGED_DOCUMENTS)
                .map(docRef -> (BaseObject) OtherObject.builder()
                        .withType(docRef.getType())
                        .withId(docRef.getUuid())
                        .withName(docRef.getName())
                        .build())
                .toList();
        searchedObjects(session, query, objects, NullSafe.list(found).size(), data, purpose);
    }

    /// Records that the agent searched streams' meta for the chat, as a Search event naming the streams found (up
    /// to [#MAX_LOGGED_DOCUMENTS]).
    ///
    /// @param session   The session, e.g. the chat.
    /// @param query     The search, in words, e.g. "feed is X, created from 2026-10-01".
    /// @param streamIds The streams found.
    /// @param data      Anything else to record, e.g. how many streams were read from the stream store, by name.
    /// @param purpose   Why, e.g. "Search by the stream_meta tool".
    public void streamsSearched(final ToolSession session,
                                final String query,
                                final List<Long> streamIds,
                                final Map<String, String> data,
                                final String purpose) {
        final List<BaseObject> objects = NullSafe.list(streamIds).stream()
                .limit(MAX_LOGGED_DOCUMENTS)
                .map(streamId -> (BaseObject) streamObject(streamId))
                .toList();
        searchedObjects(session, query, objects, NullSafe.list(streamIds).size(), data, purpose);
    }

    private void searchedObjects(final ToolSession session,
                                 final String query,
                                 final List<BaseObject> objects,
                                 final int total,
                                 final Map<String, String> data,
                                 final String purpose) {
        final SearchEventAction.Builder<Void> builder = SearchEventAction.builder()
                .withQuery(Query.builder().withRaw(shorten(query)).build())
                .withTotalResults(BigInteger.valueOf(total))
                .addData(sessionData(session))
                .addData(data("Purpose", purpose + more(total)));
        if (!objects.isEmpty()) {
            builder.withResults(MultiObject.builder().addObjects(objects).build());
        }
        NullSafe.map(data).forEach((name, value) -> builder.addData(data(name, value)));
        log(SEARCH_TYPE_ID, "Ask Stroom AI searched in " + where(session) + ": " + purpose, builder.build());
    }

    /// Records that the agent searched or listed Stroom's system properties for the chat, as a Search event naming
    /// the properties found (up to [#MAX_LOGGED_DOCUMENTS]), as the Properties screen logs its list.
    ///
    /// @param session The session, e.g. the chat.
    /// @param query   The search, in words, e.g. "max results under stroom.search".
    /// @param paths   The paths of the properties found and given to the agent.
    /// @param purpose Why, e.g. "Search by the config_properties tool".
    public void propertiesSearched(final ToolSession session,
                                   final String query,
                                   final List<String> paths,
                                   final String purpose) {
        final List<BaseObject> objects = NullSafe.list(paths).stream()
                .limit(MAX_LOGGED_DOCUMENTS)
                .map(path -> (BaseObject) propertyObject(path))
                .toList();
        searchedObjects(session, query, objects, NullSafe.list(paths).size(), Map.of(), purpose);
    }

    /// Records that the agent read Stroom's system properties in full for the chat, as one View event naming each
    /// (up to [#MAX_LOGGED_DOCUMENTS]), as the Properties screen logs opening a property.
    ///
    /// @param session The session, e.g. the chat.
    /// @param paths   The paths of the properties read.
    /// @param purpose Why, e.g. "Read by the config_properties tool".
    public void propertiesViewed(final ToolSession session, final List<String> paths, final String purpose) {
        if (paths == null || paths.isEmpty()) {
            return;
        }
        final ViewEventAction.Builder<Void> builder = ViewEventAction.builder();
        paths.stream()
                .limit(MAX_LOGGED_DOCUMENTS)
                .forEach(path -> builder.addObject(propertyObject(path)));
        log(PROPERTY_VIEW_TYPE_ID, "Ask Stroom AI viewed " + paths.size() + " system propert"
                                   + (paths.size() == 1
                ? "y"
                : "ies") + " in " + where(session),
                builder.withOutcome(Outcome.builder()
                                .withSuccess(true)
                                .withDescription(purpose + more(paths.size()))
                                .build())
                        .addData(sessionData(session))
                        .addData(data("Purpose", purpose))
                        .build());
    }

    /// Records that the agent was refused Stroom's system properties for the chat, e.g. as the user doesn't have
    /// the Manage Properties permission, as a failed Search event.
    ///
    /// @param session The session, e.g. the chat.
    /// @param query   What was asked for, in words.
    /// @param reason  Why it was refused.
    /// @param purpose Why it was asked for, e.g. "Search by the config_properties tool".
    public void propertiesRefused(final ToolSession session,
                                  final String query,
                                  final String reason,
                                  final String purpose) {
        log(SEARCH_TYPE_ID, "Ask Stroom AI was refused system properties in " + where(session) + ": " + purpose,
                SearchEventAction.builder()
                        .withQuery(Query.builder().withRaw(shorten(query)).build())
                        .withOutcome(Outcome.builder()
                                .withSuccess(false)
                                .withPermitted(false)
                                .withDescription(shorten(reason))
                                .build())
                        .addData(sessionData(session))
                        .addData(data("Purpose", purpose))
                        .build());
    }

    private static OtherObject propertyObject(final String path) {
        return OtherObject.builder()
                .withType(CONFIG_PROPERTY_TYPE)
                .withId(path)
                .withName(path)
                .build();
    }

    private static OtherObject streamObject(final long streamId) {
        return OtherObject.builder()
                .withType(STREAM_TYPE)
                .withId(String.valueOf(streamId))
                .build();
    }

    private static String more(final int total) {
        return total > MAX_LOGGED_DOCUMENTS
                ? " (the first " + MAX_LOGGED_DOCUMENTS + " of " + total + " are named)"
                : "";
    }

    /// Records something the agent did with a document for the chat that reads but changes nothing, e.g.
    /// validating a query against a data source, as a Process event.
    ///
    /// @param session The session, e.g. the chat.
    /// @param docRef  The document, e.g. the data source, or null if not known.
    /// @param command What was done, e.g. "validate_stroomql".
    /// @param input   What it was done with, e.g. the query, shortened.
    /// @param outcome What happened.
    /// @param success False if it failed.
    public void processed(final ToolSession session,
                          final DocRef docRef,
                          final String command,
                          final String input,
                          final String outcome,
                          final boolean success) {
        final ProcessEventAction.Builder<Void> builder = ProcessEventAction.builder()
                .withType(ProcessType.APPLICATION)
                .withAction(ProcessAction.EXECUTE)
                .withCommand(command)
                .withArguments(shorten(input))
                .withOutcome(Outcome.builder()
                        .withSuccess(success)
                        .withDescription(shorten(outcome))
                        .build())
                .addData(sessionData(session));
        if (docRef != null) {
            builder.addData(data("DocumentType", docRef.getType()))
                    .addData(data("DocumentUuid", docRef.getUuid()))
                    .addData(data("DocumentName", NullSafe.string(docRef.getName())));
        }
        log(PROCESS_TYPE_ID, "Ask Stroom AI ran " + command + " in " + where(session), builder.build());
    }

    private void log(final String typeId, final String description, final EventAction action) {
        try {
            eventLoggingServiceProvider.get().log(typeId, description, action);
        } catch (final RuntimeException e) {
            // Auditing must not stop the agent working, but must not be lost quietly either.
            LOGGER.error(() -> "Unable to log '" + description + "': " + e.getMessage(), e);
        }
    }

    // Where something was done, for an event's description: "chat 12", as events have always said for a chat.
    private static String where(final ToolSession session) {
        return session.isChat()
                ? "chat " + session.id()
                : session.kind() + " session " + session.id() + " of " + session.caller().name();
    }

    // The session, as an event's data: a chat by its id, as events have always named it.
    private static Data sessionData(final ToolSession session) {
        return session.isChat()
                ? data("ChatId", session.id())
                : data("Session", session.kind() + " " + session.id() + " of " + session.caller().name());
    }

    private static Data data(final String name, final String value) {
        return Data.builder().withName(name).withValue(value).build();
    }

    static String shorten(final String text) {
        final String value = NullSafe.string(text);
        return value.length() > MAX_TEXT
                ? value.substring(0, MAX_TEXT) + "… (" + value.length() + " characters)"
                : value;
    }
}
