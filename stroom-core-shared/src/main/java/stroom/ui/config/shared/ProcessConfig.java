package stroom.ui.config.shared;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import stroom.docref.SharedObject;

import javax.inject.Singleton;

@Singleton
public class ProcessConfig implements SharedObject {
    private static final long DEFAULT_TIME_LIMIT = 30L;
    private static final long DEFAULT_RECORD_LIMIT = 1000000L;

    private volatile long defaultTimeLimit = DEFAULT_TIME_LIMIT;
    private volatile long defaultRecordLimit = DEFAULT_RECORD_LIMIT;

    public ProcessConfig() {
        // Default constructor necessary for GWT serialisation.
    }

    @JsonPropertyDescription("The default number of minutes that batch search processing will be limited by.")
    public long getDefaultTimeLimit() {
        return defaultTimeLimit;
    }

    public void setDefaultTimeLimit(final long defaultTimeLimit) {
        this.defaultTimeLimit = defaultTimeLimit;
    }

    @JsonPropertyDescription("The default number of records that batch search processing will be limited by.")
    public long getDefaultRecordLimit() {
        return defaultRecordLimit;
    }

    public void setDefaultRecordLimit(final long defaultRecordLimit) {
        this.defaultRecordLimit = defaultRecordLimit;
    }
}
