package stroom.state.impl.dao;

import java.nio.ByteBuffer;

public record RangedState(
        long keyStart,
        long keyEnd,
        byte typeId,
        ByteBuffer value) {

}
