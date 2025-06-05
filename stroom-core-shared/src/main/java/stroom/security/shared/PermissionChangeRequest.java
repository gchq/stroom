package stroom.security.shared;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonInclude(Include.NON_NULL)
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = BulkDocumentPermissionChangeRequest.class,
                name = "BulkDocumentPermissionChangeRequest"),
        @JsonSubTypes.Type(value = SingleDocumentPermissionChangeRequest.class,
                name = "SingleDocumentPermissionChangeRequest"),
})
public sealed interface PermissionChangeRequest permits
        BulkDocumentPermissionChangeRequest,
        SingleDocumentPermissionChangeRequest {

}
