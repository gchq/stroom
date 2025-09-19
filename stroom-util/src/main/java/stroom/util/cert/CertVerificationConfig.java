package stroom.util.cert;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.shared.NotInjectableConfig;
import stroom.util.shared.NullSafe;
import stroom.util.shared.validation.ValidFilePath;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

@NotInjectableConfig
@JsonPropertyOrder(alphabetic = true)
public class CertVerificationConfig extends AbstractConfig implements IsStroomConfig, IsProxyConfig {

    private static final boolean DEFAULT_VALIDATE_CERT_EXPIRY = true;
    public static final String DEFAULT_TRUST_TSTORE_TYPE = "JKS";
    private static final RevocationCheckMode DEFAULT_REVOCATION_CHECK_MODE = RevocationCheckMode.NONE;

    @JsonProperty
    private final boolean validateClientCertificateExpiry;
    @JsonProperty
    private final RevocationCheckMode revocationCheckMode;
    @JsonProperty
    private final String trustStorePath;
    @JsonProperty
    private final String trustStorePassword;
    @JsonProperty
    private final String trustStoreType;
    @JsonProperty
    private final List<String> revocationListPaths;
    @JsonProperty
    private final String ocspResponderUrl;

    public CertVerificationConfig() {
        this.validateClientCertificateExpiry = DEFAULT_VALIDATE_CERT_EXPIRY;
        this.revocationCheckMode = DEFAULT_REVOCATION_CHECK_MODE;
        this.trustStorePath = null;
        this.trustStorePassword = null;
        this.trustStoreType = DEFAULT_TRUST_TSTORE_TYPE;
        this.revocationListPaths = Collections.emptyList();
        this.ocspResponderUrl = null;
    }

    public CertVerificationConfig(
            @JsonProperty("validateClientCertificateExpiry") final Boolean validateClientCertificateExpiry,
            @JsonProperty("revocationCheckEnabled") final RevocationCheckMode revocationCheckMode,
            @JsonProperty("trustStorePath") final String trustStorePath,
            @JsonProperty("trustStorePassword") final String trustStorePassword,
            @JsonProperty("trustStoreType") final String trustStoreType,
            @JsonProperty("revocationListPaths") final List<String> revocationListPaths,
            @JsonProperty("ocspResponderUrl") final String ocspResponderUrl) {

        this.validateClientCertificateExpiry = Objects.requireNonNullElse(
                validateClientCertificateExpiry, DEFAULT_VALIDATE_CERT_EXPIRY);
        this.revocationCheckMode = Objects.requireNonNullElse(
                revocationCheckMode, DEFAULT_REVOCATION_CHECK_MODE);
        this.trustStorePath = trustStorePath;
        this.trustStorePassword = trustStorePassword;
        this.trustStoreType = NullSafe.nonBlankStringElse(trustStoreType, DEFAULT_TRUST_TSTORE_TYPE);
        this.revocationListPaths = NullSafe.list(revocationListPaths);
        this.ocspResponderUrl = ocspResponderUrl;
    }

    public boolean isValidateClientCertificateExpiry() {
        return validateClientCertificateExpiry;
    }

    public RevocationCheckMode getRevocationCheckMode() {
        return revocationCheckMode;
    }

    @ValidFilePath
    @JsonPropertyDescription("The file path of the trust store to use to verify the client's X509 certificate(s)")
    public String getTrustStorePath() {
        return trustStorePath;
    }

    @JsonPropertyDescription("The password of the trust store to use to verify the client's X509 certificate(s)")
    public String getTrustStorePassword() {
        return trustStorePassword;
    }

    @JsonPropertyDescription("The type of trust store to use to verify the client's X509 certificate(s). " +
                             "Valid values are (JKS|PKCS12).")
    public String getTrustStoreType() {
        return trustStoreType;
    }

    @JsonPropertyDescription("A list of paths to Certificate Revocation List files. If none are provided ")
    public List<String> getRevocationListPaths() {
        return revocationListPaths;
    }

    @JsonPropertyDescription("The URL to use for OCSP checking (if enabled). " +
                             "If not set the URL obtained from the certificate will be used.")
    public String getOcspResponderUrl() {
        return ocspResponderUrl;
    }

    @Override
    public String toString() {
        return "CertVerificationConfig{" +
               "validateClientCertificateExpiry=" + validateClientCertificateExpiry +
               ", revocationCheckMode=" + revocationCheckMode +
               ", trustStorePath='" + trustStorePath + '\'' +
               ", trustStorePassword='" + trustStorePassword + '\'' +
               ", trustStoreType='" + trustStoreType + '\'' +
               ", revocationListPaths=" + revocationListPaths +
               ", ocspResponderUrl='" + ocspResponderUrl + '\'' +
               '}';
    }

    @Override
    public boolean equals(final Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }
        final CertVerificationConfig that = (CertVerificationConfig) object;
        return validateClientCertificateExpiry == that.validateClientCertificateExpiry
               && revocationCheckMode == that.revocationCheckMode && Objects.equals(
                trustStorePath,
                that.trustStorePath) && Objects.equals(trustStorePassword,
                that.trustStorePassword) && Objects.equals(trustStoreType,
                that.trustStoreType) && Objects.equals(revocationListPaths,
                that.revocationListPaths) && Objects.equals(ocspResponderUrl, that.ocspResponderUrl);
    }

    @Override
    public int hashCode() {
        return Objects.hash(validateClientCertificateExpiry,
                revocationCheckMode,
                trustStorePath,
                trustStorePassword,
                trustStoreType,
                revocationListPaths,
                ocspResponderUrl);
    }


    // --------------------------------------------------------------------------------


    public enum RevocationCheckMode {
        OCSP_ONLY,
        CRL_ONLY,
        PREFER_OCSP,
        PREFER_CRL,
        NONE,
        ;
    }
}
