package stroom.index.impl;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.annotation.Nullable;

@JsonInclude(Include.NON_NULL)
public class UpdateVolumeDTO extends CreateVolumeDTO {
    @JsonProperty
    private int id;

    public UpdateVolumeDTO() {
    }

    @JsonCreator
    public UpdateVolumeDTO(@JsonProperty("nodeName") @Nullable final String nodeName,
                           @JsonProperty("path") @Nullable final String path,
                           @JsonProperty("indexVolumeGroupName") final String indexVolumeGroupName,
                           @JsonProperty("id") final int id) {
        super(nodeName, path, indexVolumeGroupName);
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }
}
