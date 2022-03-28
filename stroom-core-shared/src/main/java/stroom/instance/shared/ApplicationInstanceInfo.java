package stroom.instance.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
public class ApplicationInstanceInfo {

    @JsonProperty
    private final String uuid;
    @JsonProperty
    private final String userId;
    @JsonProperty
    private final long createTime;

    @JsonCreator
    public ApplicationInstanceInfo(
            @JsonProperty("uuid") final String uuid,
            @JsonProperty("userId") final String userId,
            @JsonProperty("createTime") final long createTime) {
        this.uuid = uuid;
        this.userId = userId;
        this.createTime = createTime;
    }

    public String getUuid() {
        return uuid;
    }

    public String getUserId() {
        return userId;
    }

    public long getCreateTime() {
        return createTime;
    }

    @Override
    public String toString() {
        return "ApplicationInstanceInfo{" +
                "uuid='" + uuid + '\'' +
                ", userId='" + userId + '\'' +
                ", createTime=" + createTime +
                '}';
    }
}
