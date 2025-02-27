package stroom.annotation.impl;

import stroom.config.common.AbstractDbConfig;
import stroom.config.common.ConnectionConfig;
import stroom.config.common.ConnectionPoolConfig;
import stroom.config.common.HasDbConfig;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.BootStrapConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@JsonPropertyOrder(alphabetic = true)
public class AnnotationConfig extends AbstractConfig implements IsStroomConfig, HasDbConfig {

    public static final String DEFAULT_RETENTION_PERIOD = "5y";

    private final AnnotationDBConfig dbConfig;
    private final List<String> statusValues;
    private final List<String> standardComments;
    private final String createText;
    private final String defaultRetentionPeriod;
    private final StroomDuration physicalDeleteAge;

    public AnnotationConfig() {
        dbConfig = new AnnotationDBConfig();
        statusValues = List.of("New", "Assigned", "Closed");
        standardComments = new ArrayList<>();
        createText = "Create Annotation";
        defaultRetentionPeriod = DEFAULT_RETENTION_PERIOD;
        physicalDeleteAge = StroomDuration.ofDays(7);
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public AnnotationConfig(@JsonProperty("db") final AnnotationDBConfig dbConfig,
                            @JsonProperty("statusValues") final List<String> statusValues,
                            @JsonProperty("standardComments") final List<String> standardComments,
                            @JsonProperty("createText") final String createText,
                            @JsonProperty("defaultRetentionPeriod") final String defaultRetentionPeriod,
                            @JsonProperty("physicalDeleteAge") final StroomDuration physicalDeleteAge) {
        this.dbConfig = dbConfig;
        this.statusValues = statusValues;
        this.standardComments = standardComments;
        this.createText = createText;
        this.defaultRetentionPeriod = defaultRetentionPeriod;
        this.physicalDeleteAge = physicalDeleteAge;
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

    @JsonProperty("defaultRetentionPeriod")
    @JsonPropertyDescription("How long should we retain annotations by default, e.g. 5y")
    public String getDefaultRetentionPeriod() {
        return defaultRetentionPeriod;
    }

    @JsonPropertyDescription("How long to keep logically deleted annotations before deleting them. " +
                             "In ISO-8601 duration format, e.g. 'P1DT12H'")
    public StroomDuration getPhysicalDeleteAge() {
        return physicalDeleteAge;
    }

    @BootStrapConfig
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
