package stroom.pathways.shared.pathway;


import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = NamePathKey.class, name = "name"),
        @JsonSubTypes.Type(value = NamesPathKey.class, name = "names"),
        @JsonSubTypes.Type(value = TerminalPathKey.class, name = "terminal")
})
public sealed interface PathKey permits NamePathKey, NamesPathKey, TerminalPathKey {

}
