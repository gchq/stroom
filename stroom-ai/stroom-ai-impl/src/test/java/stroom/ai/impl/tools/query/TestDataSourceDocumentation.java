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

package stroom.ai.impl.tools.query;

import stroom.ai.impl.tools.documents.DocumentAccess;
import stroom.ai.impl.tools.documents.KeptMapping;
import stroom.ai.impl.tools.query.DataSourceDocumentation.Planned;
import stroom.docref.DocRef;
import stroom.docstore.api.DocDependencyService;
import stroom.docstore.api.DocFinder;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.XsltDoc;
import stroom.pipeline.shared.data.PipelineDataBuilder;
import stroom.pipeline.shared.data.PipelineProperty;
import stroom.pipeline.shared.data.PipelinePropertyValue;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/// The index plans kept with the indexing XSLTs of the pipelines feeding an index, read the reverse way
/// ([DataSourceDocumentation#plannedFrom]): the fields whose source is an event-logging path.
class TestDataSourceDocumentation {

    private static final DocRef PIPELINE = new DocRef(PipelineDoc.TYPE, "pipe-uuid", "Proxy indexing");
    private static final DocRef XSLT = new DocRef(XsltDoc.TYPE, "xslt-uuid", "Proxy indexing XSLT");
    private static final DocRef HIDDEN = new DocRef(PipelineDoc.TYPE, "hidden-uuid", "Hidden indexing");

    private final DocumentAccess documentAccess = Mockito.mock(DocumentAccess.class);
    private final DataSourceDocumentation documentation = new DataSourceDocumentation(documentAccess,
            Mockito.mock(DataSourceAccess.class), () -> Mockito.mock(DocFinder.class),
            () -> Mockito.mock(DocDependencyService.class));

    @Test
    void theFieldsWhoseSourceIsThePathAreFoundInPlanOrder() {
        plan(List.of(
                field("actor.login", "EventSource/User/Id"),
                field("host.name", "EventSource/Device/HostName"),
                field("user.name", "./EventSource/User/Id")));

        assertThat(documentation.plannedFrom(List.of(PIPELINE), "EventSource/User/Id"))
                .extracting(planned -> planned.field().name(), Planned::pipeline)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("actor.login", PIPELINE),
                        org.assertj.core.groups.Tuple.tuple("user.name", PIPELINE));
        assertThat(documentation.plannedFrom(List.of(PIPELINE), "EventSource/User/UserDetails/Id")).isEmpty();
    }

    @Test
    void aWildcardStepMatchesAnyOneElement() {
        plan(List.of(
                field("event.action", "EventDetail/Authenticate/Action"),
                field("deep.action", "EventDetail/Authenticate/User/Action")));

        assertThat(documentation.plannedFrom(List.of(PIPELINE), "EventDetail/*/Action"))
                .extracting(planned -> planned.field().name())
                .containsExactly("event.action");
    }

    @Test
    void aSourceThatIsMoreThanThePathIsNeverGuessedFrom() {
        plan(List.of(
                // A union, a stream id, a predicate, a transform, a constant and an array of objects.
                field("either", "EventSource/User/Id | EventDetail/Authenticate/User/Id"),
                field("stream", "@StreamId"),
                field("first", "EventSource/User[1]/Id"),
                Map.of("name", "outcome", "type", "keyword", "source", "EventSource/User/Id", "transform",
                        "outcome"),
                Map.of("name", "org", "type", "constant", "value", "Example"),
                Map.of("name", "users", "type", "object", "source", "EventSource/User", "items",
                        List.of(field("login", "Id"))),
                field("host.name", "EventSource/Device/HostName")));

        // The plan reads: its plain field is found.
        assertThat(documentation.plannedFrom(List.of(PIPELINE), "EventSource/Device/HostName")).hasSize(1);
        assertThat(documentation.plannedFrom(List.of(PIPELINE), "EventSource/User/Id")).isEmpty();
        assertThat(documentation.plannedFrom(List.of(PIPELINE), "EventSource/User")).isEmpty();
        assertThat(documentation.plannedFrom(List.of(PIPELINE), "")).isEmpty();
    }

    @Test
    void aPlanTheUserCantViewIsNeverRead() {
        plan(List.of(field("actor.login", "EventSource/User/Id")));
        // DocumentAccess reads nothing the user can't view: the hidden pipeline, or the XSLT of a viewable one.
        Mockito.when(documentAccess.read(HIDDEN, PipelineDoc.class)).thenReturn(Optional.empty());

        assertThat(documentation.plannedFrom(List.of(HIDDEN), "EventSource/User/Id")).isEmpty();
        Mockito.when(documentAccess.read(XSLT, XsltDoc.class)).thenReturn(Optional.empty());
        assertThat(documentation.plannedFrom(List.of(PIPELINE), "EventSource/User/Id")).isEmpty();
    }

    @Test
    void aSourceIsThePathStepForStep() {
        assertThat(DataSourceDocumentation.sourceIs("EventSource/User/Id", "EventSource/User/Id")).isTrue();
        assertThat(DataSourceDocumentation.sourceIs(" /EventSource/User/Id ", "EventSource/User/Id")).isTrue();
        assertThat(DataSourceDocumentation.sourceIs("EventDetail/Send/Action", "EventDetail/*/Action")).isTrue();
        assertThat(DataSourceDocumentation.sourceIs("EventDetail/*/Action", "EventDetail/*/Action")).isFalse();
        assertThat(DataSourceDocumentation.sourceIs("EventSource/User/Id", "EventSource/User")).isFalse();
        assertThat(DataSourceDocumentation.sourceIs("string(EventSource/User/Id)", "EventSource/User/Id"))
                .isFalse();
        assertThat(DataSourceDocumentation.sourceIs(null, "EventSource/User/Id")).isFalse();
        assertThat(DataSourceDocumentation.sourceIs("EventSource/User/Id", null)).isFalse();
    }

    // ------------------------------------------------------------------------------------------------

    // The pipeline's indexing XSLT keeps a plan of these fields, as save_xslt keeps it.
    private void plan(final List<Map<String, Object>> fields) {
        Mockito.when(documentAccess.read(PIPELINE, PipelineDoc.class)).thenReturn(Optional.of(PipelineDoc.builder()
                .uuid(PIPELINE.getUuid()).name(PIPELINE.getName())
                .pipelineData(new PipelineDataBuilder()
                        .addProperty(new PipelineProperty("xsltFilter", "xslt", new PipelinePropertyValue(XSLT)))
                        .build())
                .build()));
        final Map<String, Object> plan = Map.of("backend", "elasticsearch", "index_name", "proxy",
                "time_field", "@timestamp", "fields", fields);
        Mockito.when(documentAccess.read(XSLT, XsltDoc.class)).thenReturn(Optional.of(XsltDoc.builder()
                .uuid(XSLT.getUuid()).name(XSLT.getName())
                .description(KeptMapping.withMapping("", "index", plan)).build()));
    }

    private static Map<String, Object> field(final String name, final String source) {
        return Map.of("name", name, "type", "keyword", "source", source);
    }
}
