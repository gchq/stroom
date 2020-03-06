package stroom.ui.config.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import stroom.util.shared.AbstractConfig;

@JsonPropertyOrder({"ui", "authenticationService", "users", "apiKeys", "indexVolumes", "indexVolumeGroups", "userAuthorisation", "groupAuthorisation", "editDoc", "changepassword", "kibana", "trackers", "annotations", "elastic", "documentPermissions"})
@JsonInclude(Include.NON_DEFAULT)
public class UrlConfig extends AbstractConfig {
    @JsonProperty
    @JsonPropertyDescription("The URL of Stroom as provided to the browser")
    private String ui;
    @JsonProperty
    @JsonPropertyDescription("The URL of the authentication service")
    private String authenticationService;
    @JsonProperty
    private String users;
    @JsonProperty
    private String apiKeys;
    @JsonProperty
    private String indexVolumes;
    @JsonProperty
    private String indexVolumeGroups;
    @JsonProperty
    private String userAuthorisation;
    @JsonProperty
    private String groupAuthorisation;
    @JsonProperty
    private String editDoc;
    @JsonProperty
    private String changepassword;
    @JsonProperty
    private String kibana;
    @JsonProperty
    private String trackers;
    @JsonProperty
    private String annotations;
    @JsonProperty
    private String elastic;
    @JsonProperty
    private String documentPermissions;

    public UrlConfig() {
        setDefaults();
    }

    @JsonCreator
    public UrlConfig(@JsonProperty("ui") final String ui,
                     @JsonProperty("authenticationService") final String authenticationService,
                     @JsonProperty("users") final String users,
                     @JsonProperty("apiKeys") final String apiKeys,
                     @JsonProperty("indexVolumes") final String indexVolumes,
                     @JsonProperty("indexVolumeGroups") final String indexVolumeGroups,
                     @JsonProperty("userAuthorisation") final String userAuthorisation,
                     @JsonProperty("groupAuthorisation") final String groupAuthorisation,
                     @JsonProperty("editDoc") final String editDoc,
                     @JsonProperty("changepassword") final String changepassword,
                     @JsonProperty("kibana") final String kibana,
                     @JsonProperty("trackers") final String trackers,
                     @JsonProperty("annotations") final String annotations,
                     @JsonProperty("elastic") final String elastic,
                     @JsonProperty("documentPermissions") final String documentPermissions) {
        this.ui = ui;
        this.authenticationService = authenticationService;
        this.users = users;
        this.apiKeys = apiKeys;
        this.indexVolumes = indexVolumes;
        this.indexVolumeGroups = indexVolumeGroups;
        this.userAuthorisation = userAuthorisation;
        this.groupAuthorisation = groupAuthorisation;
        this.editDoc = editDoc;
        this.changepassword = changepassword;
        this.kibana = kibana;
        this.trackers = trackers;
        this.annotations = annotations;
        this.elastic = elastic;
        this.documentPermissions = documentPermissions;

        setDefaults();
    }

    private void setDefaults() {
        if (ui == null) {
            ui = "http://IP_ADDRESS";
        }
        if (authenticationService == null) {
            authenticationService = "http://auth-service:8099/authentication/v1";
        }
        if (users == null) {
            users = "http://IP_ADDRESS/users";
        }
        if (apiKeys == null) {
            apiKeys = "http://IP_ADDRESS/tokens";
        }
        if (indexVolumes == null) {
            indexVolumes = "http://IP_ADDRESS/s/indexing/volumes";
        }
        if (indexVolumeGroups == null) {
            indexVolumeGroups = "http://IP_ADDRESS/s/indexing/groups";
        }
        if (userAuthorisation == null) {
            userAuthorisation = "http://IP_ADDRESS/s/authorisationManager/false";
        }
        if (groupAuthorisation == null) {
            groupAuthorisation = "http://IP_ADDRESS/s/authorisationManager/true";
        }
        if (editDoc == null) {
            editDoc = "http://IP_ADDRESS/s/doc/";
        }
        if (changepassword == null) {
            changepassword = "http://IP_ADDRESS/changepassword";
        }
        if (annotations == null) {
            annotations = "http://IP_ADDRESS/annotationsService/queryApi/v1";
        }
        if (elastic == null) {
            elastic = "http://IP_ADDRESS/queryElasticService/queryApi/v1";
        }
        if (documentPermissions == null) {
            documentPermissions = "http://IP_ADDRESS/s/authorisationManager/document/";
        }
    }

