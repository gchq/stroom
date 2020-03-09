package stroom.annotation.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
public class AnnotationEntry {
    @JsonProperty
    private Long id;
    @JsonProperty
    private Integer version;
    @JsonProperty
    private Long createTime;
    @JsonProperty
    private String createUser;
    @JsonProperty
    private Long updateTime;
    @JsonProperty
    private String updateUser;
    @JsonProperty
    private String entryType;
    @JsonProperty
    private String data;

    public AnnotationEntry() {
    }

    @JsonCreator
    public AnnotationEntry(@JsonProperty("id") final Long id,
                           @JsonProperty("version") final Integer version,
                           @JsonProperty("createTime") final Long createTime,
                           @JsonProperty("createUser") final String createUser,
                           @JsonProperty("updateTime") final Long updateTime,
                           @JsonProperty("updateUser") final String updateUser,
                           @JsonProperty("entryType") final String entryType,
                           @JsonProperty("data") final String data) {
        this.id = id;
        this.version = version;
        this.createTime = createTime;
        this.createUser = createUser;
        this.updateTime = updateTime;
        this.updateUser = updateUser;
        this.entryType = entryType;
        this.data = data;
    }

    public Long getId() {
        return id;
    }

    public void setId(final Long id) {
        this.id = id;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(final Integer version) {
        this.version = version;
    }

    public Long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(final Long createTime) {
        this.createTime = createTime;
    }

    public String getCreateUser() {
        return createUser;
    }

    public void setCreateUser(final String createUser) {
        this.createUser = createUser;
    }

    public Long getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(final Long updateTime) {
        this.updateTime = updateTime;
    }

    public String getUpdateUser() {
        return updateUser;
    }

    public void setUpdateUser(final String updateUser) {
        this.updateUser = updateUser;
    }

    public String getEntryType() {
        return entryType;
    }

    public void setEntryType(final String entryType) {
        this.entryType = entryType;
    }

    public String getData() {
        return data;
    }

    public void setData(final String data) {
        this.data = data;
    }
}
