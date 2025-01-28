package stroom.receive.common;

import stroom.docref.DocRef;
import stroom.util.NullSafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

@JsonPropertyOrder(alphabetic = true)
public class ContentTemplates {

    @JsonProperty
    private final Set<ContentTemplate> contentTemplates;

    @JsonCreator
    public ContentTemplates(
            @JsonProperty("contentTemplates") final Set<ContentTemplate> contentTemplates) {
        this.contentTemplates = contentTemplates;
    }

    public Set<ContentTemplate> getContentTemplates() {
        return contentTemplates;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ContentTemplates that = (ContentTemplates) o;
        return Objects.equals(contentTemplates, that.contentTemplates);
    }

    @Override
    public int hashCode() {
        return Objects.hash(contentTemplates);
    }

    @Override
    public String toString() {
        return "ContentTemplates{" +
               "contentTemplates=" + contentTemplates +
               '}';
    }

    // --------------------------------------------------------------------------------


    @JsonPropertyOrder(alphabetic = true)
    public static class ContentTemplate {

        @JsonProperty
        private final Map<String, String> attributeMap;
        @JsonProperty
        private final TemplateType templateType;
        @JsonProperty
        private final DocRef pipeline;

        @JsonCreator
        public ContentTemplate(@JsonProperty("attributeMap") final Map<String, String> attributeMap,
                               @JsonProperty("templateType") final TemplateType templateType,
                               @JsonProperty("pipeline") final DocRef pipeline) {
            this.attributeMap = Objects.requireNonNull(attributeMap);
            this.templateType = Objects.requireNonNull(templateType);
            this.pipeline = Objects.requireNonNull(pipeline);
        }

        /**
         * A map of header arguments and their values
         * that describe a certain shape of data that can
         * be processed by pipeline.
         */
        public Map<String, String> getAttributeMap() {
            return attributeMap;
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

        public boolean hasAttribute(final String key, final String value) {
            if (NullSafe.isNonBlankString(key)) {
                return NullSafe.test(attributeMap.get(key), val -> Objects.equals(val, value));
            } else {
                return false;
            }
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
            return Objects.equals(attributeMap,
                    that.attributeMap) && templateType == that.templateType && Objects.equals(pipeline,
                    that.pipeline);
        }

        @Override
        public int hashCode() {
            return Objects.hash(attributeMap, templateType, pipeline);
        }

        @Override
        public String toString() {
            return "ContentTemplate{" +
                   "attributeMap=" + attributeMap +
                   ", templateType=" + templateType +
                   ", pipeline=" + pipeline +
                   '}';
        }
    }


    // --------------------------------------------------------------------------------


    public enum TemplateType {
        /**
         * Create a processor filter specific to the feed on an existing pipeline that
         * is appropriate to the attributeMap values.
         */
        PROCESSOR_FILTER,
        /**
         * Create a new pipeline (and associated filter specific to the feed) that inherits
         * from an existing template pipeline that is appropriate to the attributeMap values.
         */
        INHERIT_PIPELINE,
        ;
    }
}
