package stroom.importexport.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import stroom.util.shared.BaseCriteria;
import stroom.util.shared.PageRequest;
import stroom.util.shared.Sort;

import java.util.List;

@JsonInclude(Include.NON_NULL)
public class DependencyCriteria extends BaseCriteria {
    public static final String FIELD_FROM = "From";
    public static final String FIELD_TO = "To";
    public static final String FIELD_STATUS = "Status";

    public DependencyCriteria() {
    }

    @JsonCreator
    public DependencyCriteria(@JsonProperty("pageRequest") final PageRequest pageRequest,
                              @JsonProperty("sortList") final List<Sort> sortList) {
        super(pageRequest, sortList);
    }
}
