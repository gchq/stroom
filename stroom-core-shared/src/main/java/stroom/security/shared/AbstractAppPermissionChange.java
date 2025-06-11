package stroom.security.shared;

import stroom.security.shared.AbstractAppPermissionChange.AddAppPermission;
import stroom.security.shared.AbstractAppPermissionChange.RemoveAppPermission;
import stroom.util.shared.UserRef;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = AddAppPermission.class, name = "AddAppPermission"),
        @JsonSubTypes.Type(value = RemoveAppPermission.class, name = "RemoveAppPermission"),
})
public abstract sealed class AbstractAppPermissionChange permits AddAppPermission, RemoveAppPermission {

    @JsonProperty
    private final UserRef userRef;
    @JsonProperty
    private final AppPermission permission;

    @JsonCreator
    public AbstractAppPermissionChange(@JsonProperty("userRef") final UserRef userRef,
                                       @JsonProperty("permission") final AppPermission permission) {
        Objects.requireNonNull(userRef, "Null user ref");
        Objects.requireNonNull(permission, "Null permission");
        this.userRef = userRef;
        this.permission = permission;
    }

    public UserRef getUserRef() {
        return userRef;
    }

    public AppPermission getPermission() {
        return permission;
    }

    @JsonInclude(Include.NON_NULL)
    public static final class AddAppPermission extends AbstractAppPermissionChange {


        @JsonCreator
        public AddAppPermission(@JsonProperty("userRef") final UserRef userRef,
                                @JsonProperty("permission") final AppPermission permission) {
            super(userRef, permission);
        }
    }

    @JsonInclude(Include.NON_NULL)
    public static final class RemoveAppPermission extends AbstractAppPermissionChange {

        @JsonCreator
        public RemoveAppPermission(@JsonProperty("userRef") final UserRef userRef,
                                   @JsonProperty("permission") final AppPermission permission) {
            super(userRef, permission);
        }
    }
}
