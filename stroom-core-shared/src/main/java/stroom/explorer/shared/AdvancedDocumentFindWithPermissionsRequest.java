package stroom.explorer.shared;

import stroom.query.api.v2.ExpressionOperator;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.CriteriaFieldSort;
import stroom.util.shared.PageRequest;
import stroom.util.shared.UserRef;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Set;

@JsonInclude(Include.NON_NULL)
public class AdvancedDocumentFindWithPermissionsRequest extends AdvancedDocumentFindRequest {

    @JsonProperty
    private final UserRef userRef;
    @JsonProperty
    private final boolean explicitPermission;

    @JsonCreator
    public AdvancedDocumentFindWithPermissionsRequest(@JsonProperty("pageRequest") final PageRequest pageRequest,
                                                      @JsonProperty("sortList") final List<CriteriaFieldSort> sortList,
                                                      @JsonProperty("expression") final ExpressionOperator expression,
                                                      @JsonProperty("requiredPermissions") final Set<DocumentPermission>
                                                              requiredPermissions,
                                                      @JsonProperty("userRef") final UserRef userRef,
                                                      @JsonProperty("explicitPermission") final boolean
                                                              explicitPermission) {
        super(pageRequest, sortList, expression, requiredPermissions);
        this.userRef = userRef;
        this.explicitPermission = explicitPermission;
    }

    public UserRef getUserRef() {
        return userRef;
    }

    public boolean isExplicitPermission() {
        return explicitPermission;
    }

    public static class Builder extends AbstractBuilder<AdvancedDocumentFindWithPermissionsRequest, Builder> {

        private Set<DocumentPermission> requiredPermissions;
        private UserRef userRef;
        private boolean explicitPermission = true;

        public Builder() {

        }

        public Builder(final AdvancedDocumentFindWithPermissionsRequest expressionCriteria) {
            super(expressionCriteria);
            this.requiredPermissions = expressionCriteria.getRequiredPermissions();
            this.userRef = expressionCriteria.userRef;
        }

        public Builder requiredPermissions(final Set<DocumentPermission> requiredPermissions) {
            this.requiredPermissions = requiredPermissions;
            return self();
        }

        public Builder userRef(final UserRef userRef) {
            this.userRef = userRef;
            return self();
        }

        public Builder explicitPermission(final boolean explicitPermission) {
            this.explicitPermission = explicitPermission;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public AdvancedDocumentFindWithPermissionsRequest build() {
            return new AdvancedDocumentFindWithPermissionsRequest(
                    pageRequest,
                    sortList,
                    expression,
                    requiredPermissions,
                    userRef,
                    explicitPermission);
        }
    }
}
