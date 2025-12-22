/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.util.validation;

import stroom.test.common.util.test.TestingHomeAndTempProvidersModule;
import stroom.util.config.AppConfigValidator;
import stroom.util.config.ConfigValidator;
import stroom.util.config.ConfigValidator.Result;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.validation.ValidRegex;
import stroom.util.shared.validation.ValidSimpleCron;
import stroom.util.shared.validation.ValidationSeverity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.inject.Guice;
import com.google.inject.Injector;
import jakarta.inject.Inject;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

public class TestAppConfigValidator {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AppConfigValidator.class);

    @Inject
    private AppConfigValidator appConfigValidator;

    @BeforeEach
    void beforeEach(@TempDir final Path tempDir) {
        final Injector injector = Guice.createInjector(
                new TestingHomeAndTempProvidersModule(tempDir),
                new ValidationModule());
        injector.injectMembers(this);
    }

    @Test
    void testMyPojo_good() {

        final MyPojoErrors myPojo = new MyPojoErrors();

        final ConfigValidator.Result<AbstractConfig> result = appConfigValidator.validateRecursively(myPojo);

        LOGGER.info(result.toString());
        result.handleViolations((constraintViolation, validationSeverity) ->
                LOGGER.info("{}, {}", validationSeverity, constraintViolation));

        Assertions.assertThat(result.getErrorCount())
                .isEqualTo(0);
        Assertions.assertThat(result.getWarningCount())
                .isEqualTo(0);
    }

    @Test
    void testMyPojo_errors_recursive() {

//        final Injector injector = Guice.createInjector(new ValidationModule());
//        injector.injectMembers(this);

        final MyPojoErrors myPojo = new MyPojoErrors();
        myPojo.setBooleanValue(false);
        myPojo.setRegexValue("(((");
        myPojo.setCronValue("xxxxxxxxxxxxx");
        myPojo.setIntValue(0);

        myPojo.getChild().setBooleanValue(false);
        myPojo.getChild().setRegexValue("(((");
        myPojo.getChild().setCronValue("xxxxxxxxxxxxx");
        myPojo.getChild().setIntValue(0);

        final ConfigValidator.Result<AbstractConfig> result = appConfigValidator.validateRecursively(myPojo);

        LOGGER.info(result.toString());
        result.handleViolations((constraintViolation, validationSeverity) -> {
            LOGGER.info("{}, {}", validationSeverity, constraintViolation);
        });

        Assertions.assertThat(result.getErrorCount()).isEqualTo(4);
        Assertions.assertThat(result.getWarningCount()).isEqualTo(4);
    }

    @Test
    void testMyPojo_errors_nonRecursive() {

//        final Injector injector = Guice.createInjector(new ValidationModule());
//        injector.injectMembers(this);

        final MyPojoErrors myPojo = new MyPojoErrors();
        myPojo.setBooleanValue(false);
        myPojo.setRegexValue("(((");
        myPojo.setCronValue("xxxxxxxxxxxxx");
        myPojo.setIntValue(0);

        myPojo.getChild().setBooleanValue(false);
        myPojo.getChild().setRegexValue("(((");
        myPojo.getChild().setCronValue("xxxxxxxxxxxxx");
        myPojo.getChild().setIntValue(0);

        final ConfigValidator.Result<AbstractConfig> result = appConfigValidator.validate(myPojo);

        LOGGER.info(result.toString());
        result.handleViolations((constraintViolation, validationSeverity) -> {
            LOGGER.info("{}, {}", validationSeverity, constraintViolation);
        });

        Assertions.assertThat(result.getErrorCount()).isEqualTo(4);
        Assertions.assertThat(result.getWarningCount()).isEqualTo(0);
    }

    @Test
    void testMyPojo_warnings() {

//        final Injector injector = Guice.createInjector(new ValidationModule());
//        injector.injectMembers(this);

        final MyPojoWarnings myPojo = new MyPojoWarnings();
        myPojo.setBooleanValue(false);
        myPojo.setRegexValue("(((");
        myPojo.setCronValue("xxxxxxxxxxxxx");
        myPojo.setIntValue(0);

        final ConfigValidator.Result<AbstractConfig> result = appConfigValidator.validate(myPojo);

        LOGGER.info(result.toString());
        result.handleViolations((constraintViolation, validationSeverity) -> {
            LOGGER.info("{}, {}", validationSeverity, constraintViolation);
        });

        Assertions.assertThat(result.getErrorCount()).isEqualTo(0);
        Assertions.assertThat(result.getWarningCount()).isEqualTo(4);
    }

    @Test
    void testMethodValidation() {
        final NoddyPojoWithValidationMethod pojo = new NoddyPojoWithValidationMethod("foo", "bar");
        final Result<AbstractConfig> result = appConfigValidator.validate(pojo);
        result.handleViolations((violation, severity) -> LOGGER.error("Got {} violation {}",
                severity, violation.getMessage()));

        Assertions.assertThat(result.getErrorCount())
                .isEqualTo(1);
    }


    // --------------------------------------------------------------------------------


    public static class MyPojoErrors extends AbstractConfig {

        private boolean booleanValue = true;
        private String regexValue = "^.*$";
        private String cronValue = "* * *";
        private int intValue = 100;

        private MyPojoWarnings child = new MyPojoWarnings();
        private NoddyPojo childWithNoValidation = new NoddyPojo();

        @JsonProperty
        public MyPojoWarnings getChild() {
            return child;
        }

        public void setChild(final MyPojoWarnings child) {
            this.child = child;
        }

        @JsonProperty
        public NoddyPojo getChildWithNoValidation() {
            return childWithNoValidation;
        }

        public void setChildWithNoValidation(final NoddyPojo childWithNoValidation) {
            this.childWithNoValidation = childWithNoValidation;
        }

        @AssertTrue(
                message = "Value should be true")
        @JsonProperty("booleanValue")
        public boolean isBooleanValue() {
            return booleanValue;
        }

        public void setBooleanValue(final boolean booleanValue) {
            this.booleanValue = booleanValue;
        }

        @ValidRegex
        @JsonProperty
        public String getRegexValue() {
            return regexValue;
        }

        public void setRegexValue(final String regexValue) {
            this.regexValue = regexValue;
        }

        @ValidSimpleCron
        @JsonProperty
        public String getCronValue() {
            return cronValue;
        }

        public void setCronValue(final String cronValue) {
            this.cronValue = cronValue;
        }

        @Min(100)
        @JsonProperty
        public int getIntValue() {
            return intValue;
        }

        public void setIntValue(final int intValue) {
            this.intValue = intValue;
        }

        @Override
        public String toString() {
            return "MyPojo{" +
                   "booleanValue=" + booleanValue +
                   ", regexValue='" + regexValue + '\'' +
                   ", intValue=" + intValue +
                   '}';
        }
    }


    // --------------------------------------------------------------------------------


    public static class MyPojoWarnings extends AbstractConfig {

        private boolean booleanValue = true;
        private String regexValue = "^.*$";
        private String cronValue = "* * *";
        private int intValue = 100;

        @AssertTrue(
                message = "Value should be true",
                payload = ValidationSeverity.Warning.class)
        @JsonProperty("booleanValue")
        public boolean isBooleanValue() {
            return booleanValue;
        }

        public void setBooleanValue(final boolean booleanValue) {
            this.booleanValue = booleanValue;
        }

        @ValidRegex(
                message = "Valid regex required",
                payload = ValidationSeverity.Warning.class)
        @JsonProperty
        public String getRegexValue() {
            return regexValue;
        }

        public void setRegexValue(final String regexValue) {
            this.regexValue = regexValue;
        }

        @ValidSimpleCron(
                message = "Valid cron required",
                payload = ValidationSeverity.Warning.class)
        @JsonProperty
        public String getCronValue() {
            return cronValue;
        }

        public void setCronValue(final String cronValue) {
            this.cronValue = cronValue;
        }

        @Min(
                value = 100,
                message = "Value should be >= 100",
                payload = ValidationSeverity.Warning.class)
        @JsonProperty
        public int getIntValue() {
            return intValue;
        }

        public void setIntValue(final int intValue) {
            this.intValue = intValue;
        }

        @Override
        public String toString() {
            return "MyPojo{" +
                   "booleanValue=" + booleanValue +
                   ", regexValue='" + regexValue + '\'' +
                   ", intValue=" + intValue +
                   '}';
        }
    }


    // --------------------------------------------------------------------------------


    public static class NoddyPojo {

        private String value;

        @JsonProperty
        public String getValue() {
            return value;
        }

        public void setValue(final String value) {
            this.value = value;
        }
    }


    // --------------------------------------------------------------------------------


    public static class NoddyPojoWithValidationMethod extends AbstractConfig {

        private String value1;
        private String value2;

        public NoddyPojoWithValidationMethod(final String value1, final String value2) {
            this.value1 = value1;
            this.value2 = value2;
        }

        @NotNull
        public String getValue1() {
            return value1;
        }

        public void setValue1(final String value1) {
            this.value1 = value1;
        }

        @NotNull
        public String getValue2() {
            return value2;
        }

        public void setValue2(final String value2) {
            this.value2 = value2;
        }

        @JsonIgnore
        @AssertTrue(message = "value1 and value2 must be the same")
        public boolean isValid() {
            return value1.equals(value2);
        }
    }

}
