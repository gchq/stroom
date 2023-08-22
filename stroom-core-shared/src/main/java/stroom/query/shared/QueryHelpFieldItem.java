package stroom.query.shared;

import stroom.datasource.api.v2.AbstractField;
import stroom.query.shared.QueryHelpFieldItem.QueryHelpFieldLeaf;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.List;
import java.util.Objects;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = QueryHelpFieldItem.class, name = "branch"),
        @JsonSubTypes.Type(value = QueryHelpFieldLeaf.class, name = "leaf")
})
public interface QueryHelpFieldItem {

    String getName();


    // --------------------------------------------------------------------------------


    @JsonPropertyOrder(alphabetic = true)
    @JsonInclude(Include.NON_NULL)
    class QueryHelpFieldBranch implements QueryHelpFieldItem {

        @JsonProperty
        private final String name;
        @JsonProperty
        private final List<QueryHelpFieldItem> children;

        @JsonCreator
        public QueryHelpFieldBranch(@JsonProperty("name") final String name,
                                    @JsonProperty("children") final List<QueryHelpFieldItem> children) {
            this.name = name;
            this.children = children;
        }

        public List<QueryHelpFieldItem> getChildren() {
            return children;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final QueryHelpFieldBranch that = (QueryHelpFieldBranch) o;
            return Objects.equals(name, that.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name);
        }

        @Override
        public String toString() {
            return "QueryHelpFieldBranch{" +
                    "name='" + name + '\'' +
                    ", children=" + children +
                    '}';
        }
    }


    // --------------------------------------------------------------------------------


    @JsonPropertyOrder(alphabetic = true)
    @JsonInclude(Include.NON_NULL)
    class QueryHelpFieldLeaf implements QueryHelpFieldItem {

        @JsonProperty
        private final AbstractField field;

        @JsonCreator
        public QueryHelpFieldLeaf(@JsonProperty("field") final AbstractField field) {
            this.field = field;
        }

        @JsonIgnore
        @Override
        public String getName() {
            return field.getName();
        }

        public AbstractField getField() {
            return field;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final QueryHelpFieldLeaf that = (QueryHelpFieldLeaf) o;
            return Objects.equals(field, that.field);
        }

        @Override
        public int hashCode() {
            return Objects.hash(field);
        }


    }
}