    public String getUi() {
        return ui;
    }

    public void setUi(final String ui) {
        this.ui = ui;
    }

    public String getAuthenticationService() {
        return authenticationService;
    }

    public void setAuthenticationService(final String authenticationService) {
        this.authenticationService = authenticationService;
    }

    public String getUsers() {
        return users;
    }

    public void setUsers(final String users) {
        this.users = users;
    }

    public String getApiKeys() {
        return apiKeys;
    }

    public void setApiKeys(final String apiKeys) {
        this.apiKeys = apiKeys;
    }

    public String getIndexVolumes() {
        return indexVolumes;
    }

    public void setIndexVolumes(String indexVolumes) {
        this.indexVolumes = indexVolumes;
    }

    public String getChangepassword() {
        return changepassword;
    }

    public void setChangepassword(final String changepassword) {
        this.changepassword = changepassword;
    }

    public String getKibana() {
        return kibana;
    }

    public void setKibana(final String kibana) {
        this.kibana = kibana;
    }

    public String getTrackers() {
        return trackers;
    }

    public void setTrackers(final String trackers) {
        this.trackers = trackers;
    }

    public String getAnnotations() {
        return annotations;
    }

    public void setAnnotations(final String annotations) {
        this.annotations = annotations;
    }

    public String getElastic() {
        return elastic;
    }

    public void setElastic(final String elastic) {
        this.elastic = elastic;
    }

    public String getIndexVolumeGroups() {
        return indexVolumeGroups;
    }

    public void setIndexVolumeGroups(String indexVolumeGroups) {
        this.indexVolumeGroups = indexVolumeGroups;
    }

    public String getUserAuthorisation() {
        return userAuthorisation;
    }

    public void setUserAuthorisation(String userAuthorisation) {
        this.userAuthorisation = userAuthorisation;
    }

    public String getGroupAuthorisation() {
        return groupAuthorisation;
    }

    public void setGroupAuthorisation(String groupAuthorisation) {
        this.groupAuthorisation = groupAuthorisation;
    }

    public String getDocumentPermissions() {
        return documentPermissions;
    }

    public void setDocumentPermissions(String documentPermissions) {
        this.documentPermissions = documentPermissions;
    }

    public String getEditDoc() {
        return editDoc;
    }

    public void setEditDoc(String editDoc) {
        this.editDoc = editDoc;
    }

    @Override
    public String toString() {
        return "UrlConfig{" +
                "ui='" + ui + '\'' +
                ", authenticationService='" + authenticationService + '\'' +
                ", users='" + users + '\'' +
                ", apiKeys='" + apiKeys + '\'' +
                ", indexVolumes='" + indexVolumes + '\'' +
                ", indexVolumeGroups='" + indexVolumeGroups + '\'' +
                ", userAuthorisation='" + userAuthorisation + '\'' +
                ", groupAuthorisation='" + groupAuthorisation + '\'' +
                ", editDoc='" + editDoc + '\'' +
                ", changepassword='" + changepassword + '\'' +
                ", kibana='" + kibana + '\'' +
                ", trackers='" + trackers + '\'' +
                ", annotations='" + annotations + '\'' +
                ", elastic='" + elastic + '\'' +
                ", documentPermissions='" + documentPermissions + '\'' +
                '}';
    }

}
