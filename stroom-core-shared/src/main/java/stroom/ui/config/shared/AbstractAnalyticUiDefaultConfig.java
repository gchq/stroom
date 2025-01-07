package stroom.ui.config.shared;

import stroom.docref.DocRef;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public abstract class AbstractAnalyticUiDefaultConfig extends AbstractConfig implements IsStroomConfig {
    public abstract String getDefaultNode();

    public abstract DocRef getDefaultErrorFeed();

    public abstract DocRef getDefaultDestinationFeed();

    public abstract String getDefaultSubjectTemplate();

    public abstract String getDefaultBodyTemplate();

}
