package stroom.search.elastic.shared;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import java.io.Serializable;
import java.util.List;
import java.util.Objects;

@XmlAccessorType(XmlAccessType.FIELD)
@JsonPropertyOrder({"connectionUrls,useAuthentication,apiKeyId,apiKeySecret"})
@XmlRootElement(name = "connection")
@XmlType(name = "ElasticConnectionConfig", propOrder = {"connectionUrls", "useAuthentication", "apiKeyId", "apiKeySecret"})
public class ElasticConnectionConfig implements Serializable {
    private List<String> connectionUrls;

    private boolean useAuthentication = false;

    private String apiKeyId;

    // Plain-text API key (not serialised)
    @JsonIgnore
    private String apiKeySecret;

    // This is the field that is actually serialised and is an encrypted version of member variable `apiKeySecret`
    @JsonProperty("apiKeySecret")
    private String apiKeySecretEncrypted;

    public List<String> getConnectionUrls() { return connectionUrls; }

    public void setConnectionUrls(final List<String> connectionUrls) { this.connectionUrls = connectionUrls; }

    public boolean getUseAuthentication() { return useAuthentication; }

    public void setUseAuthentication(final boolean useAuthentication) { this.useAuthentication = useAuthentication; }

    public String getApiKeyId() { return apiKeyId; }

    public void setApiKeyId(final String apiKeyId) { this.apiKeyId = apiKeyId; }

    public String getApiKeySecret() { return apiKeySecret; }

    public void setApiKeySecret(final String apiKeySecret) { this.apiKeySecret = apiKeySecret; }

    public String getApiKeySecretEncrypted() { return apiKeySecretEncrypted; }

    public void setApiKeySecretEncrypted(final String apiKeySecretEncrypted) { this.apiKeySecretEncrypted = apiKeySecretEncrypted; }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof ElasticConnectionConfig)) return false;
        final ElasticConnectionConfig that = (ElasticConnectionConfig)o;

        return connectionUrls.equals(that.connectionUrls);
    }

    @Override
    public int hashCode() {
        return Objects.hash(connectionUrls);
    }

    @Override
    public String toString() {
        return "ElasticConnectionConfig{" +
                "connectionUrls='" + String.join(",", connectionUrls) + '\'' +
                "useAuthentication=" + useAuthentication +
                "apiKeyId='" + apiKeyId + '\'' +
                "apiKeySecret='<redacted>'" +
                '}';
    }
}
