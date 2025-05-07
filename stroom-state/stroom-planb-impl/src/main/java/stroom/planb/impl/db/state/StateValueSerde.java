package stroom.planb.impl.db.state;

import java.nio.ByteBuffer;
import java.util.function.Consumer;

public interface StateValueSerde {

    StateValue read(ByteBuffer byteBuffer);

    void write(StateValue value,
               Consumer<ByteBuffer> consumer);
}
