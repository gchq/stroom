package stroom.receive.content;

import stroom.pipeline.shared.PipelineDoc;
import stroom.query.api.v2.ExpressionOperator;
import stroom.util.json.JsonUtil;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestContentTemplates {

    @Test
    void testSerde() throws IOException {

        int templateNumber = 0;
        ContentTemplates contentTemplates = new ContentTemplates(List.of(
                new ContentTemplate(
                        true,
                        ++templateNumber,
                        ExpressionOperator.builder().build(),
                        TemplateType.PROCESSOR_FILTER,
                        PipelineDoc.buildDocRef()
                                .name("MyPipe1")
                                .uuid("uuid123")
                                .build(),
                        null,
                        null),
                new ContentTemplate(
                        true,
                        ++templateNumber,
                        ExpressionOperator.builder().build(),
                        TemplateType.INHERIT_PIPELINE,
                        PipelineDoc.buildDocRef()
                                .name("MyPipe2")
                                .uuid("uuid456")
                                .build(),
                        null,
                        null)
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
