package stroom.search.elastic.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
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
        "apiKeySecret",
        "connectionTimeoutMillis",
        "responseTimeoutMillis"})
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

    @JsonProperty
    private String apiKeySecret;

    /**
     * Amount of time to wait when connecting to Elasticsearch
     */
    @JsonProperty
    private int connectionTimeoutMillis = 3000;

    /**
     * Amount of time to allow for Elasticsearch requests to complete.
     * If this interval is exceeded, the request is aborted and an `Error` stream is created.
     */
    @JsonProperty
    private int responseTimeoutMillis = 0;

    public ElasticConnectionConfig() {
    }

    @JsonCreator
    public ElasticConnectionConfig(@JsonProperty("connectionUrls") final List<String> connectionUrls,
                                   @JsonProperty("caCertificate") final String caCertificate,
                                   @JsonProperty("useAuthentication") final boolean useAuthentication,
                                   @JsonProperty("apiKeyId") final String apiKeyId,
                                   @JsonProperty("apiKeySecret") final String apiKeySecret,
                                   @JsonProperty("connectionTimeoutMillis") final int connectionTimeoutMillis,
                                   @JsonProperty("responseTimeoutMillis") final int responseTimeoutMillis) {
        this.connectionUrls = connectionUrls;
        this.caCertificate = caCertificate;
        this.useAuthentication = useAuthentication;
        this.apiKeyId = apiKeyId;
        this.apiKeySecret = apiKeySecret;
        this.connectionTimeoutMillis = connectionTimeoutMillis;
        this.responseTimeoutMillis = responseTimeoutMillis;
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

    public String getApiKeySecret() {
        return apiKeySecret;
    }

    public void setApiKeySecret(final String apiKeySecret) {
        this.apiKeySecret = apiKeySecret;
    }

    public int getConnectionTimeoutMillis() {
        return connectionTimeoutMillis;
    }

    public void setConnectionTimeoutMillis(final int connectionTimeoutMillis) {
        this.connectionTimeoutMillis = connectionTimeoutMillis;
    }

    public int getResponseTimeoutMillis() {
        return responseTimeoutMillis;
    }

    public void setResponseTimeoutMillis(final int responseTimeoutMillis) {
        this.responseTimeoutMillis = responseTimeoutMillis;
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
               Objects.equals(connectionTimeoutMillis, that.connectionTimeoutMillis) &&
               Objects.equals(responseTimeoutMillis, that.responseTimeoutMillis);
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
               ", connectionTimeoutMillis=" + connectionTimeoutMillis +
               ", responseTimeoutMillis=" + responseTimeoutMillis +
               '}';
    }
}
