package stroom.config.global.impl.validation;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.validation.ValidRegex;
import stroom.util.shared.validation.ValidSimpleCron;
import stroom.util.shared.validation.ValidationSeverity;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.inject.Guice;
import com.google.inject.Injector;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import javax.validation.constraints.AssertTrue;
import javax.validation.constraints.Min;

public class TestConfigValidator {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ConfigValidator.class);

    @Inject
    private ConfigValidator configValidator;

    @Test
    void testMyPojo_good() {

        final Injector injector = Guice.createInjector(new ValidationModule());
        injector.injectMembers(this);

        var myPojo = new MyPojoErrors();

        ConfigValidator.Result result = configValidator.validateRecursively(myPojo);
        
        LOGGER.info(result.toString());
        result.handleViolations((constraintViolation, validationSeverity) -> {
            LOGGER.info("{}, {}", validationSeverity, constraintViolation);
        });

        Assertions.assertThat(result.getErrorCount())
                .isEqualTo(0);
        Assertions.assertThat(result.getWarningCount())
                .isEqualTo(0);
    }

    @Test
    void testMyPojo_errors_recursive() {

        final Injector injector = Guice.createInjector(new ValidationModule());
        injector.injectMembers(this);

        var myPojo = new MyPojoErrors();
        myPojo.setBooleanValue(false);
        myPojo.setRegexValue("(((");
        myPojo.setCronValue("xxxxxxxxxxxxx");
        myPojo.setIntValue(0);

        myPojo.getChild().setBooleanValue(false);
        myPojo.getChild().setRegexValue("(((");
        myPojo.getChild().setCronValue("xxxxxxxxxxxxx");
        myPojo.getChild().setIntValue(0);

        ConfigValidator.Result result = configValidator.validateRecursively(myPojo);

        LOGGER.info(result.toString());
        result.handleViolations((constraintViolation, validationSeverity) -> {
            LOGGER.info("{}, {}", validationSeverity, constraintViolation);
        });

        Assertions.assertThat(result.getErrorCount()).isEqualTo(4);
        Assertions.assertThat(result.getWarningCount()).isEqualTo(4);
    }

    @Test
    void testMyPojo_errors_nonRecursive() {

        final Injector injector = Guice.createInjector(new ValidationModule());
        injector.injectMembers(this);

        var myPojo = new MyPojoErrors();
        myPojo.setBooleanValue(false);
        myPojo.setRegexValue("(((");
        myPojo.setCronValue("xxxxxxxxxxxxx");
        myPojo.setIntValue(0);

        myPojo.getChild().setBooleanValue(false);
        myPojo.getChild().setRegexValue("(((");
        myPojo.getChild().setCronValue("xxxxxxxxxxxxx");
        myPojo.getChild().setIntValue(0);

        ConfigValidator.Result result = configValidator.validate(myPojo);

        LOGGER.info(result.toString());
        result.handleViolations((constraintViolation, validationSeverity) -> {
            LOGGER.info("{}, {}", validationSeverity, constraintViolation);
        });

        Assertions.assertThat(result.getErrorCount()).isEqualTo(4);
        Assertions.assertThat(result.getWarningCount()).isEqualTo(0);
    }

    @Test
    void testMyPojo_warnings() {

        final Injector injector = Guice.createInjector(new ValidationModule());
        injector.injectMembers(this);

        var myPojo = new MyPojoWarnings();
        myPojo.setBooleanValue(false);
        myPojo.setRegexValue("(((");
        myPojo.setCronValue("xxxxxxxxxxxxx");
        myPojo.setIntValue(0);

        ConfigValidator.Result result = configValidator.validate(myPojo);

        LOGGER.info(result.toString());
        result.handleViolations((constraintViolation, validationSeverity) -> {
            LOGGER.info("{}, {}", validationSeverity, constraintViolation);
        });

        Assertions.assertThat(result.getErrorCount()).isEqualTo(0);
        Assertions.assertThat(result.getWarningCount()).isEqualTo(4);
    }


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
        boolean isBooleanValue() {
            return booleanValue;
        }

        void setBooleanValue(final boolean booleanValue) {
            this.booleanValue = booleanValue;
        }

        @ValidRegex
        @JsonProperty
        String getRegexValue() {
            return regexValue;
        }

        void setRegexValue(final String regexValue) {
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
        int getIntValue() {
            return intValue;
        }

        void setIntValue(final int intValue) {
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

    public static class MyPojoWarnings extends AbstractConfig {
        private boolean booleanValue = true;
        private String regexValue = "^.*$";
        private String cronValue = "* * *";
        private int intValue = 100;

        @AssertTrue(
            message = "Value should be true",
            payload = ValidationSeverity.Warning.class)
        @JsonProperty("booleanValue")
        boolean isBooleanValue() {
            return booleanValue;
        }

        void setBooleanValue(final boolean booleanValue) {
            this.booleanValue = booleanValue;
        }

        @ValidRegex(
            message = "Valid regex required",
            payload = ValidationSeverity.Warning.class)
        @JsonProperty
        String getRegexValue() {
            return regexValue;
        }

        void setRegexValue(final String regexValue) {
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
        int getIntValue() {
            return intValue;
        }

        void setIntValue(final int intValue) {
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


    public static class NoddyPojo {
        private String value;

        @JsonProperty
        String getValue() {
            return value;
        }

        void setValue(final String value) {
            this.value = value;
        }
    }

}