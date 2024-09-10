package stroom.explorer.shared;

import stroom.entity.shared.ExpressionCriteria;
import stroom.query.api.v2.ExpressionOperator;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.CriteriaFieldSort;
import stroom.util.shared.PageRequest;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Set;

@JsonInclude(Include.NON_NULL)
public class AdvancedDocumentFindRequest extends ExpressionCriteria {

    @JsonProperty
    private final Set<DocumentPermission> requiredPermissions;

    @JsonCreator
    public AdvancedDocumentFindRequest(@JsonProperty("pageRequest") final PageRequest pageRequest,
                                       @JsonProperty("sortList") final List<CriteriaFieldSort> sortList,
                                       @JsonProperty("expression") final ExpressionOperator expression,
                                       @JsonProperty("requiredPermissions") final Set<DocumentPermission>
                                               requiredPermissions) {
        super(pageRequest, sortList, expression);
        this.requiredPermissions = requiredPermissions;
    }

    public Set<DocumentPermission> getRequiredPermissions() {
        return requiredPermissions;
    }

    public static class Builder extends AbstractBuilder<AdvancedDocumentFindRequest, Builder> {

        private Set<DocumentPermission> requiredPermissions;

        public Builder() {

        }

        public Builder(final AdvancedDocumentFindRequest expressionCriteria) {
            super(expressionCriteria);
        }

        public Builder requiredPermissions(final Set<DocumentPermission> requiredPermissions) {
            this.requiredPermissions = requiredPermissions;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public AdvancedDocumentFindRequest build() {
            return new AdvancedDocumentFindRequest(pageRequest, sortList, expression, requiredPermissions);
        }
    }
}
