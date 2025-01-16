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
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;


@JsonPropertyOrder(alphabetic = true)
public class ReceiveDataConfig
        extends AbstractConfig
        implements IsStroomConfig, IsProxyConfig {

    @JsonProperty
    private final String receiptPolicyUuid;
    @JsonProperty
    private final Set<String> metaTypes;
    @JsonProperty
    private final boolean authenticationRequired;
    @JsonProperty
    private final String dataFeedKeysDir;
    @JsonProperty
    private final Set<AuthenticationType> enabledAuthenticationTypes;
    @JsonProperty
    private final AutoContentCreationConfig autoContentCreation;

    public ReceiveDataConfig() {
        receiptPolicyUuid = null;
        metaTypes = new HashSet<>(StreamTypeNames.ALL_HARD_CODED_STREAM_TYPE_NAMES);
        enabledAuthenticationTypes = EnumSet.of(AuthenticationType.CERT);
        authenticationRequired = true;
        dataFeedKeysDir = "data_feed_keys";
        autoContentCreation = new AutoContentCreationConfig();
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public ReceiveDataConfig(
            @JsonProperty("receiptPolicyUuid") final String receiptPolicyUuid,
            @JsonProperty("metaTypes") final Set<String> metaTypes,
            @JsonProperty("enabledAuthenticationTypes") final Set<AuthenticationType> enabledAuthenticationTypes,
            @JsonProperty("authenticationRequired") final boolean authenticationRequired,
            @JsonProperty("dataFeedKeysDir") final String dataFeedKeysDir,
            @JsonProperty("autoContentCreation") final AutoContentCreationConfig autoContentCreation) {

        this.receiptPolicyUuid = receiptPolicyUuid;
        this.metaTypes = metaTypes;
        this.enabledAuthenticationTypes = NullSafe.enumSet(AuthenticationType.class, enabledAuthenticationTypes);
        this.authenticationRequired = authenticationRequired;
        this.dataFeedKeysDir = dataFeedKeysDir;
        this.autoContentCreation = autoContentCreation;
    }

    private ReceiveDataConfig(final Builder builder) {
        receiptPolicyUuid = builder.receiptPolicyUuid;
        metaTypes = builder.metaTypes;
        enabledAuthenticationTypes = NullSafe.enumSet(AuthenticationType.class, builder.enabledAuthenticationTypes);
        authenticationRequired = builder.authenticationRequired;
        dataFeedKeysDir = builder.dataFeedKeysDir;
        autoContentCreation = builder.autoContentCreation;
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

    @JsonProperty("autoContentCreation")
    public AutoContentCreationConfig getAutoContentCreationConfig() {
        return autoContentCreation;
    }

    @JsonPropertyDescription(
            "If true, the sender will be authenticated using a certificate or token depending on the " +
                    "state of tokenAuthenticationEnabled and certificateAuthenticationEnabled. If the sender " +
                    "can't be authenticated an error will be returned to the client." +
                    "If false, then authentication will be performed if a token/key/certificate " +
                    "is present, otherwise data will be accepted without a sender identity.")
    public boolean isAuthenticationRequired() {
        return authenticationRequired;
    }

    @JsonPropertyDescription("The directory where Stroom will look for datafeed key files. " +
            "Only used if datafeedKeyAuthenticationEnabled is true." +
            "If the value is a relative path then it will be treated as being " +
            "relative to stroom.path.home. " +
            "Data feed key files must have the extension .json. Files in sub-directories will be ignored.")
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

    @Override
    public String toString() {
        return "ReceiveDataConfig{" +
                "receiptPolicyUuid='" + receiptPolicyUuid + '\'' +
                ", enabledAuthenticationTypes=" + enabledAuthenticationTypes +
                ", authenticationRequired=" + authenticationRequired +
                ", dataFeedKeysDir=" + dataFeedKeysDir +
                '}';
    }

    public static Builder copy(final ReceiveDataConfig receiveDataConfig) {
        Builder builder = new Builder();
        builder.receiptPolicyUuid = receiveDataConfig.getReceiptPolicyUuid();
        builder.metaTypes = receiveDataConfig.getMetaTypes();
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
        private Set<AuthenticationType> enabledAuthenticationTypes = EnumSet.noneOf(AuthenticationType.class);
        private boolean authenticationRequired;
        private String dataFeedKeysDir;
        private AutoContentCreationConfig autoContentCreation;

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

        public Builder withAuthenticationRequired(final boolean val) {
            authenticationRequired = val;
            return this;
        }

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

        public Builder withAuthContentCreation(final AutoContentCreationConfig val) {
            autoContentCreation = val;
            return this;
        }

        public ReceiveDataConfig build() {
            return new ReceiveDataConfig(this);
        }
    }
}
