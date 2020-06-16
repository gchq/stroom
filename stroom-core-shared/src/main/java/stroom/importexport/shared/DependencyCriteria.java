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

    public static final String FIELD_FROM_NAME_PARTIAL_MATCH = "From (Name) Partial Match";
    public static final String FIELD_TO_NAME_PARTIAL_MATCH = "From (Name) Partial Match";

    @JsonProperty
    private String partialName;

    public DependencyCriteria() {
    }

//    @JsonCreator
//    public DependencyCriteria(@JsonProperty("pageRequest") final PageRequest pageRequest,
//                              @JsonProperty("sortList") final List<Sort> sortList,
//                              @JsonProperty("expression") final ExpressionOperator expressionOperator) {
//        super(pageRequest, sortList, expressionOperator);
//    }

    @JsonCreator
    public DependencyCriteria(@JsonProperty("pageRequest") final PageRequest pageRequest,
                              @JsonProperty("sortList") final List<Sort> sortList,
                              @JsonProperty("partialName") final String partialName) {
        super(pageRequest, sortList);
        this.partialName = partialName;
    }

    public String getPartialName() {
        return partialName;
    }

    public void setPartialName(final String partialName) {
        this.partialName = partialName;
    }

    @Override
    public String toString() {
        return "DependencyCriteria{" +
                "partialName='" + partialName + '\'' +
                '}';
    }
}
