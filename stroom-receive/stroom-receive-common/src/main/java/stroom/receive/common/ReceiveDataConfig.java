package stroom.receive.common;

import stroom.data.shared.StreamTypeNames;
import stroom.meta.api.StandardHeaderArguments;
import stroom.util.cache.CacheConfig;
import stroom.util.cert.CertVerificationConfig;
import stroom.util.cert.DNFormat;
import stroom.util.cert.EncodingType;
import stroom.util.collections.CollectionUtil;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.shared.NullSafe;
import stroom.util.shared.validation.IsSupersetOf;
import stroom.util.shared.validation.ValidDirectoryPath;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.dropwizard.validation.ValidationMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;


@JsonPropertyOrder(alphabetic = true)
public class ReceiveDataConfig
        extends AbstractConfig
        implements IsStroomConfig, IsProxyConfig {

    public static final String DEFAULT_X509_CERT_HEADER = "X-SSL-CERT";
    public static final EncodingType DEFAULT_X509_CERT_ENCODING = EncodingType.URL_ENCODED;
    public static final String DEFAULT_X509_CERT_DN_HEADER = "X-SSL-CLIENT-S-DN";
    public static final DNFormat DEFAULT_X509_CERT_DN_FORMAT = DNFormat.LDAP;
    public static final String PROP_NAME_ALLOWED_CERTIFICATE_PROVIDERS = "allowedCertificateProviders";
    public static final String DEFAULT_OWNER_META_KEY = StandardHeaderArguments.ACCOUNT_ID;
    public static final boolean DEFAULT_VALIDATE_CLIENT_CERTIFICATE_EXPIRY = true;
    public static final String DEFAULT_TRUS_TSTORE_TYPE = "JKS";

    @JsonProperty
    private final String receiptPolicyUuid;
    @JsonProperty
    private final Set<String> metaTypes;
    @JsonProperty
    private final boolean authenticationRequired;
    @JsonProperty
    private final String dataFeedKeysDir;
    @JsonProperty
    private final String dataFeedKeyOwnerMetaKey;
    @JsonProperty
    private final CacheConfig authenticatedDataFeedKeyCache;
    @JsonProperty
    private final Set<AuthenticationType> enabledAuthenticationTypes;
    @JsonProperty
    private final String x509CertificateHeader;
    @JsonProperty
    private final EncodingType x509CertificateEncoding;
    @JsonProperty
    private final CertVerificationConfig certVerificationConfig;
    @JsonProperty
    private final String x509CertificateDnHeader;
    @JsonProperty
    private final DNFormat x509CertificateDnFormat;
    @JsonProperty
    private final Set<String> allowedCertificateProviders;
    @JsonProperty
    private final boolean feedNameGenerationEnabled;
    @JsonProperty
    private final String feedNameTemplate;
    @JsonProperty
    private final Set<String> feedNameGenerationMandatoryHeaders;

    public ReceiveDataConfig() {
        receiptPolicyUuid = null;
        // Sort them to ensure consistent order on serialisation
        metaTypes = CollectionUtil.asUnmodifiabledConsistentOrderSet(StreamTypeNames.ALL_HARD_CODED_STREAM_TYPE_NAMES);
        enabledAuthenticationTypes = EnumSet.of(AuthenticationType.CERTIFICATE);
        authenticationRequired = true;
        dataFeedKeysDir = "data_feed_keys";
        dataFeedKeyOwnerMetaKey = DEFAULT_OWNER_META_KEY;
        authenticatedDataFeedKeyCache = CacheConfig.builder()
                .maximumSize(1000L)
                .expireAfterWrite(StroomDuration.ofMinutes(5))
                .statisticsMode(CacheConfig.PROXY_DEFAULT_STATISTICS_MODE) // Used by stroom & proxy so need DW metrics
                .build();
        x509CertificateHeader = DEFAULT_X509_CERT_HEADER;
        x509CertificateEncoding = DEFAULT_X509_CERT_ENCODING;
        certVerificationConfig = new CertVerificationConfig();
        x509CertificateDnHeader = DEFAULT_X509_CERT_DN_HEADER;
        x509CertificateDnFormat = DEFAULT_X509_CERT_DN_FORMAT;
        allowedCertificateProviders = Collections.emptySet();
        feedNameGenerationEnabled = false;
        feedNameTemplate = toTemplate(
                StandardHeaderArguments.ACCOUNT_ID,
                StandardHeaderArguments.COMPONENT,
                StandardHeaderArguments.FORMAT,
                StandardHeaderArguments.SCHEMA);

        feedNameGenerationMandatoryHeaders = CollectionUtil.asUnmodifiabledConsistentOrderSet(List.of(
                StandardHeaderArguments.ACCOUNT_ID,
                StandardHeaderArguments.COMPONENT,
                StandardHeaderArguments.FORMAT,
                StandardHeaderArguments.SCHEMA));
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public ReceiveDataConfig(
            @JsonProperty("receiptPolicyUuid") final String receiptPolicyUuid,
            @JsonProperty("metaTypes") final Set<String> metaTypes,
            @JsonProperty("enabledAuthenticationTypes") final Set<AuthenticationType> enabledAuthenticationTypes,
            @JsonProperty("authenticationRequired") final boolean authenticationRequired,
            @JsonProperty("dataFeedKeysDir") final String dataFeedKeysDir,
            @JsonProperty("dataFeedKeyOwnerMetaKey") final String dataFeedKeyOwnerMetaKey,
            @JsonProperty("authenticatedDataFeedKeyCache") final CacheConfig authenticatedDataFeedKeyCache,
            @JsonProperty("x509CertificateHeader") final String x509CertificateHeader,
            @JsonProperty("x509CertificateEncoding") final EncodingType x509CertificateEncoding,
            @JsonProperty("certVerificationConfig") final CertVerificationConfig certVerificationConfig,
            @JsonProperty("x509CertificateDnHeader") final String x509CertificateDnHeader,
            @JsonProperty("x509CertificateDnFormat") final DNFormat x509CertificateDnFormat,
            @JsonProperty(PROP_NAME_ALLOWED_CERTIFICATE_PROVIDERS) final Set<String> allowedCertificateProviders,
            @JsonProperty("feedNameGenerationEnabled") final boolean feedNameGenerationEnabled,
            @JsonProperty("feedNameTemplate") final String feedNameTemplate,
            @JsonProperty("feedNameGenerationMandatoryHeaders") final Set<String> feedNameGenerationMandatoryHeaders) {

        this.receiptPolicyUuid = receiptPolicyUuid;
        this.metaTypes = cleanSet(metaTypes);
        this.enabledAuthenticationTypes = NullSafe.enumSet(AuthenticationType.class, enabledAuthenticationTypes);
        this.authenticationRequired = authenticationRequired;
        this.dataFeedKeysDir = dataFeedKeysDir;
        this.dataFeedKeyOwnerMetaKey = Objects.requireNonNullElse(dataFeedKeyOwnerMetaKey, DEFAULT_OWNER_META_KEY);
        this.authenticatedDataFeedKeyCache = authenticatedDataFeedKeyCache;
        this.x509CertificateHeader = x509CertificateHeader;
        this.x509CertificateEncoding = Objects.requireNonNullElse(x509CertificateEncoding, DEFAULT_X509_CERT_ENCODING);
        this.certVerificationConfig = Objects.requireNonNullElseGet(
                certVerificationConfig, CertVerificationConfig::new);
        this.x509CertificateDnHeader = x509CertificateDnHeader;
        this.x509CertificateDnFormat = Objects.requireNonNullElse(x509CertificateDnFormat, DEFAULT_X509_CERT_DN_FORMAT);
        this.allowedCertificateProviders = cleanSet(allowedCertificateProviders);
        this.feedNameGenerationEnabled = feedNameGenerationEnabled;
        this.feedNameTemplate = feedNameTemplate;
        this.feedNameGenerationMandatoryHeaders = cleanSet(feedNameGenerationMandatoryHeaders);
    }

    private ReceiveDataConfig(final Builder builder) {
        this(
                builder.receiptPolicyUuid,
                builder.metaTypes,
                builder.enabledAuthenticationTypes,
                builder.authenticationRequired,
                builder.dataFeedKeysDir,
                builder.dataFeedKeyOwnerMetaKey,
                builder.authenticatedDataFeedKeyCache,
                builder.x509CertificateHeader,
                builder.x509CertificateEncoding,
                builder.certVerificationConfig,
                builder.x509CertificateDnHeader,
                builder.x509CertificateDnFormat,
                builder.allowedCertificateProviders,
                builder.feedNameGenerationEnabled,
                builder.feedNameTemplate,
                builder.feedNameGenerationMandatoryHeaders);
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

    @JsonPropertyDescription(
            "If true, the sender will be authenticated using a certificate or token depending on the " +
            "state of tokenAuthenticationEnabled and certificateAuthenticationEnabled. If the sender " +
            "can't be authenticated an error will be returned to the client." +
            "If false, then authentication will be performed if a token/key/certificate " +
            "is present, otherwise data will be accepted without a sender identity.")
    public boolean isAuthenticationRequired() {
        return authenticationRequired;
    }

    @ValidDirectoryPath(ensureExistence = true)
    @JsonPropertyDescription("The directory where Stroom will look for datafeed key files. " +
                             "Only used if datafeedKeyAuthenticationEnabled is true." +
                             "If the value is a relative path then it will be treated as being " +
                             "relative to stroom.path.home. " +
                             "Data feed key files must have the extension .json. Files in sub-directories" +
                             "will be ignored.")
    public String getDataFeedKeysDir() {
        return dataFeedKeysDir;
    }

    @NotBlank
    @JsonPropertyDescription("The meta key that is used to identify the owner of a Data Feed Key. This " +
                             "may be an AccountId or similar. It must be provided as a header when sending data " +
                             "using the associated Data Feed Key, and its value will be checked against the value " +
                             "held with the hashed Data Feed Key by Stroom. Default value is 'AccountId'. " +
                             "Case does not matter.")
    public String getDataFeedKeyOwnerMetaKey() {
        return dataFeedKeyOwnerMetaKey;
    }

    @NotNull
    public CacheConfig getAuthenticatedDataFeedKeyCache() {
        return authenticatedDataFeedKeyCache;
    }

    @JsonPropertyDescription(
            "The HTTP header key used to extract an X509 certificate. This is used when a load balancer does the " +
            "SSL/mTLS termination and passes the client certificate though in a header. Only used for " +
            "authentication if a value is set and 'enabledAuthenticationTypes' includes CERTIFICATE.")
    public String getX509CertificateHeader() {
        return x509CertificateHeader;
    }

    @JsonPropertyDescription("The encoding used to encode the X509 certificate in the HTTP header.")
    public EncodingType getX509CertificateEncoding() {
        return x509CertificateEncoding;
    }

    public CertVerificationConfig getCertVerificationConfig() {
        return certVerificationConfig;
    }

    @JsonPropertyDescription(
            "The HTTP header key used to extract the distinguished name (DN) as obtained from an X509 certificate. " +
            "This is used when a load balancer does the SSL/mTLS termination and passes the client DN though " +
            "in a header. Only used for " +
            "authentication if a value is set and 'enabledAuthenticationTypes' includes CERTIFICATE.")
    public String getX509CertificateDnHeader() {
        return x509CertificateDnHeader;
    }

    @JsonPropertyDescription("The format of the Distinguished Name used in the certificate. Valid values are " +
                             "LDAP and OPEN_SSL, where LDAP is the default.")
    public DNFormat getX509CertificateDnFormat() {
        return x509CertificateDnFormat;
    }

    @JsonPropertyDescription(
            "An allow-list containing IP addresses or fully qualified host names to verify that the direct sender " +
            "of a request (e.g. a load balancer or reverse proxy) is trusted to supply certificate/DN headers " +
            "as configured with 'x509CertificateHeader' and 'x509CertificateDnHeader'. " +
            "If this list is null/empty then no check will be made on the client's " +
            "address.")
    public Set<String> getAllowedCertificateProviders() {
        return allowedCertificateProviders;
    }

    @JsonPropertyDescription("If true the client is not required to set the 'Feed' header. If Feed is not present " +
                             "a feed name will be generated based on the template specified by the " +
                             "'feedNameTemplate' property. If false (the default), a populated 'Feed' " +
                             "header will be required.")
    public boolean isFeedNameGenerationEnabled() {
        return feedNameGenerationEnabled;
    }

    @JsonPropertyDescription("A template for generating a feed name from a set of headers. The value of " +
                             "each header referenced in the template will have any unsuitable characters " +
                             "replaced with '_'.")
    public String getFeedNameTemplate() {
        return feedNameTemplate;
    }

    @JsonPropertyDescription("The set of header keys are mandatory if feedNameGenerationEnabled is set to true. " +
                             "Should be set to complement the header keys used in 'feedNameTemplate', but may be a " +
                             "sub-set of those in the template to allow for optional headers.")
    public Set<String> getFeedNameGenerationMandatoryHeaders() {
        return feedNameGenerationMandatoryHeaders;
    }

    @SuppressWarnings("unused")
    @JsonIgnore
    @ValidationMethod(message = "If authenticationRequired is true, then enabledAuthenticationTypes must " +
                                "contain at least one authentication type.")
    public boolean isAuthenticationRequiredValid() {
        return !authenticationRequired
               || !enabledAuthenticationTypes.isEmpty();
    }

    private static String toTemplate(final String... parts) {
        return NullSafe.stream(parts)
                .map(part -> "${" + part.toLowerCase() + "}")
                .collect(Collectors.joining("-"));
    }

    @Override
    public String toString() {
        return "ReceiveDataConfig{" +
               "receiptPolicyUuid='" + receiptPolicyUuid + '\'' +
               ", metaTypes=" + metaTypes +
               ", authenticationRequired=" + authenticationRequired +
               ", dataFeedKeysDir='" + dataFeedKeysDir + '\'' +
               ", authenticatedDataFeedKeyCache=" + authenticatedDataFeedKeyCache +
               ", enabledAuthenticationTypes=" + enabledAuthenticationTypes +
               ", x509CertificateHeader='" + x509CertificateHeader + '\'' +
               ", x509CertificateDnHeader='" + x509CertificateDnHeader + '\'' +
               ", allowedCertificateProviders=" + allowedCertificateProviders +
               ", feedNameGenerationEnabled=" + feedNameGenerationEnabled +
               ", feedNameTemplate='" + feedNameTemplate + '\'' +
               ", feedNameGenerationMandatoryHeaders=" + feedNameGenerationMandatoryHeaders +
               '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ReceiveDataConfig that = (ReceiveDataConfig) o;
        return authenticationRequired == that.authenticationRequired
               && feedNameGenerationEnabled == that.feedNameGenerationEnabled
               && Objects.equals(receiptPolicyUuid, that.receiptPolicyUuid)
               && Objects.equals(metaTypes, that.metaTypes)
               && Objects.equals(dataFeedKeysDir, that.dataFeedKeysDir)
               && Objects.equals(authenticatedDataFeedKeyCache, that.authenticatedDataFeedKeyCache)
               && Objects.equals(enabledAuthenticationTypes, that.enabledAuthenticationTypes)
               && Objects.equals(x509CertificateHeader, that.x509CertificateHeader)
               && Objects.equals(x509CertificateDnHeader, that.x509CertificateDnHeader)
               && Objects.equals(allowedCertificateProviders, that.allowedCertificateProviders)
               && Objects.equals(feedNameTemplate, that.feedNameTemplate)
               && Objects.equals(feedNameGenerationMandatoryHeaders, that.feedNameGenerationMandatoryHeaders);
    }

    @Override
    public int hashCode() {
        return Objects.hash(receiptPolicyUuid,
                metaTypes,
                authenticationRequired,
                dataFeedKeysDir,
                authenticatedDataFeedKeyCache,
                enabledAuthenticationTypes,
                x509CertificateHeader,
                x509CertificateDnHeader,
                allowedCertificateProviders,
                feedNameGenerationEnabled,
                feedNameTemplate,
                feedNameGenerationMandatoryHeaders);
    }

    public static Builder copy(final ReceiveDataConfig receiveDataConfig) {
        final Builder builder = new Builder();
        builder.receiptPolicyUuid = receiveDataConfig.getReceiptPolicyUuid();
        builder.metaTypes = receiveDataConfig.getMetaTypes();
        builder.enabledAuthenticationTypes = receiveDataConfig.getEnabledAuthenticationTypes();
        builder.authenticationRequired = receiveDataConfig.isAuthenticationRequired();
        builder.dataFeedKeysDir = receiveDataConfig.getDataFeedKeysDir();
        builder.authenticatedDataFeedKeyCache = receiveDataConfig.getAuthenticatedDataFeedKeyCache();
        builder.x509CertificateHeader = receiveDataConfig.getX509CertificateHeader();
        builder.x509CertificateDnHeader = receiveDataConfig.getX509CertificateDnHeader();
        builder.x509CertificateDnFormat = receiveDataConfig.getX509CertificateDnFormat();
        builder.allowedCertificateProviders = receiveDataConfig.getAllowedCertificateProviders();
        builder.feedNameGenerationEnabled = receiveDataConfig.isFeedNameGenerationEnabled();
        builder.feedNameTemplate = receiveDataConfig.getFeedNameTemplate();
        builder.feedNameGenerationMandatoryHeaders = receiveDataConfig.getFeedNameGenerationMandatoryHeaders();
        return builder;
    }


    public static Builder builder() {
        return copy(new ReceiveDataConfig());
    }

    private static Set<String> cleanSet(final Set<String> set) {
        return CollectionUtil.cleanItems(set, String::trim);
    }


    // --------------------------------------------------------------------------------


    public static final class Builder {

        private CertVerificationConfig certVerificationConfig;
        private EncodingType x509CertificateEncoding;
        private String receiptPolicyUuid;
        private Set<String> metaTypes;
        private Set<AuthenticationType> enabledAuthenticationTypes = EnumSet.noneOf(AuthenticationType.class);
        private boolean authenticationRequired;
        private String dataFeedKeysDir;
        private String dataFeedKeyOwnerMetaKey;
        private CacheConfig authenticatedDataFeedKeyCache;
        private String x509CertificateHeader;
        private String x509CertificateDnHeader;
        private DNFormat x509CertificateDnFormat;
        private Set<String> allowedCertificateProviders;
        private boolean feedNameGenerationEnabled;
        private String feedNameTemplate;
        private Set<String> feedNameGenerationMandatoryHeaders;

        private Builder() {
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

        public Builder withDataFeedKeyOwnerMetaKey(final String val) {
            dataFeedKeyOwnerMetaKey = val;
            return this;
        }

        public Builder withAuthenticatedDataFeedKeyCache(final CacheConfig val) {
            authenticatedDataFeedKeyCache = val;
            return this;
        }

        public Builder withX509CertificateHeader(final String val) {
            x509CertificateHeader = val;
            return this;
        }

        public Builder withX509CertificateEncoding(final EncodingType val) {
            x509CertificateEncoding = val;
            return this;
        }

        public Builder withCertVerificationConfig(final CertVerificationConfig val) {
            certVerificationConfig = val;
            return this;
        }

        public Builder withX509CertificateDnHeader(final String val) {
            x509CertificateDnHeader = val;
            return this;
        }

        public Builder withX509CertificateDnFormat(final DNFormat val) {
            x509CertificateDnFormat = val;
            return this;
        }

        public Builder withAllowedCertificateProviders(final Set<String> val) {
            allowedCertificateProviders = val;
            return this;
        }

        public Builder withFeedNameGenerationEnabled(final boolean isEnabled) {
            this.feedNameGenerationEnabled = isEnabled;
            return this;
        }

        public Builder withFeedNameTemplate(final String feedNameTemplate) {
            this.feedNameTemplate = feedNameTemplate;
            return this;
        }

        public Builder withFeedNameGenerationMandatoryHeaders(final Set<String> feedNameGenerationMandatoryHeaders) {
            this.feedNameGenerationMandatoryHeaders = NullSafe.mutableSet(feedNameGenerationMandatoryHeaders);
            return this;
        }

        public ReceiveDataConfig build() {
            return new ReceiveDataConfig(this);
        }
    }


    // --------------------------------------------------------------------------------


}
