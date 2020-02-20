package stroom.ui.config.shared;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import stroom.util.shared.AbstractConfig;

public class UrlConfig extends AbstractConfig {
    private String ui = "http://IP_ADDRESS";
    private String authenticationService = "http://auth-service:8099/authentication/v1";
    private String users = "http://IP_ADDRESS/users";
    private String apiKeys = "http://IP_ADDRESS/tokens";
    private String indexVolumes = "http://IP_ADDRESS/s/indexing/volumes";
    private String indexVolumeGroups = "http://IP_ADDRESS/s/indexing/groups";
    private String userAuthorisation = "http://IP_ADDRESS/s/authorisationManager/false";
    private String groupAuthorisation = "http://IP_ADDRESS/s/authorisationManager/true";
    private String editDoc = "http://IP_ADDRESS/s/doc/";
    private String changepassword = "http://IP_ADDRESS/changepassword";
    private String kibana = null;
    private String trackers = null;
    private String annotations = "http://IP_ADDRESS/annotationsService/queryApi/v1";
    private String elastic = "http://IP_ADDRESS/queryElasticService/queryApi/v1";
    private String documentPermissions = "http://IP_ADDRESS/s/authorisationManager/document/";

    public UrlConfig() {
    }

    @JsonPropertyDescription("The URL of Stroom as provided to the browser")
    public String getUi() {
        return ui;
    }

    public void setUi(final String ui) {
        this.ui = ui;
    }

    @JsonPropertyDescription("The URL of the authentication service")
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
