package stroom.planb.impl.db.state;

import stroom.bytebuffer.ByteBufferUtils;
import stroom.pipeline.refdata.store.FastInfosetUtil;
import stroom.pipeline.refdata.store.FastInfosetValue;
import stroom.pipeline.refdata.store.StringValue;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValNull;
import stroom.query.language.functions.ValString;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.nio.ByteBuffer;

@JsonPropertyOrder({"typeId", "byteBuffer"})
@JsonInclude(Include.NON_NULL)
public class StateValue {

    private static final ValString STRING = ValString.create("String");
    private static final ValString FAST_INFOSET = ValString.create("Fast Infoset");

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

    @JsonIgnore
    public Val getType() {
        return switch (typeId) {
            case stroom.pipeline.refdata.store.StringValue.TYPE_ID -> STRING;
            case stroom.pipeline.refdata.store.FastInfosetValue.TYPE_ID -> FAST_INFOSET;
            default -> ValNull.INSTANCE;
        };
    }

    @JsonIgnore
    public Val getValue() {
        return ValString.create(toString());
    }

    @Override
    public String toString() {
        return switch (typeId) {
            case StringValue.TYPE_ID -> ByteBufferUtils.toString(byteBuffer);
            case FastInfosetValue.TYPE_ID -> FastInfosetUtil.byteBufferToString(byteBuffer);
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
