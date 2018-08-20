package stroom.ui.config.shared;

import com.fasterxml.jackson.annotation.JsonProperty;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class QueryConfig {
    private InfoPopupConfig infoPopupConfig;

    public QueryConfig() {
        this.infoPopupConfig = new InfoPopupConfig();
    }

    @Inject
    public QueryConfig(final InfoPopupConfig infoPopupConfig) {
        this.infoPopupConfig = infoPopupConfig;
    }

    @JsonProperty("infoPopup")
    public InfoPopupConfig getInfoPopupConfig() {
        return infoPopupConfig;
    }

    public void setInfoPopupConfig(final InfoPopupConfig infoPopupConfig) {
        this.infoPopupConfig = infoPopupConfig;
    }
}
