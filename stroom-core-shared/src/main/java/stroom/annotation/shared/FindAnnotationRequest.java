package stroom.annotation.shared;

import stroom.security.shared.DocumentPermission;
import stroom.util.shared.BaseCriteria;
import stroom.util.shared.CriteriaFieldSort;
import stroom.util.shared.PageRequest;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(Include.NON_NULL)
public class FindAnnotationRequest extends BaseCriteria {

    @JsonProperty
    private final String filter;
    @JsonProperty
    private final DocumentPermission requiredPermission;

    @JsonCreator
    public FindAnnotationRequest(@JsonProperty("pageRequest") final PageRequest pageRequest,
                                 @JsonProperty("sortList") final List<CriteriaFieldSort> sortList,
                                 @JsonProperty("filter") final String filter,
                                 @JsonProperty("requiredPermission") final DocumentPermission requiredPermission) {
        super(pageRequest, sortList);
        this.filter = filter;
        this.requiredPermission = requiredPermission;
    }

    public String getFilter() {
        return filter;
    }

    public DocumentPermission getRequiredPermission() {
        return requiredPermission;
    }
}
