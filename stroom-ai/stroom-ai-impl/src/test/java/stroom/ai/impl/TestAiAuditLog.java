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

import stroom.ai.impl.tools.ToolCaller;
import stroom.ai.impl.tools.ToolSession;
import stroom.docref.DocRef;
import stroom.event.logging.api.StroomEventLoggingService;

import event.logging.Device;
import event.logging.Event;
import event.logging.EventAction;
import event.logging.EventDetail;
import event.logging.EventSource;
import event.logging.EventTime;
import event.logging.OtherObject;
import event.logging.ProcessAction;
import event.logging.ProcessEventAction;
import event.logging.ProcessType;
import event.logging.SearchEventAction;
import event.logging.SystemDetail;
import event.logging.User;
import event.logging.ViewEventAction;
import event.logging.impl.DefaultEventSerializer;
import event.logging.impl.DefaultXMLValidator;
import event.logging.impl.ExceptionAndLoggingErrorHandler;
import event.logging.impl.SchemaLocator;
import event.logging.impl.ValidationExceptionBehaviourMode;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.math.BigInteger;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class TestAiAuditLog {

    private final StroomEventLoggingService eventLoggingService = Mockito.mock(StroomEventLoggingService.class);
    private final AiAuditLog auditLog = new AiAuditLog(() -> eventLoggingService);

    @Test
    void testToolCall() {
        auditLog.toolCall(ToolSession.chat(7), "stroom__create_feed", "{\"name\":\"X\"}", "Denied by the user", false,
                true);

        final ArgumentCaptor<ProcessEventAction> action = ArgumentCaptor.forClass(ProcessEventAction.class);
        Mockito.verify(eventLoggingService).log(Mockito.eq(AiAuditLog.TOOL_CALL_TYPE_ID), Mockito.contains("chat 7"),
                action.capture());
        assertThat(action.getValue().getAction()).isEqualTo(ProcessAction.CALL);
        assertThat(action.getValue().getType()).isEqualTo(ProcessType.APPLICATION);
        assertThat(action.getValue().getCommand()).isEqualTo("stroom__create_feed");
        assertThat(action.getValue().getArguments()).isEqualTo("{\"name\":\"X\"}");
        assertThat(action.getValue().getOutcome().isSuccess()).isFalse();
        assertThat(action.getValue().getOutcome().getDescription()).isEqualTo("Denied by the user");
        // Events from a run while the user was away say so.
        assertThat(action.getValue().getData())
                .anySatisfy(data -> {
                    assertThat(data.getName()).isEqualTo("Unattended");
                    assertThat(data.getValue()).isEqualTo("true");
                });
    }

    @Test
    void testToolCallInAChatNamesTheChatAsBefore() {
        auditLog.toolCall(ToolSession.chat(7), "t", "{}", "Run", true, false);

        final ArgumentCaptor<ProcessEventAction> action = ArgumentCaptor.forClass(ProcessEventAction.class);
        Mockito.verify(eventLoggingService).log(Mockito.eq(AiAuditLog.TOOL_CALL_TYPE_ID),
                Mockito.eq("Ask Stroom AI called tool t in chat 7"), action.capture());
        assertThat(action.getValue().getData())
                .extracting(data -> data.getName() + "=" + data.getValue())
                .containsExactly("ChatId=7", "Unattended=false");
    }

    @Test
    void testToolCallWithWorkspaceFilesPutInKeepsTheReferenceAndRecordsTheSizeSent() {
        final String arguments = "{\"mapping\":{\"$workspace\":\"/outputs/mapping.json\"}}";

        auditLog.toolCall(ToolSession.chat(7), "t", arguments, 49_152L, "Run", true, false);

        final ArgumentCaptor<ProcessEventAction> action = ArgumentCaptor.forClass(ProcessEventAction.class);
        Mockito.verify(eventLoggingService).log(Mockito.eq(AiAuditLog.TOOL_CALL_TYPE_ID), Mockito.anyString(),
                action.capture());
        assertThat(action.getValue().getArguments()).isEqualTo(arguments);
        assertThat(action.getValue().getData())
                .extracting(data -> data.getName() + "=" + data.getValue())
                .containsExactly("ChatId=7", "Unattended=false", "ExpandedArgumentsChars=49152");
    }

    @Test
    void testToolCallInAnotherSessionNamesTheSessionAndCaller() {
        final ToolSession session = new ToolSession(ToolSession.Kind.MCP, "s1",
                new ToolCaller(ToolCaller.Kind.EXTERNAL_CLIENT, "Claude Desktop"));

        auditLog.toolCall(session, "t", "{}", "Run", true, false);

        final ArgumentCaptor<ProcessEventAction> action = ArgumentCaptor.forClass(ProcessEventAction.class);
        Mockito.verify(eventLoggingService).log(Mockito.eq(AiAuditLog.TOOL_CALL_TYPE_ID),
                Mockito.eq("Ask Stroom AI called tool t in MCP session s1 of Claude Desktop"), action.capture());
        assertThat(action.getValue().getData())
                .extracting(data -> data.getName() + "=" + data.getValue())
                .containsExactly("Session=MCP s1 of Claude Desktop", "Unattended=false");
    }

    @Test
    void testAnswerIsShortened() {
        auditLog.answer(ToolSession.chat(7), "q", "a".repeat(5_000), false);

        final ArgumentCaptor<ProcessEventAction> action = ArgumentCaptor.forClass(ProcessEventAction.class);
        Mockito.verify(eventLoggingService).log(Mockito.eq(AiAuditLog.ANSWER_TYPE_ID), Mockito.anyString(),
                action.capture());
        assertThat(action.getValue().getOutcome().getDescription())
                .startsWith("a".repeat(AiAuditLog.MAX_TEXT))
                .endsWith("(5000 characters)");
    }

    @Test
    void testEventsAreValid() {
        // The schema needs the process's type, which the audit log once left out, so every event was invalid.
        auditLog.toolCall(ToolSession.chat(7), "stroom__create_pipeline", "{\"name\":\"X\"}", "Run", true, false);
        auditLog.answer(ToolSession.chat(7), "q", "a", false);
        auditLog.userAction(ToolSession.chat(7), "enable_processor_filter", "Enable", "Enabled", true);

        final ArgumentCaptor<ProcessEventAction> actions = ArgumentCaptor.forClass(ProcessEventAction.class);
        Mockito.verify(eventLoggingService, Mockito.times(3)).log(Mockito.anyString(), Mockito.anyString(),
                actions.capture());
        final DefaultXMLValidator validator = new DefaultXMLValidator(SchemaLocator.getSchemaLocation(),
                new ExceptionAndLoggingErrorHandler(), ValidationExceptionBehaviourMode.THROW);
        assertThat(actions.getAllValues())
                .hasSize(3)
                .allSatisfy(action -> assertThatCode(() -> validator.validate(
                        new DefaultEventSerializer().serialize(event(action))))
                        .doesNotThrowAnyException());
    }

    @Test
    void testDocumentViewedNamesTheDocument() {
        auditLog.documentViewed(ToolSession.chat(7), new DocRef("Dictionary", "dict-uuid", "Malicious IPs"),
                "Read by the read_document tool");

        final ArgumentCaptor<ViewEventAction> action = ArgumentCaptor.forClass(ViewEventAction.class);
        Mockito.verify(eventLoggingService).log(Mockito.eq(AiAuditLog.DOCUMENT_VIEW_TYPE_ID),
                Mockito.eq("Ask Stroom AI viewed Dictionary 'Malicious IPs' in chat 7"), action.capture());
        assertThat(action.getValue().getObjects())
                .singleElement()
                .isInstanceOfSatisfying(OtherObject.class, object -> {
                    assertThat(object.getType()).isEqualTo("Dictionary");
                    assertThat(object.getId()).isEqualTo("dict-uuid");
                    assertThat(object.getName()).isEqualTo("Malicious IPs");
                });
        assertThat(action.getValue().getOutcome().isSuccess()).isTrue();
        final DefaultXMLValidator validator = new DefaultXMLValidator(SchemaLocator.getSchemaLocation(),
                new ExceptionAndLoggingErrorHandler(), ValidationExceptionBehaviourMode.THROW);
        assertThatCode(() -> validator.validate(new DefaultEventSerializer().serialize(event(action.getValue()))))
                .doesNotThrowAnyException();
    }

    @Test
    void testDocumentsViewedNamesEachDocument() {
        auditLog.documentsViewed(ToolSession.chat(7), List.of(new DocRef("View", "v1", "Windows events"),
                new DocRef("Index", "i1", "Linux events")), "Listed by the list_data_sources tool");

        final ArgumentCaptor<ViewEventAction> action = ArgumentCaptor.forClass(ViewEventAction.class);
        Mockito.verify(eventLoggingService).log(Mockito.eq(AiAuditLog.DOCUMENT_VIEW_TYPE_ID),
                Mockito.eq("Ask Stroom AI viewed 2 document(s) in chat 7"), action.capture());
        assertThat(action.getValue().getObjects()).hasSize(2);
        assertValid(action.getValue());
    }

    @Test
    void testSearchedNamesTheDataSource() {
        auditLog.searched(ToolSession.chat(7), new DocRef("View", "v1", "Windows events"),
                "from \"Windows events\" select User",
                100L, "Snapshot taken by the current_results tool");

        final ArgumentCaptor<SearchEventAction> action = ArgumentCaptor.forClass(SearchEventAction.class);
        Mockito.verify(eventLoggingService).log(Mockito.eq(AiAuditLog.SEARCH_TYPE_ID), Mockito.contains("chat 7"),
                action.capture());
        assertThat(action.getValue().getDataSources().getDataSource()).containsExactly("View 'Windows events'");
        assertThat(action.getValue().getTotalResults()).isEqualTo(BigInteger.valueOf(100));
        assertValid(action.getValue());
    }

    @Test
    void testProcessedNamesTheDocumentAndOutcome() {
        auditLog.processed(ToolSession.chat(7), new DocRef("View", "v1", "Windows events"), "validate_stroomql",
                "from x", "OK",
                true);

        final ArgumentCaptor<ProcessEventAction> action = ArgumentCaptor.forClass(ProcessEventAction.class);
        Mockito.verify(eventLoggingService).log(Mockito.eq(AiAuditLog.PROCESS_TYPE_ID),
                Mockito.eq("Ask Stroom AI ran validate_stroomql in chat 7"), action.capture());
        assertThat(action.getValue().getCommand()).isEqualTo("validate_stroomql");
        assertThat(action.getValue().getOutcome().getDescription()).isEqualTo("OK");
        assertValid(action.getValue());
    }

    private static void assertValid(final EventAction action) {
        final DefaultXMLValidator validator = new DefaultXMLValidator(SchemaLocator.getSchemaLocation(),
                new ExceptionAndLoggingErrorHandler(), ValidationExceptionBehaviourMode.THROW);
        assertThatCode(() -> validator.validate(new DefaultEventSerializer().serialize(event(action))))
                .doesNotThrowAnyException();
    }

    @Test
    void testFailureToLogDoesNotStopTheAgent() {
        Mockito.doThrow(new RuntimeException("down")).when(eventLoggingService)
                .log(Mockito.anyString(), Mockito.anyString(), Mockito.any(ProcessEventAction.class));

        auditLog.toolCall(ToolSession.chat(7), "t", "{}", "Run", true, false);
    }

    // An event as Stroom logs it, with the least the schema needs around the action.
    private static Event event(final EventAction action) {
        return Event.builder()
                .withEventTime(EventTime.builder()
                        .withTimeCreated(new Date())
                        .build())
                .withEventSource(EventSource.builder()
                        .withSystem(SystemDetail.builder()
                                .withName("Stroom")
                                .withEnvironment("Test")
                                .build())
                        .withGenerator("Test")
                        .withDevice(Device.builder()
                                .withHostName("localhost")
                                .build())
                        .withUser(User.builder()
                                .withId("admin")
                                .build())
                        .build())
                .withEventDetail(EventDetail.builder()
                        .withTypeId(AiAuditLog.TOOL_CALL_TYPE_ID)
                        .withDescription("Test")
                        .withEventAction(action)
                        .build())
                .build();
    }

    @Test
    void streamsViewedAreNamedUpToALimit() {
        final List<Long> streams = LongStream.rangeClosed(1, AiAuditLog.MAX_LOGGED_DOCUMENTS + 5).boxed().toList();

        auditLog.streamsViewed(ToolSession.chat(7), streams, "Read 9 event(s) with the fetch_events tool");

        final ArgumentCaptor<ViewEventAction> action = ArgumentCaptor.forClass(ViewEventAction.class);
        Mockito.verify(eventLoggingService).log(Mockito.eq(AiAuditLog.DATA_VIEW_TYPE_ID),
                Mockito.contains((AiAuditLog.MAX_LOGGED_DOCUMENTS + 5) + " stream(s)"), action.capture());
        assertThat(action.getValue().getObjects()).hasSize(AiAuditLog.MAX_LOGGED_DOCUMENTS);
        assertThat(((OtherObject) action.getValue().getObjects().getFirst()).getType())
                .isEqualTo(AiAuditLog.STREAM_TYPE);
        assertThat(action.getValue().getOutcome().getDescription())
                .contains("Read 9 event(s)")
                .contains("the first " + AiAuditLog.MAX_LOGGED_DOCUMENTS + " of");
        assertValid(action.getValue());
    }

    @Test
    void noStreamsViewedLogsNothing() {
        auditLog.streamsViewed(ToolSession.chat(7), List.of(), "Nothing");

        Mockito.verifyNoInteractions(eventLoggingService);
    }

    @Test
    void documentsSearchedNameTheQueryAndWhatWasFound() {
        auditLog.documentsSearched(ToolSession.chat(7), "'X' in documents of type XSLT",
                List.of(new DocRef("XSLT", "a", "A"), new DocRef("XSLT", "b", "B")),
                Map.of("DocumentsRead", "12"), "Text found by the find_in_content tool");

        final ArgumentCaptor<SearchEventAction> action = ArgumentCaptor.forClass(SearchEventAction.class);
        Mockito.verify(eventLoggingService).log(Mockito.eq(AiAuditLog.SEARCH_TYPE_ID), Mockito.contains("chat 7"),
                action.capture());
        assertThat(action.getValue().getQuery().getRaw()).isEqualTo("'X' in documents of type XSLT");
        assertThat(action.getValue().getTotalResults()).hasToString("2");
        assertThat(action.getValue().getResults().getObjects()).hasSize(2);
        assertThat(action.getValue().getData())
                .anySatisfy(data -> {
                    assertThat(data.getName()).isEqualTo("DocumentsRead");
                    assertThat(data.getValue()).isEqualTo("12");
                });
        assertValid(action.getValue());
    }

    @Test
    void streamsSearchedNameTheStreamsFound() {
        auditLog.streamsSearched(ToolSession.chat(7), "feed is X", List.of(11L, 12L), Map.of("StreamStoreReads", "1"),
                "Stream meta found by the stream_meta tool");

        final ArgumentCaptor<SearchEventAction> action = ArgumentCaptor.forClass(SearchEventAction.class);
        Mockito.verify(eventLoggingService).log(Mockito.eq(AiAuditLog.SEARCH_TYPE_ID), Mockito.anyString(),
                action.capture());
        assertThat(action.getValue().getResults().getObjects())
                .map(object -> ((OtherObject) object).getType() + " " + ((OtherObject) object).getId())
                .containsExactly("Stream 11", "Stream 12");
        assertValid(action.getValue());
    }
}
