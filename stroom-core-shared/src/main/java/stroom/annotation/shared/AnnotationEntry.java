package stroom.annotation.shared;

import stroom.util.shared.UserRef;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
public class AnnotationEntry {

    @JsonProperty
    private Long id;
    @JsonProperty
    private Long entryTime;
    @JsonProperty
    private UserRef entryUser;
    @JsonProperty
    private String entryType;
    @JsonProperty
    private EntryValue entryValue;

    public AnnotationEntry() {
    }

    @JsonCreator
    public AnnotationEntry(@JsonProperty("id") final Long id,
                           @JsonProperty("entryTime") final Long entryTime,
                           @JsonProperty("entryUser") final UserRef entryUser,
                           @JsonProperty("entryType") final String entryType,
                           @JsonProperty("entryValue") final EntryValue entryValue) {
        this.id = id;
        this.entryTime = entryTime;
        this.entryUser = entryUser;
        this.entryType = entryType;
        this.entryValue = entryValue;
    }

    public Long getId() {
        return id;
    }

    public void setId(final Long id) {
        this.id = id;
    }

    public Long getEntryTime() {
        return entryTime;
    }

    public void setEntryTime(final Long entryTime) {
        this.entryTime = entryTime;
    }

    public UserRef getEntryUser() {
        return entryUser;
    }

    public void setEntryUser(final UserRef entryUser) {
        this.entryUser = entryUser;
    }

    public String getEntryType() {
        return entryType;
    }

    public void setEntryType(final String entryType) {
        this.entryType = entryType;
    }

    public EntryValue getEntryValue() {
        return entryValue;
    }

    public void setEntryValue(final EntryValue entryValue) {
        this.entryValue = entryValue;
    }

    @Override
    public String toString() {
        return "AnnotationEntry{" +
                "id=" + id +
                ", entryTime=" + entryTime +
                ", entryUser=" + entryUser +
                ", entryType='" + entryType + '\'' +
                ", entryValue=" + entryValue +
                '}';
    }
}
