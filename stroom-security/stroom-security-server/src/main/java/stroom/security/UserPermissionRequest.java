package stroom.security;

import com.fasterxml.jackson.annotation.JsonProperty;

import javax.validation.constraints.NotNull;

public class UserPermissionRequest {

  @JsonProperty
  @NotNull
  private String permission;

  public UserPermissionRequest(){}

  public UserPermissionRequest(String permission){
    this.permission = permission;
  }

  public String getPermission() {
    return permission;
  }
}
