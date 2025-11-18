package stroom.receive.content;

import stroom.pipeline.shared.PipelineDoc;
import stroom.processor.shared.ProcessorFilter;
import stroom.query.api.ExpressionOperator;
import stroom.receive.content.shared.ContentTemplate;
import stroom.receive.content.shared.ContentTemplates;
import stroom.receive.content.shared.TemplateType;
import stroom.util.json.JsonUtil;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TestContentTemplates {

    @Test
    void testSerde() throws IOException {

        int templateNumber = 0;
        final ContentTemplates contentTemplates = ContentTemplates
                .builder()
                .uuid(UUID.randomUUID().toString())
                .contentTemplates(List.of(
                        new ContentTemplate(
                                true,
                                ++templateNumber,
                                ExpressionOperator.builder().build(),
                                TemplateType.PROCESSOR_FILTER,
                                false,
                                PipelineDoc.buildDocRef()
                                        .name("MyPipe1")
                                        .uuid("uuid123")
                                        .build(),
                                null,
                                null,
                                ProcessorFilter.DEFAULT_PRIORITY,
                                ProcessorFilter.DEFAULT_MAX_PROCESSING_TASKS),
                        new ContentTemplate(
                                true,
                                ++templateNumber,
                                ExpressionOperator.builder().build(),
                                TemplateType.INHERIT_PIPELINE,
                                true,
                                PipelineDoc.buildDocRef()
                                        .name("MyPipe2")
                                        .uuid("uuid456")
                                        .build(),
                                null,
                                null,
                                ProcessorFilter.DEFAULT_PRIORITY,
                                ProcessorFilter.DEFAULT_MAX_PROCESSING_TASKS)
                )).build();

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

    @Test
    void resetTemplateNumbers() {
        final List<ContentTemplate> contentTemplates = new ArrayList<>();
        int iter = 1;
        for (int i = 5; i > 0; i--) {
            final ContentTemplate contentTemplate = ContentTemplate.builder()
                    .withName(String.valueOf(iter))
                    .withTemplateNumber(i)
                    .build();
            contentTemplates.add(contentTemplate);
            iter++;
        }

        assertThat(contentTemplates.stream()
                .map(template -> template.getName() + ":" + template.getTemplateNumber())
                .toList())
                .containsExactly(
                        "1:5",
                        "2:4",
                        "3:3",
                        "4:2",
                        "5:1");

        final List<ContentTemplate> contentTemplates2 = ContentTemplates.resetTemplateNumbers(contentTemplates);

        assertThat(contentTemplates2.stream()
                .map(template -> template.getName() + ":" + template.getTemplateNumber())
                .toList())
                .containsExactly(
                        "1:1",
                        "2:2",
                        "3:3",
                        "4:4",
                        "5:5");

        // No change to the list as they are in the right order
        final List<ContentTemplate> contentTemplates3 = ContentTemplates.resetTemplateNumbers(contentTemplates2);

        assertThat(contentTemplates3)
                .isEqualTo(contentTemplates2);
    }
}
