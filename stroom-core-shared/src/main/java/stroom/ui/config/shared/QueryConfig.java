package stroom.ui.config.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import stroom.util.shared.AbstractConfig;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
@JsonPropertyOrder({"infoPopup"})
@JsonInclude(Include.NON_DEFAULT)
public class QueryConfig extends AbstractConfig {
    @JsonProperty("infoPopup")
    private InfoPopupConfig infoPopupConfig;

    public QueryConfig() {
        infoPopupConfig = new InfoPopupConfig();
    }

    @Inject
    @JsonCreator
    public QueryConfig(@JsonProperty("infoPopup") final InfoPopupConfig infoPopupConfig) {
        if (infoPopupConfig != null) {
            this.infoPopupConfig = infoPopupConfig;
        } else {
            this.infoPopupConfig = new InfoPopupConfig();
        }
    }

    public InfoPopupConfig getInfoPopupConfig() {
        return infoPopupConfig;
    }

    public void setInfoPopupConfig(final InfoPopupConfig infoPopupConfig) {
        this.infoPopupConfig = infoPopupConfig;
    }
}
