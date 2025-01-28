package stroom.receive.common;

import stroom.meta.api.StandardHeaderArguments;
import stroom.meta.shared.DataFormatNames;
import stroom.pipeline.shared.PipelineDoc;
import stroom.receive.common.ContentTemplates.ContentTemplate;
import stroom.receive.common.ContentTemplates.TemplateType;
import stroom.util.json.JsonUtil;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TestContentTemplates {

    @Test
    void testSerde() throws IOException {

        ContentTemplates contentTemplates = new ContentTemplates(Set.of(
                new ContentTemplate(
                        Map.of(
                                StandardHeaderArguments.FORMAT, DataFormatNames.XML,
                                StandardHeaderArguments.SCHEMA, "event-logging"
                        ),
                        TemplateType.PROCESSOR_FILTER,
                        PipelineDoc.buildDocRef()
                                .name("MyPipe1")
                                .uuid("uuid123")
                                .build()),
                new ContentTemplate(
                        Map.of(
                                StandardHeaderArguments.FORMAT, DataFormatNames.JSON,
                                StandardHeaderArguments.SCHEMA, "event-logging-json"
                        ),
                        TemplateType.PROCESSOR_FILTER,
                        PipelineDoc.buildDocRef()
                                .name("MyPipe2")
                                .uuid("uuid456")
                                .build())
        ));

        doSerdeTest(contentTemplates, ContentTemplates.class);
    }

    private <T> void doSerdeTest(final T entity,
                                 final Class<T> clazz) throws IOException {

        final ObjectMapper mapper = JsonUtil.getMapper();
        assertThat(mapper.canSerialize(entity.getClass()))
                .isTrue();

        final String json = mapper.writeValueAsString(entity);
        System.out.println("\n" + json);

        final T entity2 = mapper.readValue(json, clazz);

        assertThat(entity2)
                .isEqualTo(entity);
    }
}
