package stroom.node.shared;

import stroom.util.shared.BaseCriteria;
import stroom.util.shared.CriteriaFieldSort;
import stroom.util.shared.PageRequest;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class FindNodeStatusCriteria extends BaseCriteria {

    public static final String FIELD_ID_NAME = "Name";
    public static final String FIELD_ID_URL = "URL";
    public static final String FIELD_ID_PRIORITY = "Priority";
    public static final String FIELD_ID_ENABLED = "Enabled";

    public FindNodeStatusCriteria() {
        super();
    }

    @JsonCreator
    public FindNodeStatusCriteria(@JsonProperty("pageRequest") final PageRequest pageRequest,
                                  @JsonProperty("sortList") final List<CriteriaFieldSort> sortList) {
        super(pageRequest, sortList);
    }

    @Override
    public String toString() {
        return super.toString();
    }
}
