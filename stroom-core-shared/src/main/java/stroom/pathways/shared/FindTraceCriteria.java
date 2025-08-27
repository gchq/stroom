package stroom.pathways.shared;

import stroom.docref.DocRef;
import stroom.pathways.shared.pathway.Pathway;
import stroom.query.api.datasource.FieldFields;
import stroom.util.shared.BaseCriteria;
import stroom.util.shared.CriteriaFieldSort;
import stroom.util.shared.PageRequest;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder(alphabetic = true)
public class FindTraceCriteria extends BaseCriteria {

    public static final CriteriaFieldSort DEFAULT_SORT =
            new CriteriaFieldSort(FieldFields.NAME, false, true);
    public static final List<CriteriaFieldSort> DEFAULT_SORT_LIST =
            Collections.singletonList(DEFAULT_SORT);

    @JsonProperty
    private final DocRef dataSourceRef;
    @JsonProperty
    private final String filter;
    @JsonProperty
    private final Pathway pathway;

    public FindTraceCriteria(@JsonProperty("pageRequest") final PageRequest pageRequest,
                             @JsonProperty("sortList") final List<CriteriaFieldSort> sortList,
                             @JsonProperty("dataSourceRef") final DocRef dataSourceRef) {
        this(pageRequest, sortList, dataSourceRef, null, null);
    }

    @JsonCreator
    public FindTraceCriteria(@JsonProperty("pageRequest") final PageRequest pageRequest,
                             @JsonProperty("sortList") final List<CriteriaFieldSort> sortList,
                             @JsonProperty("dataSourceRef") final DocRef dataSourceRef,
                             @JsonProperty("filter") final String filter,
                             @JsonProperty("pathway") final Pathway pathway) {
        super(pageRequest, sortList);
        this.dataSourceRef = dataSourceRef;
        this.filter = filter;
        this.pathway = pathway;
    }

    public DocRef getDataSourceRef() {
        return dataSourceRef;
    }

    public String getFilter() {
        return filter;
    }

    public Pathway getPathway() {
        return pathway;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final FindTraceCriteria that = (FindTraceCriteria) o;
        return Objects.equals(dataSourceRef, that.dataSourceRef) &&
               Objects.equals(filter, that.filter) &&
               Objects.equals(pathway, that.pathway);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), dataSourceRef, filter, pathway);
    }

    @Override
    public String toString() {
        return "FindTraceCriteria{" +
               "dataSourceRef=" + dataSourceRef +
               ", filter='" + filter + '\'' +
               ", pathway=" + pathway +
               '}';
    }
}
