package stroom.annotation.impl;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;
import stroom.node.server.StroomPropertyService;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
@Singleton
public class AnnotationConfig {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AnnotationConfig.class);
    private StroomPropertyService stroomPropertyService;

    private List<String> statusValues = new ArrayList<>();
    private List<String> standardComments = new ArrayList<>();
    private String createText = "Create Annotation";

    public AnnotationConfig() {
        statusValues.add("New");
        statusValues.add("Assigned");
        statusValues.add("Closed");
    }

    @Inject
    public AnnotationConfig(final StroomPropertyService stroomPropertyService) {
        this.stroomPropertyService = stroomPropertyService;
    }

    @JsonProperty("statusValues")
    @JsonPropertyDescription("The different status values that can be set on an annotation")
    public List<String> getStatusValues() {
        if (stroomPropertyService != null) {
            return createList("stroom.annotation.statusValues", "New,Assigned,Closed");
        }
        return statusValues;
    }

    public void setStatusValues(final List<String> statusValues) {
        this.statusValues = statusValues;
    }

    @JsonProperty("standardComments")
    @JsonPropertyDescription("A comma separated list of standard comments that can be added to annotations")
    public List<String> getStandardComments() {
        if (stroomPropertyService != null) {
            return createList("stroom.annotation.standardComments", "");
        }
        return standardComments;
    }

    public void setStandardComments(final List<String> standardComments) {
        this.standardComments = standardComments;
    }

    @JsonProperty("createText")
    @JsonPropertyDescription("The text to display to create an annotation")
    public String getCreateText() {
        if (stroomPropertyService != null) {
            return stroomPropertyService.getProperty("stroom.annotation.createText", "Create Annotation");
        }
        return createText;
    }

    public void setCreateText(final String createText) {
        this.createText = createText;
    }

    private List<String> createList(final String propertyName, final String defaultValue) {
        final List<String> list = new ArrayList<>();
        try {
            final String values = stroomPropertyService.getProperty(propertyName, defaultValue);
            final CSVParser parser = CSVParser.parse(values, CSVFormat.RFC4180);
            for (final CSVRecord csvRecord : parser) {
                for (final String value : csvRecord) {
                    list.add(value);
                }
            }
        } catch (final IOException | RuntimeException e) {
            LOGGER.error(e::getMessage, e);
        }
        return list;
    }
}
