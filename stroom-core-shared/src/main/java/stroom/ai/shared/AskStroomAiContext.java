/*
 * Copyright 2025 Crown Copyright
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

package stroom.ai.shared;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = DashboardTableContext.class, name = "dashboardTable"),
        @JsonSubTypes.Type(value = QueryTableContext.class, name = "queryTable"),
        @JsonSubTypes.Type(value = GeneralTableContext.class, name = "generalTable"),
        @JsonSubTypes.Type(value = DocumentReferenceContext.class, name = "documentReference")
})
@Schema(
        discriminatorProperty = "type",
        discriminatorMapping = {
                @DiscriminatorMapping(value = "dashboardTable", schema = DashboardTableContext.class),
                @DiscriminatorMapping(value = "queryTable", schema = QueryTableContext.class),
                @DiscriminatorMapping(value = "generalTable", schema = GeneralTableContext.class),
                @DiscriminatorMapping(value = "documentReference", schema = DocumentReferenceContext.class)})
public abstract sealed class AskStroomAiContext permits DashboardTableContext, QueryTableContext, GeneralTableContext,
        DocumentReferenceContext {

    public AskStroomAiContext() {
    }

    /**
     * Returns a human-readable description of this context suitable for display in the UI,
     * chat message headers, and audit event logs.
     */
    public abstract String getDescription();
}
