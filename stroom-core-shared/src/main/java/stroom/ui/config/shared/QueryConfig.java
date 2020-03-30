package stroom.ui.config.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import stroom.util.shared.AbstractConfig;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Objects;

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

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final QueryConfig that = (QueryConfig) o;
        return Objects.equals(infoPopup, that.infoPopup);
    }

    @Override
    public int hashCode() {
        return Objects.hash(infoPopup);
    }
}
