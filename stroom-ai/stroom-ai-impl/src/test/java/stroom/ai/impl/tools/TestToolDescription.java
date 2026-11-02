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

import stroom.ai.impl.tools.ToolSchema.ArraySchema;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;

import dev.langchain4j.agent.tool.ToolSpecification;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestToolDescription {

    private static final ToolDescription DELETE = ToolDescription.builder()
            .general("Deletes a file")
            .chatOnly(" in /outputs/")
            .general(" that is no longer needed.")
            .chatOnly(" Use ls to find it.")
            .build();

    @Test
    void theChatIsShownEveryPartExactlyAsWritten() {
        assertThat(DELETE.forChat()).isEqualTo("Deletes a file in /outputs/ that is no longer needed. Use ls to "
                                               + "find it.");
    }

    @Test
    void theGeneralRenderingLeavesOutTheChatOnlyParts() {
        assertThat(DELETE.general()).isEqualTo("Deletes a file that is no longer needed.");
        assertThat(DELETE.hasChatOnlyParts()).isTrue();
        assertThat(DELETE.isChatOnly()).isFalse();
    }

    @Test
    void aLeftOutPartLeavesNoDoubledSpaceOrSpaceBeforePunctuation() {
        final ToolDescription description = ToolDescription.builder()
                .general("Lists the files ")
                .chatOnly("in the workspace")
                .general(". Reads ")
                .chatOnly("workspace")
                .general(" files ")
                .chatOnly("(e.g. /outputs/)")
                .general(", and more.")
                .build();

        assertThat(description.general()).isEqualTo("Lists the files. Reads files, and more.");
    }

    @Test
    void aDescriptionOfOnePlainTextIsGeneralThroughout() {
        final ToolDescription description = ToolDescription.of("Says the time.");

        assertThat(description.forChat()).isEqualTo("Says the time.");
        assertThat(description.general()).isEqualTo("Says the time.");
        assertThat(description.hasChatOnlyParts()).isFalse();
        assertThat(ToolDescription.of(null)).isNull();
        assertThat(ToolDescription.forChat(null)).isNull();
    }

    @Test
    void aWhollyChatOnlyDescriptionHasNoGeneralRendering() {
        final ToolDescription description = ToolDescription.builder()
                .chatOnly("A file in /outputs/ to write them to.")
                .build();

        assertThat(description.isChatOnly()).isTrue();
        assertThat(description.general()).isEmpty();
        assertThat(description.withoutChatOnlyParts()).isNull();
        // An empty description is not chat-only: there is nothing in it to leave out.
        assertThat(ToolDescription.builder().build().isChatOnly()).isFalse();
    }

    @Test
    void aPartMustHaveText() {
        assertThatThrownBy(() -> ToolDescription.builder().general(null).build())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void theChatsSpecificationHasEveryPart() {
        final ToolSpecification specification = LangChain4jTools.specification(definition());

        assertThat(specification.description())
                .isEqualTo("Deletes a file in /outputs/ that is no longer needed. Use ls to find it.");
        assertThat(specification.parameters().properties()).containsOnlyKeys("path", "out", "tags");
        assertThat(specification.parameters().required()).containsExactly("path", "out");
    }

    @Test
    void theGeneralDefinitionLeavesOutChatOnlyPartsAndWhollyChatOnlyParameters() {
        final ToolDefinition definition = definition();
        final ToolDefinition general = definition.general();

        assertThat(general.name()).isEqualTo("delete_file");
        assertThat(general.description()).isEqualTo("Deletes a file that is no longer needed.");
        assertThat(general.parameters().properties()).containsOnlyKeys("path", "tags");
        assertThat(general.parameters().properties().get("path").description()).isEqualTo("The file.");
        assertThat(((ArraySchema) general.parameters().properties().get("tags")).items().description())
                .isEqualTo("A tag.");
        // A parameter that is left out is no longer required.
        assertThat(general.parameters().required()).containsExactly("path");
        // The chat's definition is unchanged.
        assertThat(definition.description()).isEqualTo(DELETE.forChat());
        assertThat(definition.parameters().properties()).containsOnlyKeys("path", "out", "tags");
    }

    @Test
    void aDefinitionWithoutDescriptionOrParametersHasAGeneralOneToo() {
        final ToolDefinition general = ToolDefinition.builder().name("t").build().general();

        assertThat(general.description()).isNull();
        assertThat(general.parameters()).isNull();
    }

    private static ToolDefinition definition() {
        return ToolDefinition.builder()
                .name("delete_file")
                .description(DELETE)
                .parameters(ObjectSchema.builder()
                        .addStringProperty("path", ToolDescription.builder()
                                .general("The file")
                                .chatOnly(", e.g. /outputs/a.csv")
                                .general(".")
                                .build())
                        .addStringProperty("out", ToolDescription.builder()
                                .chatOnly("A file in /outputs/ to write them to.")
                                .build())
                        .addProperty("tags", ArraySchema.builder()
                                .items(ObjectSchema.builder()
                                        .description(ToolDescription.builder()
                                                .general("A tag")
                                                .chatOnly(", as ls shows it")
                                                .general(".")
                                                .build())
                                        .build())
                                .description("The tags.")
                                .build())
                        .required(List.of("path", "out"))
                        .build())
                .build();
    }
}
