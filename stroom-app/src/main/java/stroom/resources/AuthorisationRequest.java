package stroom.resources;

public class AuthorisationRequest {
    private String documentType;
    private String documentUuid;
    private String permissions;

    public AuthorisationRequest(String documentType, String documentUuid, String permissions){
        this.documentType = documentType;
        this.documentUuid = documentUuid;
        this.permissions = permissions;
    }

    public String getDocumentType() {
        return documentType;
    }

    public String getDocumentUuid() {
        return documentUuid;
    }

    public String getPermissions() {
        return permissions;
    }
}
