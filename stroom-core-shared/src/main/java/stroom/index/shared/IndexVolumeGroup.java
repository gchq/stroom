package stroom.index.shared;

import stroom.docref.DocRef;
import stroom.docref.DocRef.TypedBuilder;
import stroom.docref.HasDocRef;
import stroom.docref.HasNameMutable;
import stroom.util.shared.HasAuditInfo;
import stroom.util.shared.HasIntegerId;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class IndexVolumeGroup implements HasAuditInfo, HasIntegerId, HasNameMutable, HasDocRef {

    public static final String DOCUMENT_TYPE = "IndexVolumeGroup";

    // This uuid will be used for the auto created volume group.
    // It is hard coded so that every stroom env that sets up a default volume
    // will have the same uuid for it, which makes imp/exp between instances easier,
    // similar to how context pack entities have fixed UUIDs.
    public static final String DEFAULT_VOLUME_UUID = "5de2d603-cfc7-45cf-a8b4-e06bdf454f5e";
    public static final String DEFAULT_VOLUME_NAME = "Default Volume Group";

    @JsonProperty
    private Integer id;
    @JsonProperty
    private Integer version;
    @JsonProperty
    private Long createTimeMs;
    @JsonProperty
    private String createUser;
    @JsonProperty
    private Long updateTimeMs;
    @JsonProperty
    private String updateUser;
    @JsonProperty
    private String name;
    @JsonProperty
    private String uuid;
    @JsonProperty
    private boolean defaultVolume;

    public IndexVolumeGroup() {
    }

    @JsonCreator
    public IndexVolumeGroup(@JsonProperty("id") final Integer id,
                            @JsonProperty("version") final Integer version,
                            @JsonProperty("createTimeMs") final Long createTimeMs,
                            @JsonProperty("createUser") final String createUser,
                            @JsonProperty("updateTimeMs") final Long updateTimeMs,
                            @JsonProperty("updateUser") final String updateUser,
                            @JsonProperty("name") final String name,
                            @JsonProperty("uuid") final String uuid,
                            @JsonProperty("defaultVolume") final boolean defaultVolume) {
        this.id = id;
        this.version = version;
        this.createTimeMs = createTimeMs;
        this.createUser = createUser;
        this.updateTimeMs = updateTimeMs;
        this.updateUser = updateUser;
        this.name = name;
        this.uuid = uuid;
        this.defaultVolume = defaultVolume;
    }

    private IndexVolumeGroup(final Builder builder) {
        setId(builder.id);
        setVersion(builder.version);
        setCreateTimeMs(builder.createTimeMs);
        setCreateUser(builder.createUser);
        setUpdateTimeMs(builder.updateTimeMs);
        setUpdateUser(builder.updateUser);
        setName(builder.name);
        setUuid(builder.uuid);
        setDefaultVolume(builder.defaultVolume);
    }

    public Builder copy() {
        Builder builder = new Builder();
        builder.id = this.getId();
        builder.version = this.getVersion();
        builder.createTimeMs = this.getCreateTimeMs();
        builder.createUser = this.getCreateUser();
        builder.updateTimeMs = this.getUpdateTimeMs();
        builder.updateUser = this.getUpdateUser();
        builder.name = this.getName();
        builder.uuid = this.getUuid();
        builder.defaultVolume = this.isDefaultVolume();
        return builder;
    }

    @Override
    public Integer getId() {
        return id;
    }

    public void setId(final Integer id) {
        this.id = id;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(final Integer version) {
        this.version = version;
    }

    @Override
    public Long getCreateTimeMs() {
        return createTimeMs;
    }

    public void setCreateTimeMs(final Long createTimeMs) {
        this.createTimeMs = createTimeMs;
    }

    @Override
    public String getCreateUser() {
        return createUser;
    }

    public void setCreateUser(final String createUser) {
        this.createUser = createUser;
    }

    @Override
    public Long getUpdateTimeMs() {
        return updateTimeMs;
    }

    public void setUpdateTimeMs(final Long updateTimeMs) {
        this.updateTimeMs = updateTimeMs;
    }

    @Override
    public String getUpdateUser() {
        return updateUser;
    }

    public void setUpdateUser(final String updateUser) {
        this.updateUser = updateUser;
    }

    @Override
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String getType() {
        return null;
    }

    @Override
    public String getUuid() {
        return uuid;
    }

    public void setUuid(final String uuid) {
        this.uuid = uuid;
    }

    public boolean isDefaultVolume() {
        return defaultVolume;
    }

    public void setDefaultVolume(final boolean defaultVolume) {
        this.defaultVolume = defaultVolume;
    }

    public static TypedBuilder buildDocRef() {
        return DocRef.builder(DOCUMENT_TYPE);
    }

    public static DocRef buildDefaultVolumeGroupDocRef(final String name) {
        Objects.requireNonNull(name);
        return buildDocRef()
                .uuid(DEFAULT_VOLUME_UUID)
                .name(name)
                .build();
    }

    @Override
    public String toString() {
        return "IndexVolumeGroup{" +
                "id=" + id +
                ", version=" + version +
                ", createTimeMs=" + createTimeMs +
                ", createUser='" + createUser + '\'' +
                ", updateTimeMs=" + updateTimeMs +
                ", updateUser='" + updateUser + '\'' +
                ", name='" + name + '\'' +
                ", uuid='" + uuid + '\'' +
                ", defaultVolume='" + defaultVolume + '\'' +
                '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final IndexVolumeGroup that = (IndexVolumeGroup) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    public static Builder builder() {
        return new Builder();
    }


    // --------------------------------------------------------------------------------


    public static final class Builder {

        private Integer id;
        private Integer version;
        private Long createTimeMs;
        private String createUser;
        private Long updateTimeMs;
        private String updateUser;
        private String name;
        private String uuid;
        private boolean defaultVolume;

        private Builder() {
        }

        public Builder withId(final Integer val) {
            id = val;
            return this;
        }

        public Builder withVersion(final Integer val) {
            version = val;
            return this;
        }

        public Builder withCreateTimeMs(final Long val) {
            createTimeMs = val;
            return this;
        }

        public Builder withCreateUser(final String val) {
            createUser = val;
            return this;
        }

        public Builder withUpdateTimeMs(final Long val) {
            updateTimeMs = val;
            return this;
        }

        public Builder withUpdateUser(final String val) {
            updateUser = val;
            return this;
        }

        public Builder withName(final String val) {
            name = val;
            return this;
        }

        public Builder withUuid(final String val) {
            uuid = val;
            return this;
        }

        public Builder withDefaultVolume(final boolean val) {
            defaultVolume = val;
            return this;
        }

        public IndexVolumeGroup build() {
            return new IndexVolumeGroup(this);
        }
    }
}
