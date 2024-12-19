package stroom.receive.common;

import stroom.data.shared.StreamTypeNames;
import stroom.util.NullSafe;
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

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;


@JsonPropertyOrder(alphabetic = true)
public class ReceiveDataConfig
        extends AbstractConfig
        implements IsStroomConfig, IsProxyConfig {

    @JsonProperty
    private final String receiptPolicyUuid;
    @JsonProperty
    private final Set<String> metaTypes;
    //    @JsonProperty
//    private final boolean tokenAuthenticationEnabled;
//    @JsonProperty
//    private final boolean certificateAuthenticationEnabled;
//    @JsonProperty
//    private final boolean datafeedKeyAuthenticationEnabled;
    @JsonProperty
    private final boolean authenticationRequired;
    @JsonProperty
    private final String dataFeedKeysDir;
    @JsonProperty
    private final Set<AuthenticationType> enabledAuthenticationTypes;

    public ReceiveDataConfig() {
        receiptPolicyUuid = null;
        metaTypes = new HashSet<>(StreamTypeNames.ALL_HARD_CODED_STREAM_TYPE_NAMES);
//        tokenAuthenticationEnabled = false;
//        certificateAuthenticationEnabled = true;
//        datafeedKeyAuthenticationEnabled = false;
        enabledAuthenticationTypes = EnumSet.of(AuthenticationType.CERT);
        authenticationRequired = true;
        dataFeedKeysDir = "data_feed_keys";
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public ReceiveDataConfig(
            @JsonProperty("receiptPolicyUuid") final String receiptPolicyUuid,
            @JsonProperty("metaTypes") final Set<String> metaTypes,
//            @JsonProperty("tokenAuthenticationEnabled") final boolean tokenAuthenticationEnabled,
//            @JsonProperty("certificateAuthenticationEnabled") final boolean certificateAuthenticationEnabled,
//            @JsonProperty("datafeedKeyAuthenticationEnabled") final boolean datafeedKeyAuthenticationEnabled,
            @JsonProperty("enabledAuthenticationTypes") final Set<AuthenticationType> enabledAuthenticationTypes,
            @JsonProperty("authenticationRequired") final boolean authenticationRequired,
            @JsonProperty("dataFeedKeysDir") final String dataFeedKeysDir) {

        this.receiptPolicyUuid = receiptPolicyUuid;
        this.metaTypes = metaTypes;
//        this.tokenAuthenticationEnabled = tokenAuthenticationEnabled;
//        this.certificateAuthenticationEnabled = certificateAuthenticationEnabled;
//        this.datafeedKeyAuthenticationEnabled = datafeedKeyAuthenticationEnabled;
        this.enabledAuthenticationTypes = NullSafe.enumSet(AuthenticationType.class, enabledAuthenticationTypes);
        this.authenticationRequired = authenticationRequired;
        this.dataFeedKeysDir = dataFeedKeysDir;
    }

    private ReceiveDataConfig(final Builder builder) {
        receiptPolicyUuid = builder.receiptPolicyUuid;
        metaTypes = builder.metaTypes;
//        tokenAuthenticationEnabled = builder.tokenAuthenticationEnabled;
//        certificateAuthenticationEnabled = builder.certificateAuthenticationEnabled;
//        datafeedKeyAuthenticationEnabled = builder.datafeedKeyAuthenticationEnabled;
        enabledAuthenticationTypes = NullSafe.enumSet(AuthenticationType.class, builder.enabledAuthenticationTypes);
        authenticationRequired = builder.authenticationRequired;
        dataFeedKeysDir = builder.dataFeedKeysDir;
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

    @NotNull
    @JsonPropertyDescription("The types of authentication that are enabled for data receipt.")
    public Set<AuthenticationType> getEnabledAuthenticationTypes() {
        return enabledAuthenticationTypes;
    }

    /**
     * @return True if the passed {@link AuthenticationType} has been configured as enabled for use..
     */
    public boolean isAuthenticationTypeEnabled(final AuthenticationType authenticationType) {
        return authenticationType != null
                && enabledAuthenticationTypes.contains(authenticationType);
    }

//    @JsonPropertyDescription("If true, the data receipt request headers will be checked for the presence of an " +
//            "Open ID access token. This token will be used to authenticate the sender.")
//    public boolean isTokenAuthenticationEnabled() {
//        return tokenAuthenticationEnabled;
//    }
//
//    @JsonPropertyDescription("If true, the data receipt request will be checked for the presence of a " +
//            "certificate. The certificate will be used to authenticate the sender.")
//    public boolean isCertificateAuthenticationEnabled() {
//        return certificateAuthenticationEnabled;
//    }

    @JsonPropertyDescription(
            "If true, the sender will be authenticated using a certificate or token depending on the " +
                    "state of tokenAuthenticationEnabled and certificateAuthenticationEnabled. If the sender " +
                    "can't be authenticated an error will be returned to the client." +
                    "If false, then authentication will be performed if a token/key/certificate " +
                    "is present, otherwise data will be accepted without a sender identity.")
    public boolean isAuthenticationRequired() {
        return authenticationRequired;
    }

//    @JsonPropertyDescription("If true, the data receipt request will be checked for
//    the presence of a datafeed key. " +
//            "If present this key will be checked against the configured list of valid datafeed keys.")
//    public boolean isDatafeedKeyAuthenticationEnabled() {
//        return datafeedKeyAuthenticationEnabled;
//    }

    @JsonPropertyDescription("The directory where Stroom will look for datafeed key files. " +
            "Only used if datafeedKeyAuthenticationEnabled is true." +
            "If the value is a relative path then it will be treated as being " +
            "relative to stroom.path.home.")
    public String getDataFeedKeysDir() {
        return dataFeedKeysDir;
    }

    @SuppressWarnings("unused")
    @JsonIgnore
    @ValidationMethod(message = "If authenticationRequired is true, then enabledAuthenticationTypes must " +
            "contain at least one authentication type.")
    public boolean isAuthenticationRequiredValid() {
        return !authenticationRequired
                || !enabledAuthenticationTypes.isEmpty();
    }

//    public ReceiveDataConfig withTokenAuthenticationEnabled(final boolean isTokenAuthenticationEnabled) {
//        return new ReceiveDataConfig(
//                receiptPolicyUuid,
//                metaTypes,
//                isTokenAuthenticationEnabled,
//                certificateAuthenticationEnabled,
//                datafeedKeyAuthenticationEnabled,
//                authenticationRequired,
//                dataFeedKeysDir);
//    }
//
//    public ReceiveDataConfig withCertificateAuthenticationEnabled(final boolean isCertificateAuthenticationEnabled) {
//        return new ReceiveDataConfig(
//                receiptPolicyUuid,
//                metaTypes,
//                tokenAuthenticationEnabled,
//                isCertificateAuthenticationEnabled,
//                datafeedKeyAuthenticationEnabled,
//                authenticationRequired,
//                dataFeedKeysDir);
//    }
//
//    public ReceiveDataConfig withDatafeedKeyAuthenticationEnabled(final boolean isDatafeedKeyAuthenticationEnabled) {
//        return new ReceiveDataConfig(
//                receiptPolicyUuid,
//                metaTypes,
//                datafeedKeyAuthenticationEnabled,
//                certificateAuthenticationEnabled,
//                isDatafeedKeyAuthenticationEnabled,
//                authenticationRequired,
//                dataFeedKeysDir);
//    }
//
//    public ReceiveDataConfig withAuthenticationRequired(final boolean isAuthenticationRequired) {
//        return new ReceiveDataConfig(
//                receiptPolicyUuid,
//                metaTypes,
//                tokenAuthenticationEnabled,
//                certificateAuthenticationEnabled,
//                datafeedKeyAuthenticationEnabled,
//                isAuthenticationRequired,
//                dataFeedKeysDir);
//    }

    @Override
    public String toString() {
        return "ReceiveDataConfig{" +
                "receiptPolicyUuid='" + receiptPolicyUuid + '\'' +
//                ", tokenAuthenticationEnabled=" + tokenAuthenticationEnabled +
//                ", certificateAuthenticationEnabled=" + certificateAuthenticationEnabled +
//                ", datafeedKeyAuthenticationEnabled=" + datafeedKeyAuthenticationEnabled +
                ", enabledAuthenticationTypes=" + enabledAuthenticationTypes +
                ", authenticationRequired=" + authenticationRequired +
                ", dataFeedKeysDir=" + dataFeedKeysDir +
                '}';
    }

    public static Builder copy(final ReceiveDataConfig receiveDataConfig) {
        Builder builder = new Builder();
        builder.receiptPolicyUuid = receiveDataConfig.getReceiptPolicyUuid();
        builder.metaTypes = receiveDataConfig.getMetaTypes();
//        builder.tokenAuthenticationEnabled = receiveDataConfig.isTokenAuthenticationEnabled();
//        builder.certificateAuthenticationEnabled = receiveDataConfig.isCertificateAuthenticationEnabled();
//        builder.datafeedKeyAuthenticationEnabled = receiveDataConfig.isDatafeedKeyAuthenticationEnabled();
        builder.enabledAuthenticationTypes = receiveDataConfig.getEnabledAuthenticationTypes();
        builder.authenticationRequired = receiveDataConfig.isAuthenticationRequired();
        builder.dataFeedKeysDir = receiveDataConfig.getDataFeedKeysDir();
        return builder;
    }

    public static Builder builder() {
        return copy(new ReceiveDataConfig());
    }


    // --------------------------------------------------------------------------------


    public static final class Builder {

        private String receiptPolicyUuid;
        private Set<String> metaTypes;
        //        private boolean tokenAuthenticationEnabled;
//        private boolean certificateAuthenticationEnabled;
        private Set<AuthenticationType> enabledAuthenticationTypes = EnumSet.noneOf(AuthenticationType.class);
        private boolean authenticationRequired;
        //        private boolean datafeedKeyAuthenticationEnabled;
        private String dataFeedKeysDir;

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

//        public Builder withTokenAuthenticationEnabled(final boolean val) {
//            tokenAuthenticationEnabled = val;
//            return this;
//        }
//
//        public Builder withCertificateAuthenticationEnabled(final boolean val) {
//            certificateAuthenticationEnabled = val;
//            return this;
//        }

        public Builder withAuthenticationRequired(final boolean val) {
            authenticationRequired = val;
            return this;
        }

        //        public Builder withDatafeedKeyAuthenticationEnabled(final boolean val) {
//            datafeedKeyAuthenticationEnabled = val;
//            return this;
//        }
        public Builder withEnabledAuthenticationTypes(final Set<AuthenticationType> val) {
            enabledAuthenticationTypes = NullSafe.enumSet(AuthenticationType.class, val);
            return this;
        }

        public Builder withEnabledAuthenticationTypes(final AuthenticationType... values) {
            enabledAuthenticationTypes = NullSafe.enumSetOf(AuthenticationType.class, values);
            return this;
        }

        public Builder addEnabledAuthenticationType(final AuthenticationType val) {
            if (val != null) {
                if (enabledAuthenticationTypes == null) {
                    enabledAuthenticationTypes = NullSafe.enumSetOf(AuthenticationType.class, val);
                } else {
                    enabledAuthenticationTypes.add(val);
                }
            }
            return this;
        }

        public Builder withDataFeedKeysDir(final String val) {
            dataFeedKeysDir = val;
            return this;
        }

        public ReceiveDataConfig build() {
            return new ReceiveDataConfig(this);
        }
    }
}
