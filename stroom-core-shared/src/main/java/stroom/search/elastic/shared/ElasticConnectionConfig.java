package stroom.search.elastic.shared;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import java.io.Serializable;
import java.util.List;
import java.util.Objects;

@XmlAccessorType(XmlAccessType.FIELD)
@JsonPropertyOrder({"connectionUrls"})
@XmlRootElement(name = "connection")
@XmlType(name = "ElasticConnectionConfig", propOrder = {"connectionUrls"})
public class ElasticConnectionConfig implements Serializable {
    private List<String> connectionUrls;

    public List<String> getConnectionUrls() { return connectionUrls; }

    public void setConnectionUrls(final List<String> connectionUrls) { this.connectionUrls = connectionUrls; }

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
                '}';
    }
}
