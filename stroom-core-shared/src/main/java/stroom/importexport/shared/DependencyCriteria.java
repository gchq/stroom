package stroom.importexport.shared;

import stroom.util.shared.BaseCriteria;
import stroom.util.shared.PageRequest;
import stroom.util.shared.Sort;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(Include.NON_NULL)
public class DependencyCriteria extends BaseCriteria {
    public static final String FIELD_FROM_TYPE = "From (Type)";
    public static final String FIELD_FROM_NAME = "From (Name)";
    public static final String FIELD_FROM_UUID = "From (UUID)";
    public static final String FIELD_TO_TYPE = "To (Type)";
    public static final String FIELD_TO_NAME = "To (Name)";
    public static final String FIELD_TO_UUID = "To (UUID)";
    public static final String FIELD_STATUS = "Status";

    public DependencyCriteria() {
    }

    @JsonCreator
    public DependencyCriteria(@JsonProperty("pageRequest") final PageRequest pageRequest,
                              @JsonProperty("sortList") final List<Sort> sortList) {
        super(pageRequest, sortList);
    }
}
