package stroom.annotation.shared;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = StringEntryValue.class, name = "string"),
        @JsonSubTypes.Type(value = UserNameEntryValue.class, name = "user")
})
public interface EntryValue {

    String asUiValue();

    String asPersistedValue();
}
