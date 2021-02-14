package stroom.search.elastic.shared;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import java.io.Serializable;
import java.util.Objects;

@XmlAccessorType(XmlAccessType.FIELD)
@JsonPropertyOrder({"scheme", "host", "port"})
@XmlRootElement(name = "connection")
@XmlType(name = "ElasticConnectionConfig", propOrder = {"scheme", "host", "port"})
public class ElasticConnectionConfig implements Serializable {
    private String scheme = "http";
    private String host;
    private Integer port = 9200;

    public String getScheme() { return scheme; }

    public void setScheme(final String scheme) { this.scheme = scheme; }

    public String getHost() { return this.host; }

    public void setHost(final String host) { this.host = host; }

    public Integer getPort() { return port; }

    public void setPort(final Integer port) { this.port = port; }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof ElasticConnectionConfig)) return false;
        final ElasticConnectionConfig that = (ElasticConnectionConfig)o;
        return
                scheme.equals(that.scheme) &&
                host.equals(that.host) &&
                port.equals(that.port);
    }

    @Override
    public int hashCode() {
        return Objects.hash(host);
    }

    @Override
    public String toString() {
        return "ElasticConnectionConfig{" +
                "scheme='" + scheme + '\'' +
                ", host='" + host + '\'' +
                ", port=" + port +
                '}';
    }

    public String toUrl() {
        return scheme + "://" + host + ":" + port;
    }
}
