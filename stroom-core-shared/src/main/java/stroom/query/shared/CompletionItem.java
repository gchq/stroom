package stroom.query.shared;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = CompletionValue.class, name = "value"),
        @JsonSubTypes.Type(value = CompletionSnippet.class, name = "snippet")
})
public sealed interface CompletionItem permits CompletionValue, CompletionSnippet {

    String getCaption();

    String getMeta();

    int getScore();

    String getTooltip();
}
