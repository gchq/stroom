package stroom.datasource.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.util.shared.HasDisplayValue;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;
import java.io.Serializable;
import java.util.Arrays;

@JsonPropertyOrder({"type", "name", "queryable", "conditions"})
@XmlType(name = "DataSourceField", propOrder = {"type", "name", "queryable", "conditions"})
public class DataSourceField implements Serializable, HasDisplayValue {
    private static final long serialVersionUID = 1272545271946712570L;

    private DataSourceFieldType type;
    private String name;
    private Boolean queryable;

    /**
     * Defines a list of the {@link Condition} values supported by this field,
     * can be null in which case a default set will be returned. Not persisted
     * in the XML
     */
    private Condition[] conditions;

    public DataSourceField() {
    }

    public DataSourceField(final DataSourceFieldType type, final String name) {
        this.type = type;
        this.name = name;
    }

    public DataSourceField(final DataSourceFieldType type, final String name, final Boolean queryable, final Condition[] conditions) {
        this.type = type;
        this.name = name;
        this.queryable = queryable;
        this.conditions = conditions;
    }

    @XmlElement
    public DataSourceFieldType getType() {
        return type;
    }

    public void setType(final DataSourceFieldType type) {
        this.type = type;
    }

    @XmlElement
    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    @XmlElementWrapper(name = "conditions")
    @XmlElement(name = "condition")
    public Condition[] getConditions() {
        return conditions;
    }

    public void setConditions(final Condition[] conditions) {
        this.conditions = conditions;
    }

    @XmlElement
    public Boolean getQueryable() {
        return queryable;
    }

    public void setQueryable(final Boolean queryable) {
        this.queryable = queryable;
    }

    public boolean queryable() {
        return queryable != null && queryable;
    }

    @JsonIgnore
    @XmlTransient
    @Override
    public String getDisplayValue() {
        return name;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final DataSourceField that = (DataSourceField) o;

        if (type != that.type) return false;
        if (name != null ? !name.equals(that.name) : that.name != null) return false;
        if (queryable != null ? !queryable.equals(that.queryable) : that.queryable != null) return false;
        // Probably incorrect - comparing Object[] arrays with Arrays.equals
        return Arrays.equals(conditions, that.conditions);
    }

    @Override
    public int hashCode() {
        int result = type != null ? type.hashCode() : 0;
        result = 31 * result + (name != null ? name.hashCode() : 0);
        result = 31 * result + (queryable != null ? queryable.hashCode() : 0);
        result = 31 * result + Arrays.hashCode(conditions);
        return result;
    }

    @Override
    public String toString() {
        return "DataSourceField{" +
                "type=" + type +
                ", name='" + name + '\'' +
                ", queryable=" + queryable +
                ", conditions=" + Arrays.toString(conditions) +
                '}';
    }

    public enum DataSourceFieldType implements HasDisplayValue {
        FIELD("Text", false), NUMERIC_FIELD("Number", true), DATE_FIELD("Date", false), ID("Id", true);

        private final String displayValue;
        private final boolean numeric;

        DataSourceFieldType(final String displayValue, final boolean numeric) {
            this.displayValue = displayValue;
            this.numeric = numeric;
        }

        public boolean isNumeric() {
            return numeric;
        }

        @Override
        public String getDisplayValue() {
            return displayValue;
        }
    }
}