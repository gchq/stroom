package stroom.receive.content;

import stroom.docref.DocRef;
import stroom.query.api.v2.ExpressionOperator;
import stroom.util.shared.GwtNullSafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class ContentTemplate {

    @JsonProperty
    private final boolean enabled;
    @JsonProperty
    private final int templateNumber;
    @JsonProperty
    private final ExpressionOperator expression;
    @JsonProperty
    private final TemplateType templateType;
    @JsonProperty
    private final DocRef pipeline;
    @JsonProperty
    private final String name;
    @JsonProperty
    private final String description;

    @JsonCreator
    public ContentTemplate(@JsonProperty("enabled") final boolean enabled,
                           @JsonProperty("templateNumber") final int templateNumber,
                           @JsonProperty("expression") final ExpressionOperator expression,
                           @JsonProperty("templateType") final TemplateType templateType,
                           @JsonProperty("pipeline") final DocRef pipeline,
                           @JsonProperty("name") final String name,
                           @JsonProperty("description") final String description) {
        this.enabled = enabled;
        this.templateNumber = templateNumber;
        this.expression = GwtNullSafe.requireNonNullElseGet(
                expression,
                () -> ExpressionOperator.builder().build());
        this.templateType = Objects.requireNonNull(templateType);
        this.pipeline = Objects.requireNonNull(pipeline);
        this.name = name;
        this.description = description;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * The ordinal number of the template. Templates are checked in order of templateNumber,
     * lowest number first.
     */
    public int getTemplateNumber() {
        return templateNumber;
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
               && Objects.equals(expression, that.expression)
               && templateType == that.templateType
               && Objects.equals(pipeline, that.pipeline)
               && Objects.equals(name, that.name)
               && Objects.equals(description, that.description);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                enabled,
                templateNumber,
                expression,
                templateType,
                pipeline,
                name,
                description);
    }

    @Override
    public String toString() {
        return "ContentTemplate{" +
               "enabled=" + enabled +
               ", templateNumber=" + templateNumber +
               ", expression=" + expression +
               ", templateType=" + templateType +
               ", pipeline=" + pipeline +
               ", name='" + name + '\'' +
               ", description='" + description + '\'' +
               '}';
    }
}
