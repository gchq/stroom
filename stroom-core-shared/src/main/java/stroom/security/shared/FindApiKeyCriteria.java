package stroom.security.shared;

import stroom.util.shared.BaseCriteria;
import stroom.util.shared.CriteriaFieldSort;
import stroom.util.shared.PageRequest;
import stroom.util.shared.UserRef;
import stroom.util.shared.filter.FilterFieldDefinition;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Arrays;
import java.util.List;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class FindApiKeyCriteria extends BaseCriteria {

    public static final String FIELD_NAME = "Name";
    public static final String FIELD_PREFIX = "Prefix";
    public static final String FIELD_OWNER = "Owner";
    public static final String FIELD_COMMENTS = "Comments";
    public static final String FIELD_STATE = "State";
    public static final String FIELD_EXPIRE_TIME = "Expire Time";

    public static final FilterFieldDefinition FIELD_DEF_NAME = FilterFieldDefinition.defaultField(FIELD_NAME);
    public static final FilterFieldDefinition FIELD_DEF_PREFIX = FilterFieldDefinition.defaultField(FIELD_PREFIX);
    public static final FilterFieldDefinition FIELD_DEF_OWNER_DISPLAY_NAME = FilterFieldDefinition.qualifiedField(
            FIELD_OWNER);
    public static final FilterFieldDefinition FIELD_DEF_COMMENTS = FilterFieldDefinition.qualifiedField(
            FIELD_COMMENTS);
    public static final FilterFieldDefinition FIELD_DEF_ENABLED = FilterFieldDefinition.qualifiedField(
            FIELD_STATE);

    public static final List<FilterFieldDefinition> FILTER_FIELD_DEFINITIONS = Arrays.asList(
            FIELD_DEF_NAME,
            FIELD_DEF_PREFIX,
            FIELD_DEF_OWNER_DISPLAY_NAME,
            FIELD_DEF_COMMENTS,
            FIELD_DEF_ENABLED);

    @JsonProperty
    private String quickFilterInput;
    @JsonProperty
    private UserRef owner;

    public FindApiKeyCriteria() {
    }

    @JsonCreator
    public FindApiKeyCriteria(@JsonProperty("pageRequest") final PageRequest pageRequest,
                              @JsonProperty("sortList") final List<CriteriaFieldSort> sortList,
                              @JsonProperty("quickFilterInput") final String quickFilterInput,
                              @JsonProperty("owner") final UserRef owner) {
        super(pageRequest, sortList);
        this.quickFilterInput = quickFilterInput;
        this.owner = owner;
    }

    public static FindApiKeyCriteria create(final String quickFilterInput) {
        FindApiKeyCriteria findApiKeyCriteria = new FindApiKeyCriteria();
        findApiKeyCriteria.setQuickFilterInput(quickFilterInput);
        return findApiKeyCriteria;
    }

    public static FindApiKeyCriteria create(final UserRef owner) {
        FindApiKeyCriteria findApiKeyCriteria = new FindApiKeyCriteria();
        findApiKeyCriteria.setOwner(owner);
        return findApiKeyCriteria;
    }

    public static FindApiKeyCriteria create(final String quickFilterInput, final UserRef owner) {
        FindApiKeyCriteria findApiKeyCriteria = new FindApiKeyCriteria();
        findApiKeyCriteria.setQuickFilterInput(quickFilterInput);
        findApiKeyCriteria.setOwner(owner);
        return findApiKeyCriteria;
    }

    public String getQuickFilterInput() {
        return quickFilterInput;
    }

    public void setQuickFilterInput(final String quickFilterInput) {
        this.quickFilterInput = quickFilterInput;
    }

    public UserRef getOwner() {
        return owner;
    }

    public void setOwner(final UserRef owner) {
        this.owner = owner;
    }
}
