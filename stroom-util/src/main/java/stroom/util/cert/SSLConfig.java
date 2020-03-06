package stroom.util.cert;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SSLConfig {
    private String keyStorePath;
    private String keyStoreType = "JKS";
    private String keyStorePassword;

    private String trustStorePath;
    private String trustStoreType = "JKS";
    private String trustStorePassword;

    private boolean isHostnameVerificationEnabled = true;
    private String sslProtocol = "TLSv1.2";

    /**
     * The path to the keystore file that will be used for client authentication during forwarding
     */
    @JsonProperty
    public String getKeyStorePath() {
        return keyStorePath;
    }

    @JsonProperty
    public void setKeyStorePath(final String keyStorePath) {
        this.keyStorePath = keyStorePath;
    }

    /**
     * The type of the keystore, e.g. JKS
     */
    @JsonProperty
    public String getKeyStoreType() {
        return keyStoreType;
    }

    @JsonProperty
    public void setKeyStoreType(final String keyStoreType) {
        this.keyStoreType = keyStoreType;
    }

    /**
     * The password for the keystore
     */
    @JsonProperty
    public String getKeyStorePassword() {
        return keyStorePassword;
    }

    @JsonProperty
    public void setKeyStorePassword(final String keyStorePassword) {
        this.keyStorePassword = keyStorePassword;
    }

    /**
     * The path to the truststore file that will be used for client authentication during forwarding
     */
    @JsonProperty
    public String getTrustStorePath() {
        return trustStorePath;
    }

    @JsonProperty
    public void setTrustStorePath(final String trustStorePath) {
        this.trustStorePath = trustStorePath;
    }

    /**
     * The type of the truststore, e.g. JKS
     */
    @JsonProperty
    public String getTrustStoreType() {
        return trustStoreType;
    }

    @JsonProperty
    public void setTrustStoreType(final String trustStoreType) {
        this.trustStoreType = trustStoreType;
    }

    /**
     * The password for the truststore
     */
    @JsonProperty
    public String getTrustStorePassword() {
        return trustStorePassword;
    }

    @JsonProperty
    public void setTrustStorePassword(final String trustStorePassword) {
        this.trustStorePassword = trustStorePassword;
    }

    /**
     * If true default verification of the destination hostname against the server certificate will be used.
     * If false any destination hostname will be permitted.
     */
    @JsonProperty
    public boolean isHostnameVerificationEnabled() {
        return isHostnameVerificationEnabled;
    }

    @JsonProperty
    public void setHostnameVerificationEnabled(final boolean hostnameVerificationEnabled) {
        isHostnameVerificationEnabled = hostnameVerificationEnabled;
    }

    /**
     * The SSL protocol to use, e.g. TLSv1.2
     */
    @JsonProperty
    public String getSslProtocol() {
        return sslProtocol;
    }

    @JsonProperty
    public void setSslProtocol(final String sslProtocol) {
        this.sslProtocol = sslProtocol;
    }
}
