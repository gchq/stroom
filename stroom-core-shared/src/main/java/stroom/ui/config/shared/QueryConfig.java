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
@JsonInclude(Include.NON_NULL)
public class QueryConfig extends AbstractConfig {
    @JsonProperty
    private InfoPopupConfig infoPopup;

    public QueryConfig() {
        infoPopup = new InfoPopupConfig();
    }

    @Inject
    @JsonCreator
    public QueryConfig(@JsonProperty("infoPopup") final InfoPopupConfig infoPopup) {
        if (infoPopup != null) {
            this.infoPopup = infoPopup;
        } else {
            this.infoPopup = new InfoPopupConfig();
        }
    }

    public InfoPopupConfig getInfoPopup() {
        return infoPopup;
    }

    public void setInfoPopup(final InfoPopupConfig infoPopup) {
        this.infoPopup = infoPopup;
    }
}
