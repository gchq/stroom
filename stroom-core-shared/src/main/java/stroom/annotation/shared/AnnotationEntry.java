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
    private Long updateTime;
    @JsonProperty
    private UserRef updateUser;
    @JsonProperty
    private AnnotationEntryType entryType;
    @JsonProperty
    private EntryValue entryValue;
    @JsonProperty
    private EntryValue previousValue;
    @JsonProperty
    private boolean deleted;

    public AnnotationEntry() {
    }

    @JsonCreator
    public AnnotationEntry(@JsonProperty("id") final Long id,
                           @JsonProperty("entryTime") final Long entryTime,
                           @JsonProperty("entryUser") final UserRef entryUser,
                           @JsonProperty("updateTime") final Long updateTime,
                           @JsonProperty("updateUser") final UserRef updateUser,
                           @JsonProperty("entryType") final AnnotationEntryType entryType,
                           @JsonProperty("entryValue") final EntryValue entryValue,
                           @JsonProperty("previousValue") final EntryValue previousValue,
                           @JsonProperty("deleted") final boolean deleted) {
        this.id = id;
        this.entryTime = entryTime;
        this.entryUser = entryUser;
        this.entryType = entryType;
        this.entryValue = entryValue;
        this.updateTime = updateTime;
        this.updateUser = updateUser;
        this.previousValue = previousValue;
        this.deleted = deleted;
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

    public AnnotationEntryType getEntryType() {
        return entryType;
    }

    public void setEntryType(final AnnotationEntryType entryType) {
        this.entryType = entryType;
    }

    public EntryValue getEntryValue() {
        return entryValue;
    }

    public void setEntryValue(final EntryValue entryValue) {
        this.entryValue = entryValue;
    }

    public Long getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(final Long updateTime) {
        this.updateTime = updateTime;
    }

    public UserRef getUpdateUser() {
        return updateUser;
    }

    public void setUpdateUser(final UserRef updateUser) {
        this.updateUser = updateUser;
    }

    public void setPreviousValue(final EntryValue previousValue) {
        this.previousValue = previousValue;
    }

    public EntryValue getPreviousValue() {
        return previousValue;
    }

    public void setDeleted(final boolean deleted) {
        this.deleted = deleted;
    }

    public boolean isDeleted() {
        return deleted;
    }

    @Override
    public String toString() {
        return "AnnotationEntry{" +
               "id=" + id +
               ", entryTime=" + entryTime +
               ", entryUser=" + entryUser +
               ", updateTime=" + updateTime +
               ", updateUser=" + updateUser +
               ", entryType=" + entryType +
               ", entryValue=" + entryValue +
               ", previousValue=" + previousValue +
               ", deleted=" + deleted +
               '}';
    }
}
