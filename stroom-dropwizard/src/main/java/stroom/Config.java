package stroom;

import io.dropwizard.Configuration;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

public class Config extends Configuration {

    @NotNull
    @Valid
    private String uiPath;

    public String getUiPath() {
        return uiPath;
    }
}
