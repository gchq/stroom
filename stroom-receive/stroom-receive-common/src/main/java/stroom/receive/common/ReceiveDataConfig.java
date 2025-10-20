package stroom.receive.common;

import stroom.data.shared.StreamTypeNames;
import stroom.meta.api.StandardHeaderArguments;
import stroom.receive.rules.shared.ReceiptCheckMode;
import stroom.receive.rules.shared.ReceiveAction;
import stroom.util.cache.CacheConfig;
import stroom.util.cert.DNFormat;
import stroom.util.collections.CollectionUtil;
import stroom.util.io.ByteSize;
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


/**
 * This config is common to both stroom and proxy, so should contain only those
 * properties that are used in both.
 */
@JsonPropertyOrder(alphabetic = true)
public class ReceiveDataConfig
        extends AbstractConfig
        implements IsStroomConfig, IsProxyConfig {

    public static final String DEFAULT_X509_CERT_HEADER = "X-SSL-CERT";
    public static final String DEFAULT_X509_CERT_DN_HEADER = "X-SSL-CLIENT-S-DN";
    public static final DNFormat DEFAULT_X509_CERT_DN_FORMAT = DNFormat.LDAP;
    public static final String PROP_NAME_ALLOWED_CERTIFICATE_PROVIDERS = "allowedCertificateProviders";
    public static final String DEFAULT_OWNER_META_KEY = StandardHeaderArguments.ACCOUNT_ID;

    public static final boolean DEFAULT_AUTHENTICATION_REQUIRED = true;
    public static final String DEFAULT_DATA_FEED_KEYS_DIR = "data_feed_keys";
    public static final boolean DEFAULT_FEED_NAME_GENERATION_ENABLED = false;

    public static final String DEFAULT_FEED_NAME_TEMPLATE = toTemplate(
            StandardHeaderArguments.ACCOUNT_ID,
            StandardHeaderArguments.COMPONENT,
            StandardHeaderArguments.FORMAT,
            StandardHeaderArguments.SCHEMA);

    public static final Set<String> DEFAULT_FEED_NAME_MANDATORY_HEADERS =
            CollectionUtil.asUnmodifiabledConsistentOrderSet(List.of(
                    StandardHeaderArguments.ACCOUNT_ID,
                    StandardHeaderArguments.COMPONENT,
                    StandardHeaderArguments.FORMAT,
                    StandardHeaderArguments.SCHEMA));

    public static final Set<String> DEFAULT_META_TYPES = CollectionUtil.asUnmodifiabledConsistentOrderSet(
            StreamTypeNames.ALL_HARD_CODED_STREAM_TYPE_NAMES);

    public static final Set<AuthenticationType> DEFAULT_AUTH_TYPES = Collections.unmodifiableSet(
            EnumSet.of(AuthenticationType.CERTIFICATE));

    public static final ReceiptCheckMode DEFAULT_RECEIPT_CHECK_MODE = ReceiptCheckMode.getDefault();
    // If we can't hit the downstream then we have to let everything in
    public static final ReceiveAction DEFAULT_FALLBACK_RECEIVE_ACTION = ReceiveAction.RECEIVE;

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
    @JsonProperty
    private final ReceiptCheckMode receiptCheckMode;
    @JsonProperty
    private final ReceiveAction fallbackReceiveAction;
    @JsonProperty
    private final ByteSize maxRequestSize;

    public ReceiveDataConfig() {
        // Sort them to ensure consistent order on serialisation
        metaTypes = DEFAULT_META_TYPES;
        enabledAuthenticationTypes = DEFAULT_AUTH_TYPES;
        authenticationRequired = DEFAULT_AUTHENTICATION_REQUIRED;
        dataFeedKeysDir = DEFAULT_DATA_FEED_KEYS_DIR;
        dataFeedKeyOwnerMetaKey = DEFAULT_OWNER_META_KEY;
        authenticatedDataFeedKeyCache = createDefaultDataFeedKeyCacheConfig();
        x509CertificateHeader = DEFAULT_X509_CERT_HEADER;
        x509CertificateDnHeader = DEFAULT_X509_CERT_DN_HEADER;
        x509CertificateDnFormat = DEFAULT_X509_CERT_DN_FORMAT;
        allowedCertificateProviders = Collections.emptySet();
        feedNameGenerationEnabled = DEFAULT_FEED_NAME_GENERATION_ENABLED;
        feedNameTemplate = DEFAULT_FEED_NAME_TEMPLATE;
        feedNameGenerationMandatoryHeaders = DEFAULT_FEED_NAME_MANDATORY_HEADERS;
        receiptCheckMode = DEFAULT_RECEIPT_CHECK_MODE;
        fallbackReceiveAction = DEFAULT_FALLBACK_RECEIVE_ACTION;
        maxRequestSize = null;
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public ReceiveDataConfig(
            @JsonProperty("metaTypes") final Set<String> metaTypes,
            @JsonProperty("enabledAuthenticationTypes") final Set<AuthenticationType> enabledAuthenticationTypes,
            @JsonProperty("authenticationRequired") final Boolean authenticationRequired,
            @JsonProperty("dataFeedKeysDir") final String dataFeedKeysDir,
            @JsonProperty("dataFeedKeyOwnerMetaKey") final String dataFeedKeyOwnerMetaKey,
            @JsonProperty("authenticatedDataFeedKeyCache") final CacheConfig authenticatedDataFeedKeyCache,
            @JsonProperty("x509CertificateHeader") final String x509CertificateHeader,
            @JsonProperty("x509CertificateDnHeader") final String x509CertificateDnHeader,
            @JsonProperty("x509CertificateDnFormat") final DNFormat x509CertificateDnFormat,
            @JsonProperty(PROP_NAME_ALLOWED_CERTIFICATE_PROVIDERS) final Set<String> allowedCertificateProviders,
            @JsonProperty("feedNameGenerationEnabled") final Boolean feedNameGenerationEnabled,
            @JsonProperty("feedNameTemplate") final String feedNameTemplate,
            @JsonProperty("feedNameGenerationMandatoryHeaders") final Set<String> feedNameGenerationMandatoryHeaders,
            @JsonProperty("receiptCheckMode") final ReceiptCheckMode receiptCheckMode,
            @JsonProperty("fallbackReceiveAction") final ReceiveAction fallbackReceiveAction,
            @JsonProperty("maxRequestSize") final ByteSize maxRequestSize) {

        this.metaTypes = NullSafe.getOrElse(metaTypes, ReceiveDataConfig::cleanSet, DEFAULT_META_TYPES);
        this.enabledAuthenticationTypes = NullSafe.getOrElse(
                enabledAuthenticationTypes,
                authTypes -> NullSafe.unmodifialbeEnumSet(AuthenticationType.class, authTypes),
                DEFAULT_AUTH_TYPES);
        this.authenticationRequired = Objects.requireNonNullElse(
                authenticationRequired, DEFAULT_AUTHENTICATION_REQUIRED);
        this.dataFeedKeysDir = NullSafe.nonBlankStringElse(dataFeedKeysDir, DEFAULT_DATA_FEED_KEYS_DIR);
        this.dataFeedKeyOwnerMetaKey = NullSafe.nonBlankStringElse(dataFeedKeyOwnerMetaKey, DEFAULT_OWNER_META_KEY);
        this.authenticatedDataFeedKeyCache = Objects.requireNonNullElseGet(
                authenticatedDataFeedKeyCache, ReceiveDataConfig::createDefaultDataFeedKeyCacheConfig);
        this.x509CertificateHeader = NullSafe.nonBlankStringElse(x509CertificateHeader, DEFAULT_X509_CERT_HEADER);
        this.x509CertificateDnHeader = NullSafe.nonBlankStringElse(
                x509CertificateDnHeader, DEFAULT_X509_CERT_DN_HEADER);
        this.x509CertificateDnFormat = Objects.requireNonNullElse(x509CertificateDnFormat, DEFAULT_X509_CERT_DN_FORMAT);
        this.allowedCertificateProviders = cleanSet(allowedCertificateProviders);
        this.feedNameGenerationEnabled = Objects.requireNonNullElse(
                feedNameGenerationEnabled, DEFAULT_FEED_NAME_GENERATION_ENABLED);
        this.feedNameTemplate = Objects.requireNonNullElse(feedNameTemplate, DEFAULT_FEED_NAME_TEMPLATE);
        this.feedNameGenerationMandatoryHeaders = NullSafe.getOrElse(
                feedNameGenerationMandatoryHeaders,
                ReceiveDataConfig::cleanSet,
                DEFAULT_FEED_NAME_MANDATORY_HEADERS);
        this.receiptCheckMode = Objects.requireNonNullElse(receiptCheckMode, DEFAULT_RECEIPT_CHECK_MODE);
        this.fallbackReceiveAction = Objects.requireNonNullElse(fallbackReceiveAction, DEFAULT_FALLBACK_RECEIVE_ACTION);
        this.maxRequestSize = maxRequestSize;
    }

    private ReceiveDataConfig(final Builder builder) {
        this(
                builder.metaTypes,
                builder.enabledAuthenticationTypes,
                builder.authenticationRequired,
                builder.dataFeedKeysDir,
                builder.dataFeedKeyOwnerMetaKey,
                builder.authenticatedDataFeedKeyCache,
                builder.x509CertificateHeader,
                builder.x509CertificateDnHeader,
                builder.x509CertificateDnFormat,
                builder.allowedCertificateProviders,
                builder.feedNameGenerationEnabled,
                builder.feedNameTemplate,
                builder.feedNameGenerationMandatoryHeaders,
                builder.receiptCheckMode,
                builder.fallbackReceiveAction,
                builder.maxRequestSize);
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
                             "replaced with '_'. " +
                             "If this property is set in the YAML file, use single quotes to prevent the " +
                             "variables being expanded when the config file is loaded.")
    public String getFeedNameTemplate() {
        return feedNameTemplate;
    }

    @JsonPropertyDescription("The set of header keys are mandatory if feedNameGenerationEnabled is set to true. " +
                             "Should be set to complement the header keys used in 'feedNameTemplate', but may be a " +
                             "sub-set of those in the template to allow for optional headers.")
    public Set<String> getFeedNameGenerationMandatoryHeaders() {
        return feedNameGenerationMandatoryHeaders;
    }

    @JsonPropertyDescription("Controls how or whether data is checked on receipt. Valid values " +
                             "(FEED_STATUS|RECEIPT_POLICY|RECEIVE_ALL|REJECT_ALL|DROP_ALL).")
    public ReceiptCheckMode getReceiptCheckMode() {
        return receiptCheckMode;
    }

    @JsonPropertyDescription("If receiptCheckMode is RECEIPT_POLICY or FEED_STATUS and stroom/proxy is " +
                             "unable to perform the receipt check, then this action will be used as a fallback " +
                             "until the receipt check can be successfully performed.")
    public ReceiveAction getFallbackReceiveAction() {
        return fallbackReceiveAction;
    }

    @JsonPropertyDescription("If defined then states the maximum size of a request (uncompressed for gzip requests). " +
                             "Will return a 413 Content Too Long response code for any requests exceeding this " +
                             "value. If undefined then there is no limit to the size of the request.")
    public ByteSize getMaxRequestSize() {
        return maxRequestSize;
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
               ", metaTypes=" + metaTypes +
               ", authenticationRequired=" + authenticationRequired +
               ", dataFeedKeysDir='" + dataFeedKeysDir + '\'' +
               ", dataFeedKeyOwnerMetaKey='" + dataFeedKeyOwnerMetaKey + '\'' +
               ", authenticatedDataFeedKeyCache=" + authenticatedDataFeedKeyCache +
               ", enabledAuthenticationTypes=" + enabledAuthenticationTypes +
               ", x509CertificateHeader='" + x509CertificateHeader + '\'' +
               ", x509CertificateDnHeader='" + x509CertificateDnHeader + '\'' +
               ", allowedCertificateProviders=" + allowedCertificateProviders +
               ", feedNameGenerationEnabled=" + feedNameGenerationEnabled +
               ", feedNameTemplate='" + feedNameTemplate + '\'' +
               ", feedNameGenerationMandatoryHeaders=" + feedNameGenerationMandatoryHeaders +
               ", receiptCheckMode=" + receiptCheckMode +
               ", maxRequestSize=" + maxRequestSize +
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
               && Objects.equals(metaTypes, that.metaTypes)
               && Objects.equals(dataFeedKeysDir, that.dataFeedKeysDir)
               && Objects.equals(dataFeedKeyOwnerMetaKey, that.dataFeedKeyOwnerMetaKey)
               && Objects.equals(authenticatedDataFeedKeyCache, that.authenticatedDataFeedKeyCache)
               && Objects.equals(enabledAuthenticationTypes, that.enabledAuthenticationTypes)
               && Objects.equals(x509CertificateHeader, that.x509CertificateHeader)
               && Objects.equals(x509CertificateDnHeader, that.x509CertificateDnHeader)
               && Objects.equals(allowedCertificateProviders, that.allowedCertificateProviders)
               && Objects.equals(feedNameTemplate, that.feedNameTemplate)
               && Objects.equals(feedNameGenerationMandatoryHeaders, that.feedNameGenerationMandatoryHeaders)
               && Objects.equals(maxRequestSize, that.maxRequestSize)
               && receiptCheckMode == that.receiptCheckMode;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                metaTypes,
                authenticationRequired,
                dataFeedKeysDir,
                dataFeedKeyOwnerMetaKey,
                authenticatedDataFeedKeyCache,
                enabledAuthenticationTypes,
                x509CertificateHeader,
                x509CertificateDnHeader,
                allowedCertificateProviders,
                feedNameGenerationEnabled,
                feedNameTemplate,
                feedNameGenerationMandatoryHeaders,
                receiptCheckMode,
                maxRequestSize);
    }

    public static Builder copy(final ReceiveDataConfig receiveDataConfig) {
        final Builder builder = new Builder();
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
        builder.receiptCheckMode = receiveDataConfig.getReceiptCheckMode();
        builder.fallbackReceiveAction = receiveDataConfig.fallbackReceiveAction;
        builder.maxRequestSize = receiveDataConfig.maxRequestSize;
        return builder;
    }


    public static Builder builder() {
        return copy(new ReceiveDataConfig());
    }

    private static Set<String> cleanSet(final Set<String> set) {
        return CollectionUtil.cleanItems(set, String::trim);
    }

    private static CacheConfig createDefaultDataFeedKeyCacheConfig() {
        return CacheConfig.builder()
                .maximumSize(1000L)
                .expireAfterWrite(StroomDuration.ofMinutes(5))
                .statisticsMode(CacheConfig.PROXY_DEFAULT_STATISTICS_MODE) // Used by stroom & proxy so need DW metrics
                .build();
    }


    // --------------------------------------------------------------------------------


    public static final class Builder {

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
        private ReceiptCheckMode receiptCheckMode;
        private ReceiveAction fallbackReceiveAction;
        private ByteSize maxRequestSize;

        private Builder() {
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
            enabledAuthenticationTypes = NullSafe.mutableEnumSet(AuthenticationType.class, val);
            return this;
        }

        public Builder withEnabledAuthenticationTypes(final AuthenticationType... values) {
            enabledAuthenticationTypes = NullSafe.mutableEnumSetOf(AuthenticationType.class, values);
            return this;
        }

        public Builder addEnabledAuthenticationType(final AuthenticationType val) {
            if (val != null) {
                if (enabledAuthenticationTypes == null) {
                    enabledAuthenticationTypes = NullSafe.mutableEnumSetOf(AuthenticationType.class, val);
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

        public Builder withReceiptCheckMode(final ReceiptCheckMode receiptCheckMode) {
            this.receiptCheckMode = receiptCheckMode;
            return this;
        }

        public Builder withFallBackReceiveAction(final ReceiveAction fallBackReceiveAction) {
            this.fallbackReceiveAction = fallBackReceiveAction;
            return this;
        }

        public Builder withMaxRequestSize(final ByteSize maxRequestSize) {
            this.maxRequestSize = maxRequestSize;
            return this;
        }

        public ReceiveDataConfig build() {
            return new ReceiveDataConfig(this);
        }
    }
}
