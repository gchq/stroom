package stroom.planb.impl.db;

import stroom.pipeline.refdata.store.FastInfosetUtil;
import stroom.pipeline.refdata.store.FastInfosetValue;
import stroom.pipeline.refdata.store.StringValue;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

@JsonPropertyOrder({"typeId", "byteBuffer"})
@JsonInclude(Include.NON_NULL)
public class StateValue {

    @JsonProperty
    private final byte typeId;
    @JsonProperty
    private final ByteBuffer byteBuffer;

    @JsonCreator
    public StateValue(@JsonProperty("typeId") final byte typeId,
                      @JsonProperty("byteBuffer") final ByteBuffer byteBuffer) {
        this.typeId = typeId;
        this.byteBuffer = byteBuffer;
    }

    public byte getTypeId() {
        return typeId;
    }

    public ByteBuffer getByteBuffer() {
        return byteBuffer;
    }

    @Override
    public String toString() {
        return switch (typeId) {
            case StringValue.TYPE_ID -> new String(byteBuffer.duplicate().array(), StandardCharsets.UTF_8);
            case FastInfosetValue.TYPE_ID -> FastInfosetUtil.byteBufferToString(byteBuffer.duplicate());
            default -> null;
        };
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private byte typeId;
        private ByteBuffer byteBuffer;

        private Builder() {
        }

        private Builder(final StateValue value) {
            this.typeId = value.typeId;
            this.byteBuffer = value.byteBuffer;
        }

        public Builder typeId(final byte typeId) {
            this.typeId = typeId;
            return this;
        }

        public Builder byteBuffer(final ByteBuffer byteBuffer) {
            this.byteBuffer = byteBuffer;
            return this;
        }

        public StateValue build() {
            return new StateValue(typeId, byteBuffer);
        }
    }
}
