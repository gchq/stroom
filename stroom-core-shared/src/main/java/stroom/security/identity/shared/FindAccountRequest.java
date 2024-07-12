package stroom.security.identity.shared;

import stroom.util.shared.BaseCriteria;
import stroom.util.shared.CriteriaFieldSort;
import stroom.util.shared.PageRequest;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(Include.NON_NULL)
public class FindAccountRequest extends BaseCriteria {

    public static final String FIELD_NAME_USER_ID = "userId";
    public static final String FIELD_NAME_EMAIL = "email";
    public static final String FIELD_NAME_STATUS = "status";
    public static final String FIELD_NAME_LAST_LOGIN_MS = "lastLoginMs";
    public static final String FIELD_NAME_LOGIN_FAILURES = "loginFailures";
    public static final String FIELD_NAME_COMMENTS = "comments";

    @JsonProperty
    private final String quickFilter;

    @JsonCreator
    public FindAccountRequest(@JsonProperty("pageRequest") final PageRequest pageRequest,
                              @JsonProperty("sortList") final List<CriteriaFieldSort> sortList,
                              @JsonProperty("quickFilter") final String quickFilter) {
        super(pageRequest, sortList);
        this.quickFilter = quickFilter;
    }

    public String getQuickFilter() {
        return quickFilter;
    }
}
