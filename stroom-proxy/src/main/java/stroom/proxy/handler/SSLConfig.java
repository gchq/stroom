package stroom.proxy.handler;

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
    String getKeyStorePath() {
        return keyStorePath;
    }

    @JsonProperty
    void setKeyStorePath(final String keyStorePath) {
        this.keyStorePath = keyStorePath;
    }

    /**
     * The type of the keystore, e.g. JKS
     */
    @JsonProperty
    String getKeyStoreType() {
        return keyStoreType;
    }

    @JsonProperty
    void setKeyStoreType(final String keyStoreType) {
        this.keyStoreType = keyStoreType;
    }

    /**
     * The password for the keystore
     */
    @JsonProperty
    String getKeyStorePassword() {
        return keyStorePassword;
    }

    @JsonProperty
    void setKeyStorePassword(final String keyStorePassword) {
        this.keyStorePassword = keyStorePassword;
    }

    /**
     * The path to the truststore file that will be used for client authentication during forwarding
     */
    @JsonProperty
    String getTrustStorePath() {
        return trustStorePath;
    }

    @JsonProperty
    void setTrustStorePath(final String trustStorePath) {
        this.trustStorePath = trustStorePath;
    }

    /**
     * The type of the truststore, e.g. JKS
     */
    @JsonProperty
    String getTrustStoreType() {
        return trustStoreType;
    }

    @JsonProperty
    void setTrustStoreType(final String trustStoreType) {
        this.trustStoreType = trustStoreType;
    }

    /**
     * The password for the truststore
     */
    @JsonProperty
    String getTrustStorePassword() {
        return trustStorePassword;
    }

    @JsonProperty
    void setTrustStorePassword(final String trustStorePassword) {
        this.trustStorePassword = trustStorePassword;
    }

    /**
     * If true default verification of the destination hostname against the server certificate will be used.
     * If false any destination hostname will be permitted.
     */
    @JsonProperty
    boolean isHostnameVerificationEnabled() {
        return isHostnameVerificationEnabled;
    }

    @JsonProperty
    void setHostnameVerificationEnabled(final boolean hostnameVerificationEnabled) {
        isHostnameVerificationEnabled = hostnameVerificationEnabled;
    }

    /**
     * The SSL protocol to use, e.g. TLSv1.2
     */
    @JsonProperty
    String getSslProtocol() {
        return sslProtocol;
    }

    @JsonProperty
    void setSslProtocol(final String sslProtocol) {
        this.sslProtocol = sslProtocol;
    }
}
