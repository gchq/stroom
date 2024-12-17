package stroom.receive.common;

import stroom.data.shared.StreamTypeNames;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.shared.validation.IsSupersetOf;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.dropwizard.validation.ValidationMethod;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.HashSet;
import java.util.Set;


@JsonPropertyOrder(alphabetic = true)
public class ReceiveDataConfig
        extends AbstractConfig
        implements IsStroomConfig, IsProxyConfig {

    private final String receiptPolicyUuid;
    private final Set<String> metaTypes;
    private final boolean tokenAuthenticationEnabled;
    private final boolean certificateAuthenticationEnabled;
    private final boolean datafeedKeyAuthenticationEnabled;
    private final boolean authenticationRequired;

    public ReceiveDataConfig() {
        receiptPolicyUuid = null;
        metaTypes = new HashSet<>(StreamTypeNames.ALL_HARD_CODED_STREAM_TYPE_NAMES);
        tokenAuthenticationEnabled = false;
        certificateAuthenticationEnabled = true;
        datafeedKeyAuthenticationEnabled = false;
        authenticationRequired = true;
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public ReceiveDataConfig(
            @JsonProperty("receiptPolicyUuid") final String receiptPolicyUuid,
            @JsonProperty("metaTypes") final Set<String> metaTypes,
            @JsonProperty("tokenAuthenticationEnabled") final boolean tokenAuthenticationEnabled,
            @JsonProperty("certificateAuthenticationEnabled") final boolean certificateAuthenticationEnabled,
            @JsonProperty("datafeedKeyAuthenticationEnabled") final boolean datafeedKeyAuthenticationEnabled,
            @JsonProperty("authenticationRequired") final boolean authenticationRequired) {

        this.receiptPolicyUuid = receiptPolicyUuid;
        this.metaTypes = metaTypes;
        this.tokenAuthenticationEnabled = tokenAuthenticationEnabled;
        this.certificateAuthenticationEnabled = certificateAuthenticationEnabled;
        this.datafeedKeyAuthenticationEnabled = datafeedKeyAuthenticationEnabled;
        this.authenticationRequired = authenticationRequired;
    }

    private ReceiveDataConfig(final Builder builder) {
        receiptPolicyUuid = builder.receiptPolicyUuid;
        metaTypes = builder.metaTypes;
        tokenAuthenticationEnabled = builder.tokenAuthenticationEnabled;
        certificateAuthenticationEnabled = builder.certificateAuthenticationEnabled;
        datafeedKeyAuthenticationEnabled = builder.datafeedKeyAuthenticationEnabled;
        authenticationRequired = builder.authenticationRequired;
    }


    @JsonPropertyDescription("The UUID of the data receipt policy to use")
    public String getReceiptPolicyUuid() {
        return receiptPolicyUuid;
    }

    @NotNull
    @NotEmpty
    @JsonPropertyDescription("Set of supported meta type names. This set must contain all of the names " +
                             "in the default value for this property but can contain additional names.")
    @IsSupersetOf(requiredValues = {
            StreamTypeNames.RAW_EVENTS,
            StreamTypeNames.RAW_REFERENCE,
            StreamTypeNames.EVENTS,
            StreamTypeNames.REFERENCE,
            StreamTypeNames.META,
            StreamTypeNames.ERROR,
            StreamTypeNames.CONTEXT,
    }) // List should contain, as a minimum. all those types that the java code reference
    public Set<String> getMetaTypes() {
        return metaTypes;
    }

    @JsonPropertyDescription("If true, the data receipt request headers will be checked for the presence of an " +
                             "Open ID access token. This token will be used to authenticate the sender.")
    public boolean isTokenAuthenticationEnabled() {
        return tokenAuthenticationEnabled;
    }

    @JsonPropertyDescription("If true, the data receipt request will be checked for the presence of a " +
                             "certificate. The certificate will be used to authenticate the sender.")
    public boolean isCertificateAuthenticationEnabled() {
        return certificateAuthenticationEnabled;
    }

    @JsonPropertyDescription(
            "If true, the sender will be authenticated using a certificate or token depending on the " +
            "state of tokenAuthenticationEnabled and certificateAuthenticationEnabled. If the sender " +
            "can't be authenticated an error will be returned to the client." +
            "If false, then no authentication will be performed and data will be accepted without a sender identity.")
    public boolean isAuthenticationRequired() {
        return authenticationRequired;
    }

    @JsonPropertyDescription("If true, the data receipt request will be checked for the presence of a datafeed key. " +
                             "If present this key will be checked against the configured list of valid datafeed keys.")
    public boolean isDatafeedKeyAuthenticationEnabled() {
        return datafeedKeyAuthenticationEnabled;
    }

    @SuppressWarnings("unused")
    @JsonIgnore
    @ValidationMethod(message = "If authenticationRequired is true, then one of tokenAuthenticationEnabled " +
                                "or certificateAuthenticationEnabled must also be set to true.")
    public boolean isAuthenticationRequiredValid() {
        return !authenticationRequired
               || (tokenAuthenticationEnabled
                   || certificateAuthenticationEnabled
                   || datafeedKeyAuthenticationEnabled);
    }

    public ReceiveDataConfig withTokenAuthenticationEnabled(final boolean isTokenAuthenticationEnabled) {
        return new ReceiveDataConfig(
                receiptPolicyUuid,
                metaTypes,
                isTokenAuthenticationEnabled,
                certificateAuthenticationEnabled,
                datafeedKeyAuthenticationEnabled, authenticationRequired
        );
    }

    public ReceiveDataConfig withCertificateAuthenticationEnabled(final boolean isCertificateAuthenticationEnabled) {
        return new ReceiveDataConfig(
                receiptPolicyUuid,
                metaTypes,
                tokenAuthenticationEnabled,
                isCertificateAuthenticationEnabled,
                datafeedKeyAuthenticationEnabled, authenticationRequired
        );
    }

    public ReceiveDataConfig withDatafeedKeyAuthenticationEnabled(final boolean isDatafeedKeyAuthenticationEnabled) {
        return new ReceiveDataConfig(
                receiptPolicyUuid,
                metaTypes,
                datafeedKeyAuthenticationEnabled,
                certificateAuthenticationEnabled,
                isDatafeedKeyAuthenticationEnabled, authenticationRequired
        );
    }

    public ReceiveDataConfig withAuthenticationRequired(final boolean isAuthenticationRequired) {
        return new ReceiveDataConfig(
                receiptPolicyUuid,
                metaTypes,
                tokenAuthenticationEnabled,
                certificateAuthenticationEnabled,
                datafeedKeyAuthenticationEnabled, isAuthenticationRequired
        );
    }

    @Override
    public String toString() {
        return "ReceiveDataConfig{" +
               "receiptPolicyUuid='" + receiptPolicyUuid + '\'' +
               ", tokenAuthenticationEnabled=" + tokenAuthenticationEnabled +
               ", certificateAuthenticationEnabled=" + certificateAuthenticationEnabled +
               ", datafeedKeyAuthenticationEnabled=" + datafeedKeyAuthenticationEnabled +
               ", authenticationRequired=" + authenticationRequired +
               '}';
    }

    public static Builder copy(final ReceiveDataConfig receiveDataConfig) {
        Builder builder = new Builder();
        builder.receiptPolicyUuid = receiveDataConfig.getReceiptPolicyUuid();
        builder.metaTypes = receiveDataConfig.getMetaTypes();
        builder.tokenAuthenticationEnabled = receiveDataConfig.isTokenAuthenticationEnabled();
        builder.certificateAuthenticationEnabled = receiveDataConfig.isCertificateAuthenticationEnabled();
        builder.datafeedKeyAuthenticationEnabled = receiveDataConfig.isDatafeedKeyAuthenticationEnabled();
        builder.authenticationRequired = receiveDataConfig.isAuthenticationRequired();
        return builder;
    }

    public static Builder builder() {
        return copy(new ReceiveDataConfig());
    }


    // --------------------------------------------------------------------------------


    public static final class Builder {

        private String receiptPolicyUuid;
        private Set<String> metaTypes;
        private boolean tokenAuthenticationEnabled;
        private boolean certificateAuthenticationEnabled;
        private boolean authenticationRequired;
        private boolean datafeedKeyAuthenticationEnabled;

        private Builder() {
        }

        public static Builder builder() {
            return new Builder();
        }

        public Builder withReceiptPolicyUuid(final String val) {
            receiptPolicyUuid = val;
            return this;
        }

        public Builder withMetaTypes(final Set<String> val) {
            metaTypes = val;
            return this;
        }

        public Builder withTokenAuthenticationEnabled(final boolean val) {
            tokenAuthenticationEnabled = val;
            return this;
        }

        public Builder withCertificateAuthenticationEnabled(final boolean val) {
            certificateAuthenticationEnabled = val;
            return this;
        }

        public Builder withAuthenticationRequired(final boolean val) {
            authenticationRequired = val;
            return this;
        }

        public Builder withDatafeedKeyAuthenticationEnabled(final boolean val) {
            datafeedKeyAuthenticationEnabled = val;
            return this;
        }

        public ReceiveDataConfig build() {
            return new ReceiveDataConfig(this);
        }
    }
}
