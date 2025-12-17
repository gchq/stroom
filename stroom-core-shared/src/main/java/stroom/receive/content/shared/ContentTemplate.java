/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.receive.content.shared;

import stroom.docref.DocRef;
import stroom.processor.shared.ProcessorFilter;
import stroom.query.api.ExpressionOperator;
import stroom.util.shared.NullSafe;
import stroom.util.shared.SerialisationTestConstructor;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class ContentTemplate {

    public static final TemplateType DEFAULT_TEMPLATE_TYPE = TemplateType.INHERIT_PIPELINE;

    @JsonProperty
    private final boolean enabled;
    @JsonProperty
    private final int templateNumber;
    @JsonProperty
    private final ExpressionOperator expression;
    @JsonProperty
    private final TemplateType templateType;
    @JsonProperty
    private final boolean copyElementDependencies;
    @JsonProperty
    private final DocRef pipeline;
    @JsonProperty
    private final String name;
    @JsonProperty
    private final String description;
    @JsonProperty
    private final int processorPriority;
    @JsonProperty
    private final int processorMaxConcurrent;

    @JsonCreator
    public ContentTemplate(@JsonProperty("enabled") final Boolean enabled,
                           @JsonProperty("templateNumber") final int templateNumber,
                           @JsonProperty("expression") final ExpressionOperator expression,
                           @JsonProperty("templateType") final TemplateType templateType,
                           @JsonProperty("copyElementDependencies") final Boolean copyElementDependencies,
                           @JsonProperty("pipeline") final DocRef pipeline,
                           @JsonProperty("name") final String name,
                           @JsonProperty("description") final String description,
                           @JsonProperty("processorPriority") final int processorPriority,
                           @JsonProperty("processorMaxConcurrent") final int processorMaxConcurrent) {
        if (templateNumber < 1) {
            throw new IllegalArgumentException(
                    "Invalid templateNumber " + templateNumber + ". Must be >= 1.");
        }
        if (processorPriority < 0) {
            throw new IllegalArgumentException("processorPriority must be >= 0");
        }
        if (processorMaxConcurrent < 0) {
            throw new IllegalArgumentException("processorMaxConcurrent must be >= 0");
        }
        this.enabled = NullSafe.requireNonNullElse(enabled, true);
        this.templateNumber = templateNumber;
        this.expression = NullSafe.requireNonNullElseGet(
                expression,
                () -> ExpressionOperator.builder().build());
        this.templateType = NullSafe.requireNonNullElse(templateType, DEFAULT_TEMPLATE_TYPE);
        this.copyElementDependencies = NullSafe.requireNonNullElse(copyElementDependencies, false);

        if (this.copyElementDependencies && templateType == TemplateType.PROCESSOR_FILTER) {
            throw new IllegalArgumentException("copyElementDependencies cannot be set to true if templateType is "
                                               + TemplateType.PROCESSOR_FILTER);
        }

        this.pipeline = Objects.requireNonNull(pipeline);
        this.name = name;
        this.description = description;
        this.processorPriority = processorPriority;
        this.processorMaxConcurrent = processorMaxConcurrent;
    }

    @SerialisationTestConstructor
    private ContentTemplate() {
        this(ContentTemplate.builder()
                .withExpression(ExpressionOperator.builder().build())
                .withTemplateNumber(1)
                .withPipeline(new DocRef("test", "test"))
                .withTemplateType(TemplateType.INHERIT_PIPELINE));
    }

    private ContentTemplate(final Builder builder) {
        enabled = builder.enabled;
        templateNumber = builder.templateNumber;
        expression = builder.expression;
        templateType = builder.templateType;
        copyElementDependencies = builder.copyElementDependencies;
        pipeline = builder.pipeline;
        name = builder.name;
        description = builder.description;
        processorPriority = builder.processorPriority;
        processorMaxConcurrent = builder.processorMaxConcurrent;
    }

    public Builder copy() {
        return copy(this);
    }

    public static Builder copy(final ContentTemplate copy) {
        final Builder builder = new Builder();
        builder.enabled = copy.isEnabled();
        builder.templateNumber = copy.getTemplateNumber();
        builder.expression = copy.getExpression();
        builder.templateType = copy.getTemplateType();
        builder.copyElementDependencies = copy.isCopyElementDependencies();
        builder.pipeline = copy.getPipeline();
        builder.name = copy.getName();
        builder.description = copy.getDescription();
        builder.processorPriority = copy.getProcessorPriority();
        builder.processorMaxConcurrent = copy.getProcessorMaxConcurrent();
        return builder;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * The ordinal number of the template, one based. Templates are checked in order of templateNumber,
     * lowest number first. One based. Template numbers should be contiguous.
     */
    public int getTemplateNumber() {
        return templateNumber;
    }

    /**
     * The ordinal number of the template, zero based. Templates are checked in order of templateNumber,
     * lowest number first. Template numbers should be contiguous.
     */
    @JsonIgnore
    public int getTemplateIndex() {
        return templateNumber - 1;
    }

    public ExpressionOperator getExpression() {
        return expression;
    }

    /**
     * The nature of the content templating.
     */
    public TemplateType getTemplateType() {
        return templateType;
    }

    public boolean isCopyElementDependencies() {
        return copyElementDependencies;
    }

    /**
     * The pipeline to process the data with or to inherit from (depending
     * on the value of templateType).
     */
    public DocRef getPipeline() {
        return pipeline;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public int getProcessorPriority() {
        return processorPriority;
    }

    public int getProcessorMaxConcurrent() {
        return processorMaxConcurrent;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ContentTemplate that = (ContentTemplate) o;
        return enabled == that.enabled
               && templateNumber == that.templateNumber
               && processorPriority == that.processorPriority
               && processorMaxConcurrent == that.processorMaxConcurrent
               && Objects.equals(expression, that.expression)
               && templateType == that.templateType
               && copyElementDependencies == that.copyElementDependencies
               && Objects.equals(pipeline, that.pipeline)
               && Objects.equals(name, that.name)
               && Objects.equals(description, that.description);
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled,
                templateNumber,
                expression,
                templateType,
                copyElementDependencies,
                pipeline,
                name,
                description,
                processorPriority,
                processorMaxConcurrent);
    }

    @Override
    public String toString() {
        return "ContentTemplate{" +
               "enabled=" + enabled +
               ", templateNumber=" + templateNumber +
               ", expression=" + expression +
               ", templateType=" + templateType +
               ", copyElementDependencies=" + copyElementDependencies +
               ", pipeline=" + pipeline +
               ", name='" + name + '\'' +
               ", description='" + description + '\'' +
               ", processorPriority=" + processorPriority +
               ", processorMaxConcurrent=" + processorMaxConcurrent +
               '}';
    }

    /**
     * If isEnabled is different to the current enabled state return a new
     * {@link ContentTemplate} with the passed enabled state, else return this.
     */
    public ContentTemplate withEnabledState(final boolean isEnabled) {
        return this.enabled == isEnabled
                ? this
                : new ContentTemplate(
                        isEnabled,
                        templateNumber,
                        expression,
                        templateType,
                        copyElementDependencies,
                        pipeline,
                        name,
                        description,
                        processorPriority,
                        processorMaxConcurrent);
    }

    /**
     * If templateNumber is different to the current templateNumber return a new
     * {@link ContentTemplate} with the passed templateNumber, else return this.
     */
    public ContentTemplate withTemplateNumber(final int templateNumber) {
        return this.templateNumber == templateNumber
                ? this
                : new ContentTemplate(
                        enabled,
                        templateNumber,
                        expression,
                        templateType,
                        copyElementDependencies,
                        pipeline,
                        name,
                        description,
                        processorPriority,
                        processorMaxConcurrent);
    }

    public static Builder builder() {
        return new Builder();
    }


    // --------------------------------------------------------------------------------


    public static final class Builder {

        private boolean enabled;
        private int templateNumber;
        private ExpressionOperator expression;
        private TemplateType templateType;
        private boolean copyElementDependencies;
        private DocRef pipeline;
        private String name;
        private String description;
        private int processorPriority = ProcessorFilter.DEFAULT_PRIORITY;
        private int processorMaxConcurrent = ProcessorFilter.DEFAULT_MAX_PROCESSING_TASKS;

        private Builder() {
        }

        public static Builder builder() {
            return new Builder();
        }

        public Builder withEnabled(final boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder withTemplateNumber(final int templateNumber) {
            this.templateNumber = templateNumber;
            return this;
        }

        public Builder withExpression(final ExpressionOperator expression) {
            this.expression = expression;
            return this;
        }

        public Builder withTemplateType(final TemplateType templateType) {
            this.templateType = templateType;
            return this;
        }

        public Builder withCopyElementDependencies(final boolean copyElementDependencies) {
            this.copyElementDependencies = copyElementDependencies;
            return this;
        }

        public Builder withPipeline(final DocRef pipeline) {
            this.pipeline = pipeline;
            return this;
        }

        public Builder withName(final String name) {
            this.name = name;
            return this;
        }

        public Builder withDescription(final String description) {
            this.description = description;
            return this;
        }

        public Builder withProcessorPriority(final int processorPriority) {
            this.processorPriority = processorPriority;
            return this;
        }

        public Builder withProcessorMaxConcurrent(final int processorMaxConcurrent) {
            this.processorMaxConcurrent = processorMaxConcurrent;
            return this;
        }

        public ContentTemplate build() {
            return new ContentTemplate(this);
        }
    }
}
