package stroom.annotation.impl;

import stroom.config.common.AbstractDbConfig;
import stroom.config.common.ConnectionConfig;
import stroom.config.common.ConnectionPoolConfig;
import stroom.config.common.HasDbConfig;
import stroom.util.shared.AbstractConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.ArrayList;
import java.util.List;
import javax.inject.Singleton;

@Singleton
public class AnnotationConfig extends AbstractConfig implements HasDbConfig {

    private final AnnotationDBConfig dbConfig;
    private final List<String> statusValues;
    private final List<String> standardComments;
    private final String createText;

    public AnnotationConfig() {
        dbConfig = new AnnotationDBConfig();
        statusValues = List.of("New", "Assigned", "Closed");
        standardComments = new ArrayList<>();
        createText = "Create Annotation";
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public AnnotationConfig(@JsonProperty("db") final AnnotationDBConfig dbConfig,
                            @JsonProperty("statusValues") final List<String> statusValues,
                            @JsonProperty("standardComments") final List<String> standardComments,
                            @JsonProperty("createText") final String createText) {
        this.dbConfig = dbConfig;
        this.statusValues = statusValues;
        this.standardComments = standardComments;
        this.createText = createText;
    }

    @Override
    @JsonProperty("db")
    public AnnotationDBConfig getDbConfig() {
        return dbConfig;
    }

    @JsonProperty("statusValues")
    @JsonPropertyDescription("The different status values that can be set on an annotation")
    public List<String> getStatusValues() {
        return statusValues;
    }

    @JsonProperty("standardComments")
    @JsonPropertyDescription("A list of standard comments that can be added to annotations")
    public List<String> getStandardComments() {
        return standardComments;
    }

    @JsonProperty("createText")
    @JsonPropertyDescription("The text to display to create an annotation")
    public String getCreateText() {
        return createText;
    }

    public static class AnnotationDBConfig extends AbstractDbConfig {

        public AnnotationDBConfig() {
            super();
        }

        @JsonCreator
        public AnnotationDBConfig(
                @JsonProperty(PROP_NAME_CONNECTION) final ConnectionConfig connectionConfig,
                @JsonProperty(PROP_NAME_CONNECTION_POOL) final ConnectionPoolConfig connectionPoolConfig) {
            super(connectionConfig, connectionPoolConfig);
        }
    }
}
