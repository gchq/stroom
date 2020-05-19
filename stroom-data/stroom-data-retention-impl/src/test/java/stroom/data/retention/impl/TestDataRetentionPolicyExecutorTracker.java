package stroom.data.retention.impl;

import stroom.data.retention.shared.DataRetentionRule;
import stroom.data.retention.shared.DataRetentionRules;
import stroom.data.retention.shared.TimeUnit;
import stroom.query.api.v2.ExpressionOperator;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

class TestDataRetentionPolicyExecutorTracker {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestDataRetentionPolicyExecutorTracker.class);

    @Test
    void testTrackerSerialisation() throws IOException {

        final List<DataRetentionRule> rules = List.of(
                new DataRetentionRule(1,
                        Instant.now().toEpochMilli(),
                        "Default",
                        true,
                        new ExpressionOperator(),
                        1,
                        TimeUnit.MONTHS,
                        false),
                new DataRetentionRule(2,
                        Instant.now().toEpochMilli(),
                        "Other",
                        true,
                        new ExpressionOperator(),
                        5,
                        TimeUnit.DAYS,
                        false)
        );

        final DataRetentionRules dataRetentionRules = new DataRetentionRules(
                DataRetentionRules.DOCUMENT_TYPE,
                UUID.randomUUID().toString(),
                "MyRules",
                UUID.randomUUID().toString(),
                Instant.now().toEpochMilli(),
                Instant.now().toEpochMilli(),
                "admin",
                "admin",
                rules);

        final DataRetentionPolicyExecutor.Tracker tracker = new DataRetentionPolicyExecutor.Tracker(
                Instant.now().toEpochMilli(), dataRetentionRules);

        ObjectMapper objectMapper = new ObjectMapper();
        String json = objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(tracker);

        LOGGER.info("\n{}", json);

        DataRetentionPolicyExecutor.Tracker tracker2 = objectMapper.readValue(
                json, DataRetentionPolicyExecutor.Tracker.class);

        Assertions.assertThat(tracker2).isEqualTo(tracker);
    }
}