package stroom.pathways.shared;

import stroom.docref.DocRef;
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
public class FindPathwayCriteria extends BaseCriteria {

    public static final CriteriaFieldSort DEFAULT_SORT =
            new CriteriaFieldSort(FieldFields.NAME, false, true);
    public static final List<CriteriaFieldSort> DEFAULT_SORT_LIST =
            Collections.singletonList(DEFAULT_SORT);

    @JsonProperty
    private final DocRef dataSourceRef;
    @JsonProperty
    private final String filter;
    @JsonProperty
    private final Boolean queryable;

    public FindPathwayCriteria(@JsonProperty("pageRequest") final PageRequest pageRequest,
                               @JsonProperty("sortList") final List<CriteriaFieldSort> sortList,
                               @JsonProperty("dataSourceRef") final DocRef dataSourceRef) {
        this(pageRequest, sortList, dataSourceRef, null, null);
    }

    @JsonCreator
    public FindPathwayCriteria(@JsonProperty("pageRequest") final PageRequest pageRequest,
                               @JsonProperty("sortList") final List<CriteriaFieldSort> sortList,
                               @JsonProperty("dataSourceRef") final DocRef dataSourceRef,
                               @JsonProperty("filter") final String filter,
                               @JsonProperty("queryable") final Boolean queryable) {
        super(pageRequest, sortList);
        this.dataSourceRef = dataSourceRef;
        this.filter = filter;
        this.queryable = queryable;
    }

    public DocRef getDataSourceRef() {
        return dataSourceRef;
    }

    public String getFilter() {
        return filter;
    }

    public Boolean getQueryable() {
        return queryable;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof FindPathwayCriteria)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final FindPathwayCriteria that = (FindPathwayCriteria) o;
        return Objects.equals(dataSourceRef, that.dataSourceRef) &&
               Objects.equals(filter, that.filter) &&
               Objects.equals(queryable, that.queryable);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), dataSourceRef, filter, queryable);
    }
}
