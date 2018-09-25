package stroom.ui.config.shared;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import stroom.docref.SharedObject;

public class UrlConfig implements SharedObject {
    private String ui = null;
    private String authenticationService = "http://auth-service:8099/authentication/v1";
    private String users = "http://IP_ADDRESS/users";
    private String apiKeys = "http://IP_ADDRESS/tokens";
    private String changepassword = "http://IP_ADDRESS/changepassword";
    private String kibana = null;
    private String trackers = null;
    private String annotations = "http://IP_ADDRESS/annotationsService/queryApi/v1";
    private String elastic = "http://IP_ADDRESS/queryElasticService/queryApi/v1";

    public UrlConfig() {
        // Default constructor necessary for GWT serialisation.
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

    @Override
    public String toString() {
        return "UrlConfig{" +
                "ui='" + ui + '\'' +
                ", authenticationService='" + authenticationService + '\'' +
                ", users='" + users + '\'' +
                ", apiKeys='" + apiKeys + '\'' +
                ", changepassword='" + changepassword + '\'' +
                ", kibana='" + kibana + '\'' +
                ", trackers='" + trackers + '\'' +
                ", annotations='" + annotations + '\'' +
                ", elastic='" + elastic + '\'' +
                '}';
    }
}
