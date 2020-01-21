package stroom.ui.config.shared;

public class UiPathsConfig {
    private static final String NEW_UI_BASE = "/s";
//    private String ui = "";

    // TODO not sure where this lives as it is not really UI related
    private String authenticationService = "http://auth-service:8099/authentication/v1";

    private String users = "/users";
    private String apiKeys = "/tokens";
    private String changePassword = "/changepassword";

    private String indexVolumes = NEW_UI_BASE + "/indexing/volumes";
    private String indexVolumeGroups = NEW_UI_BASE + "/indexing/groups";
    private String userAuthorisation = NEW_UI_BASE + "/authorisationManager/false";
    private String groupAuthorisation = NEW_UI_BASE + "/authorisationManager/true";
    private String editDoc = NEW_UI_BASE + "/doc/";
    private String documentPermissions = NEW_UI_BASE + "/authorisationManager/document/";


//    private String kibana = null;
//    private String trackers = null;
//    private String annotations = "/annotationsService/queryApi/v1";
//    private String elastic = "/queryElasticService/queryApi/v1";
}
