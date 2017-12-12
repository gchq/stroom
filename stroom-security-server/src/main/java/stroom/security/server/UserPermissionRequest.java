package stroom.security.server;

import com.fasterxml.jackson.annotation.JsonProperty;

public class UserPermissionRequest {

  @JsonProperty
  private String permission;

  public UserPermissionRequest(){}

  public UserPermissionRequest(String permission){
    this.permission = permission;
  }

  public String getPermission() {
    return permission;
  }
}
