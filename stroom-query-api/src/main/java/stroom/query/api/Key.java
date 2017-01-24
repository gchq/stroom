package stroom.query.api;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.XmlType;
import java.io.Serializable;

@JsonPropertyOrder({"type", "value"})
@XmlType(name = "key", propOrder = {"type", "value"})
public class Key implements Serializable {
    private static final long serialVersionUID = 1272545271946712570L;

    private String type;
    private Object value;

    public Key() {
    }

    public Key(final String type, final Object value) {
        this.type = type;
        this.value = value;
    }

    @XmlElement
    public String getType() {
        return type;
    }

    public void setType(final String type) {
        this.type = type;
    }

    @XmlElements({
            @XmlElement(name = "byte", type = Byte.class),
            @XmlElement(name = "double", type = Double.class),
            @XmlElement(name = "float", type = Float.class),
            @XmlElement(name = "short", type = Short.class),
            @XmlElement(name = "integer", type = Integer.class),
            @XmlElement(name = "long", type = Long.class),
            @XmlElement(name = "string", type = String.class)
    })
    public Object getValue() {
        return value;
    }

    public void setValue(final Object value) {
        this.value = value;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof Key)) return false;

        final Key key = (Key) o;

        if (type != null ? !type.equals(key.type) : key.type != null) return false;
        return value != null ? value.equals(key.value) : key.value == null;
    }

    @Override
    public int hashCode() {
        int result = type != null ? type.hashCode() : 0;
        result = 31 * result + (value != null ? value.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "Key{" +
                "type='" + type + '\'' +
                ", value=" + value +
                '}';
    }
}
