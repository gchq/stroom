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

import stroom.ai.impl.AiConfig;
import stroom.ai.impl.ErrorTriageConfig;
import stroom.data.shared.StreamTypeNames;
import stroom.docref.DocRef;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.data.PipelineDataBuilder;
import stroom.pipeline.shared.data.PipelineElement;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class TestStreamSummaries {

    private static final String EVENTS = "<Events xmlns=\"event-logging:3\">"
                                          + "<Event><EventDetail><TypeId>logon</TypeId><Authenticate>"
                                          + "<Action>Logon</Action><User><Id>alice</Id></User></Authenticate>"
                                          + "</EventDetail><Data Name=\"host\" Value=\"ws01\"/></Event>"
                                          + "<Event><EventDetail><TypeId>logon</TypeId><Authenticate>"
                                          + "<Action>Logoff</Action></Authenticate></EventDetail></Event>"
                                          + "</Events>";

    @TempDir
    Path tempDir;

    private final StreamWorld world = new StreamWorld();
    private ToolTestFixture fixture;
    private AiConfig aiConfig = new AiConfig();
    private StreamSummaries summaries;

    @BeforeEach
    void setUp() {
        fixture = new ToolTestFixture(tempDir);
        fixture.feedAccess();
        summaries = new StreamSummaries(world.streamReader(), () -> world.store, fixture.documentAccess(),
                new AcceptedErrors(() -> fixture.docFinder, fixture.documentAccess()), () -> aiConfig);
    }

    @Test
    void theErrorChildrenOfARawStreamAreTriagedTheirPipelinesOwnElementsToldApart() {
        final PipelineDoc pipeline = pipeline("p", "Acme-Events", "translationFilter");
        world.addRaw(5, "raw");
        world.add(9, StreamTypeNames.ERROR, 5L, pipeline.getUuid(), Map.of());
        world.bytes.put(9L, ("[2:14 translationFilter] ERROR: Unknown element 'Foo'\n"
                             + "[3:1 schemaFilter] ERROR: The value 'x' of element 'Id' is not valid.\n")
                .getBytes(StandardCharsets.UTF_8));

        final Map<String, Object> result = summaries.summariseErrors(5);

        assertThat(result.get("error_streams")).isEqualTo(List.of(9L));
        assertThat(result.get("verdict")).isEqualTo("blocking");
        final List<Map<String, Object>> groups = groups(result);
        assertThat(groups).extracting(g -> g.get("element"), g -> g.get("own_element"))
                .containsExactlyInAnyOrder(
                        tuple("translationFilter", true),
                        tuple("schemaFilter", false));
    }

    @Test
    void errorStreamsFromAPipelineStroomCantLoadAreStillTriaged() {
        // Production streams: an Error stream Stroom can't read (its volume gone), or written by a pipeline it can
        // no longer load, is said; the rest are still summarised.
        world.addRaw(5, "raw");
        world.add(9, StreamTypeNames.ERROR, 5L, "bad", Map.of());
        world.add(10, StreamTypeNames.ERROR, 5L, "bad", Map.of());
        world.bytes.put(9L, new byte[0]);
        Mockito.doThrow(new IllegalStateException("500 volume gone")).when(world.store).openSource(10L);

        final Map<String, Object> out = summaries.summariseErrors(5);

        assertThat(out.get("error_streams")).isEqualTo(List.of(10L, 9L));
        assertThat(out.get("unread")).isEqualTo(List.of("Error stream 10: 500 volume gone"));
        assertThat(groups(out)).isEmpty();
    }

    @Test
    void anErrorStreamIsTriagedItself() {
        world.add(9, StreamTypeNames.ERROR, null, null, Map.of());
        world.bytes.put(9L, "[1:1 decorationFilter] WARN: No user found for \"a\"\n".getBytes(StandardCharsets.UTF_8));

        final Map<String, Object> result = summaries.summariseErrors(9);

        assertThat(result.get("error_streams")).isEqualTo(List.of(9L));
        assertThat(groups(result).getFirst().get("class")).isEqualTo("benign");
    }

    @Test
    void theEnvironmentsOwnRulesComeFirst() {
        aiConfig = new AiConfig(null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, new ErrorTriageConfig(List.of(
                "{element: decorationFilter, class: blocking, reason: 'Decoration must not fail here'}")), null);
        world.add(9, StreamTypeNames.ERROR, null, null, Map.of());
        world.bytes.put(9L, "[1:1 decorationFilter] WARN: No user found for \"a\"\n".getBytes(StandardCharsets.UTF_8));

        assertThat(groups(summaries.summariseErrors(9)).getFirst().get("reason"))
                .isEqualTo("Decoration must not fail here");
    }

    @Test
    void aStreamWithNoErrorChildrenIsClean() {
        world.addRaw(5, "raw");

        final Map<String, Object> result = summaries.summariseErrors(5);

        assertThat(result.get("error_streams")).isEqualTo(List.of());
        assertThat(result.get("verdict")).isEqualTo("clean");
    }

    @Test
    void eventsAreProfiledByTypeTypeIdActionAndPath() {
        world.addSegmented(8, StreamTypeNames.EVENTS, null, null, List.of(EVENTS));

        final Map<String, Object> profile = summaries.summariseEvents(List.of(8L), 200);

        assertThat(profile.get("events")).isEqualTo(2);
        assertThat(profile.get("event_types")).isEqualTo(Map.of("Authenticate", 2));
        assertThat(profile.get("type_ids")).isEqualTo(Map.of("logon", 2));
        assertThat(profile.get("actions")).isEqualTo(Map.of("Authenticate/Logon", 1, "Authenticate/Logoff", 1));
        assertThat(population(profile))
                .containsEntry("EventDetail/Authenticate/User/Id", 50.0)
                .containsEntry("EventDetail/TypeId", 100.0);
        // Which Data elements each Data path holds, by Name.
        assertThat(profile.get("data_names")).isEqualTo(Map.of("Data", Map.of("host", 50.0)));
        assertThat(profile.get("hint")).isNull();
    }

    @Test
    void aSampledProfileSaysSo() {
        world.addSegmented(8, StreamTypeNames.EVENTS, null, null, List.of(EVENTS, EVENTS));

        final Map<String, Object> profile = summaries.summariseEvents(List.of(8L), 1);

        assertThat((String) profile.get("hint")).contains("raise max_events");
    }

    @Test
    void aRecordThatIsntXmlIsPassedOver() {
        world.addSegmented(8, StreamTypeNames.EVENTS, null, null, List.of("<Events><Event>cut", EVENTS));

        assertThat(summaries.summariseEvents(List.of(8L), 200).get("events")).isEqualTo(2);
    }

    private PipelineDoc pipeline(final String uuid, final String name, final String element) {
        final PipelineDataBuilder data = new PipelineDataBuilder();
        data.addElement(new PipelineElement(element, "XSLTFilter"));
        final PipelineDoc doc = PipelineDoc.builder().uuid(uuid).name(name).pipelineData(data.build()).build();
        fixture.add(new DocRef(PipelineDoc.TYPE, uuid, name), doc, true);
        return doc;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Double> population(final Map<String, Object> profile) {
        return (Map<String, Double>) profile.get("path_population");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> groups(final Map<String, Object> result) {
        return (List<Map<String, Object>>) result.get("groups");
    }
}
