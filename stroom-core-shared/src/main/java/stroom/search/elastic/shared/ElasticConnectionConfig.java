package stroom.search.elastic.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@JsonPropertyOrder({
        "connectionUrls",
        "caCertificate",
        "useAuthentication",
        "apiKeyId",
        "apiKeySecretEncrypted",
        "socketTimeoutMillis"})
@JsonInclude(Include.NON_NULL)
public class ElasticConnectionConfig implements Serializable {

    @JsonProperty
    private List<String> connectionUrls = new ArrayList<>();

    /**
     * DER or PEM-encoded CA certificate for X.509 verification
     */
    @JsonProperty
    private String caCertificate;

    @JsonProperty
    private boolean useAuthentication = false;

    @JsonProperty
    private String apiKeyId;

    /**
     * Plain-text API key (not serialised)
     */
    @JsonIgnore
    private String apiKeySecret;

    /**
     * This is the field that is actually serialised and is an encrypted version of member variable `apiKeySecret`
     */
    @JsonProperty("apiKeySecretEncrypted")
    private String apiKeySecretEncrypted;

    /**
     * Socket timeout duration. Any Elasticsearch requests are expected to complete within this interval,
     * else the request is aborted and an `Error` is reported.
     */
    @JsonProperty
    private int socketTimeoutMillis = -1;

    public ElasticConnectionConfig() {
    }

    @JsonCreator
    public ElasticConnectionConfig(@JsonProperty("connectionUrls") final List<String> connectionUrls,
                                   @JsonProperty("caCertificate") final String caCertificate,
                                   @JsonProperty("useAuthentication") final boolean useAuthentication,
                                   @JsonProperty("apiKeyId") final String apiKeyId,
                                   @JsonProperty("apiKeySecretEncrypted") final String apiKeySecretEncrypted,
                                   @JsonProperty("socketTimeoutMillis") final int socketTimeoutMillis) {
        this.connectionUrls = connectionUrls;
        this.caCertificate = caCertificate;
        this.useAuthentication = useAuthentication;
        this.apiKeyId = apiKeyId;
        this.apiKeySecretEncrypted = apiKeySecretEncrypted;
        this.socketTimeoutMillis = socketTimeoutMillis;
    }

    public List<String> getConnectionUrls() {
        return connectionUrls;
    }

    public void setConnectionUrls(final List<String> connectionUrls) {
        this.connectionUrls = connectionUrls;
    }

    public String getCaCertificate() {
        return caCertificate;
    }

    public void setCaCertificate(final String caCertificate) {
        this.caCertificate = caCertificate;
    }

    public boolean getUseAuthentication() {
        return useAuthentication;
    }

    public void setUseAuthentication(final boolean useAuthentication) {
        this.useAuthentication = useAuthentication;
    }

    public String getApiKeyId() {
        return apiKeyId;
    }

    public void setApiKeyId(final String apiKeyId) {
        this.apiKeyId = apiKeyId;
    }

    @JsonIgnore
    public String getApiKeySecret() {
        return apiKeySecret;
    }

    @JsonIgnore
    public void setApiKeySecret(final String apiKeySecret) {
        this.apiKeySecret = apiKeySecret;
    }

    public String getApiKeySecretEncrypted() {
        return apiKeySecretEncrypted;
    }

    public void setApiKeySecretEncrypted(final String apiKeySecretEncrypted) {
        this.apiKeySecretEncrypted = apiKeySecretEncrypted;
    }

    public int getSocketTimeoutMillis() {
        return socketTimeoutMillis;
    }

    public void setSocketTimeoutMillis(final int socketTimeoutMillis) {
        this.socketTimeoutMillis = socketTimeoutMillis;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof ElasticConnectionConfig)) {
            return false;
        }
        final ElasticConnectionConfig that = (ElasticConnectionConfig) obj;
        return Objects.equals(connectionUrls, that.connectionUrls) &&
                Objects.equals(caCertificate, that.caCertificate) &&
                Objects.equals(useAuthentication, that.useAuthentication) &&
                Objects.equals(apiKeyId, that.apiKeyId) &&
                Objects.equals(apiKeySecret, that.apiKeySecret) &&
                Objects.equals(socketTimeoutMillis, that.socketTimeoutMillis);
    }

    @Override
    public int hashCode() {
        return Objects.hash(connectionUrls);
    }

    @Override
    public String toString() {
        return "ElasticConnectionConfig{" +
                "connectionUrls='" + String.join(",", connectionUrls) + '\'' +
                ", caCertPath='" + caCertificate + '\'' +
                ", useAuthentication=" + useAuthentication +
                ", apiKeyId='" + apiKeyId + '\'' +
                ", apiKeySecret='<redacted>'" +
                ", socketTimeoutMillis=" + socketTimeoutMillis +
                '}';
    }
}
