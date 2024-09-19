package stroom.aws.s3.shared;

import stroom.docref.HasDisplayValue;

public enum AwsCredentialsProviderType implements HasDisplayValue {
    ANONYMOUS("Anonymous Credentials Provider"),
    DEFAULT("Default Credentials Provider"),
    ENVIRONMENT_VARIABLE("Environment Variable Credentials Provider"),
    //    LAZY("Lazy Aws Credentials Provider"),
//    PROCESS("Process Credentials Provider"),
    PROFILE("Profile Credentials Provider"),
    STATIC("Static Credentials Provider"),
    SYSTEM_PROPERTY("System Property Credentials Provider"),
    WEB("Web Identity Token File Credentials Provider");

    private final String displayValue;

    AwsCredentialsProviderType(final String displayValue) {
        this.displayValue = displayValue;
    }

    @Override
    public String getDisplayValue() {
        return displayValue;
    }
}
