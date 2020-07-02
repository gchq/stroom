package stroom.data.retention.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class DataRetentionDeleteSummary {

    @JsonProperty
    private final String feedName;
    @JsonProperty
    private final String metaType;
    @JsonProperty
    private final int ruleNumber;
    @JsonProperty
    private final String ruleName;
    @JsonProperty
    private final int count;

    @JsonCreator
    public DataRetentionDeleteSummary(@JsonProperty("feedName") final String feedName,
                                      @JsonProperty("metaType") final String metaType,
                                      @JsonProperty("ruleNumber") final int ruleNumber,
                                      @JsonProperty("ruleName") final String ruleName,
                                      @JsonProperty("count") final int count) {
        this.feedName = Objects.requireNonNull(feedName);
        this.metaType = Objects.requireNonNull(metaType);
        this.ruleNumber = ruleNumber;
        this.ruleName = Objects.requireNonNull(ruleName);
        this.count = count;
    }

    public String getFeedName() {
        return feedName;
    }

    public String getMetaType() {
        return metaType;
    }

    public int getRuleNumber() {
        return ruleNumber;
    }

    public String getRuleName() {
        return ruleName;
    }

    public int getCount() {
        return count;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final DataRetentionDeleteSummary that = (DataRetentionDeleteSummary) o;
        return ruleNumber == that.ruleNumber &&
                count == that.count &&
                feedName.equals(that.feedName) &&
                metaType.equals(that.metaType) &&
                ruleName.equals(that.ruleName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(feedName, metaType, ruleNumber, ruleName, count);
    }
}
