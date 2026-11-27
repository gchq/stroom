/*
 * Copyright 2026 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.ai.impl;

import stroom.util.json.JsonUtil;
import stroom.util.shared.validation.ValidationSeverity;
import stroom.util.time.StroomDuration;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/// The settings for compacting chats, including those left idle.
class TestAiCompactionConfig {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @Test
    void idleChatsAreCompactedAfterTenMinutesAtSixtyPercentByDefault() {
        final AiCompactionConfig config = new AiCompactionConfig();

        assertThat(config.getIdleAfter()).isEqualTo(StroomDuration.ofMinutes(10));
        assertThat(config.getIdleThresholdPercent()).isEqualTo(60);
        assertThat(config.isIdleCompactionEnabled()).isTrue();
        assertThat(config.getEffectiveIdleThresholdPercent()).isEqualTo(60);
        assertThat(validator.validate(config)).isEmpty();
    }

    @Test
    void aConfigWithoutTheIdleSettingsReadsAsTheDefaults() {
        final AiCompactionConfig read = JsonUtil.readValue("{\"thresholdPercent\": 75}", AiCompactionConfig.class);

        assertThat(read.getThresholdPercent()).isEqualTo(75);
        assertThat(read.getIdleThresholdPercent()).isEqualTo(60);
        assertThat(read.getIdleAfter()).isNull();
    }

    @Test
    void anEmptyOrZeroIdleTimeTurnsIdleCompactionOff() {
        assertThat(config(null, 60).isIdleCompactionEnabled()).isFalse();
        assertThat(config(StroomDuration.ZERO, 60).isIdleCompactionEnabled()).isFalse();
        assertThat(config(StroomDuration.ofSeconds(1), 60).isIdleCompactionEnabled()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 101})
    void anIdleThresholdOutsideOneToAHundredIsAnError(final int percent) {
        final Set<ConstraintViolation<AiCompactionConfig>> violations = validator.validate(config(null, percent));

        assertThat(violations)
                .anySatisfy(violation -> {
                    assertThat(violation.getPropertyPath().toString()).isEqualTo("idleThresholdPercent");
                    assertThat(ValidationSeverity.fromPayloads(violation.getConstraintDescriptor().getPayload()))
                            .isEqualTo(ValidationSeverity.ERROR);
                });
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 79})
    void anIdleThresholdBelowTheThresholdIsUsedAsSet(final int percent) {
        final AiCompactionConfig config = config(null, percent);

        assertThat(validator.validate(config)).isEmpty();
        assertThat(config.getEffectiveIdleThresholdPercent()).isEqualTo(percent);
    }

    @ParameterizedTest
    @ValueSource(ints = {80, 95, 100})
    void anIdleThresholdAtOrAboveTheThresholdIsAWarningAndTheThresholdIsUsed(final int percent) {
        final AiCompactionConfig config = config(null, percent);

        final Set<ConstraintViolation<AiCompactionConfig>> violations = validator.validate(config);

        assertThat(violations).singleElement().satisfies(violation -> {
            assertThat(violation.getMessage()).contains("idleThresholdPercent should be below thresholdPercent");
            assertThat(ValidationSeverity.fromPayloads(violation.getConstraintDescriptor().getPayload()))
                    .isEqualTo(ValidationSeverity.WARNING);
        });
        // An idle compaction never leaves a chat above where a question would compact it.
        assertThat(config.getEffectiveIdleThresholdPercent()).isEqualTo(80);
    }

    private static AiCompactionConfig config(final StroomDuration idleAfter, final int idleThresholdPercent) {
        return new AiCompactionConfig(80, null, null, null, idleAfter, idleThresholdPercent);
    }
}
