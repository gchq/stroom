package stroom.annotation.shared;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = StringEntryValue.class, name = "string"),
        @JsonSubTypes.Type(value = UserRefEntryValue.class, name = "user")
})
public sealed interface EntryValue permits StringEntryValue, UserRefEntryValue {

    String asUiValue();

    String asPersistedValue();
}
