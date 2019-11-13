package stroom.annotation.impl;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import org.springframework.stereotype.Component;
import stroom.node.server.StroomPropertyService;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
@Singleton
public class AnnotationConfig {
    private StroomPropertyService stroomPropertyService;

    private List<String> statusValues = new ArrayList<>();
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
            final String values = stroomPropertyService.getProperty("stroom.annotation.statusValues", "New,Assigned,Closed");
            return Arrays.asList(values.split(","));
        }
        return statusValues;
    }

    public void setStatusValues(final List<String> statusValues) {
        this.statusValues = statusValues;
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
}
