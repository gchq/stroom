package stroom.data.retention.shared;

public enum RetentionRuleOutcome {
    /**
     * States that associated data should be deleted or marked for deletion
     */
    DELETE,
    /**
     * States that associated data should be retained
     */
    RETAIN
}
