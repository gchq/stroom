package stroom.ui.config.shared;

import com.fasterxml.jackson.annotation.JsonProperty;
import stroom.docref.SharedObject;
import stroom.util.shared.AbstractConfig;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class QueryConfig extends AbstractConfig implements SharedObject {
    private InfoPopupConfig infoPopupConfig = new InfoPopupConfig();

    public QueryConfig() {
        // Default constructor necessary for GWT serialisation.
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
