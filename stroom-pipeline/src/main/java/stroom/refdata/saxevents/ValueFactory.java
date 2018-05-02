package stroom.refdata.saxevents;

import stroom.util.logging.LambdaLogger;

import java.nio.ByteBuffer;

class ValueFactory {


    static AbstractPoolValue fromByteBuffer(final ByteBuffer byteBuffer) {

        final short typeId = byteBuffer.getShort();

        final AbstractPoolValue value;
        if (typeId == FastInfosetValue.TYPE_ID) {
            value = FastInfosetValue.fromByteBuffer(byteBuffer);
        } else if (typeId == StringValue.TYPE_ID){
            value = StringValue.fromByteBuffer(byteBuffer);
        } else {
            throw new RuntimeException(LambdaLogger.buildMessage("Unexpected typeId value {}", typeId));
        }

        return value;
    }





}
