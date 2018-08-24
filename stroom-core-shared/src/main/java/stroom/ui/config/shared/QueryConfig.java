package stroom.ui.config.shared;

import com.fasterxml.jackson.annotation.JsonProperty;
import stroom.docref.SharedObject;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class QueryConfig implements SharedObject {
    private InfoPopupConfig infoPopupConfig;

    public QueryConfig() {
        // Default constructor necessary for GWT serialisation.
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
