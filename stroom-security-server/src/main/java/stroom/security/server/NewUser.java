package stroom.security.server;

import com.fasterxml.jackson.annotation.JsonProperty;

public class NewUser {
    @JsonProperty
    private String name;
    @JsonProperty
    private boolean group;

    public NewUser() {}

    public NewUser(String name, boolean group) {
        this.name = name;
        this.group = group;
    }

    public String getName() {
        return name;
    }

    public boolean isGroup() {
        return group;
    }
}
