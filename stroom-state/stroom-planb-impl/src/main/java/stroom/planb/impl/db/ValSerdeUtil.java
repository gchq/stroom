package stroom.planb.impl.db;

import stroom.bytebuffer.ByteBufferUtils;
import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.query.language.functions.Type;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValBoolean;
import stroom.query.language.functions.ValByte;
import stroom.query.language.functions.ValDate;
import stroom.query.language.functions.ValDouble;
import stroom.query.language.functions.ValDuration;
import stroom.query.language.functions.ValErr;
import stroom.query.language.functions.ValFloat;
import stroom.query.language.functions.ValInteger;
import stroom.query.language.functions.ValLong;
import stroom.query.language.functions.ValNull;
import stroom.query.language.functions.ValShort;
import stroom.query.language.functions.ValString;
import stroom.query.language.functions.ValXml;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import java.util.function.Function;

public class ValSerdeUtil {

//    public static int getLength(final Val val) {
//        return switch (val.type()) {
//            case NULL -> 1;
//            case BOOLEAN -> 2;
//            case BYTE -> 1 + Byte.BYTES;
//            case SHORT -> 1 + Short.BYTES;
//            case INTEGER -> 1 + Integer.BYTES;
//            case LONG -> 1 + Long.BYTES;
//            case FLOAT -> 1 + Float.BYTES;
//            case DOUBLE -> 1 + Double.BYTES;
//            case DATE -> 1 + Long.BYTES;
//            case STRING -> 1 + val.toString().getBytes(StandardCharsets.UTF_8).length;
//            case ERR -> 1 + val.toString().getBytes(StandardCharsets.UTF_8).length;
//            case DURATION -> 1 + Long.BYTES;
//            case XML -> 1 + ((ValXml) val).getByteBuffer().limit();
//        };
//    }

    public static <R> R write(final Val val,
                              final ByteBuffers byteBuffers,
                              final Function<ByteBuffer, R> function) {
        return write(val, byteBuffers, 0, bb -> {
        }, function);
    }

    public static <R> R write(final Val val,
                              final ByteBuffers byteBuffers,
                              final int prefixLength,
                              final Consumer<ByteBuffer> prefixConsumer,
                              final Function<ByteBuffer, R> function) {
        final Type type = val.type();
        return switch (type) {
            case NULL -> byteBuffers.use(prefixLength + 1, byteBuffer -> {
                prefixConsumer.accept(byteBuffer);
                byteBuffer.put(type.getId());
                byteBuffer.flip();
                return function.apply(byteBuffer);
            });
            case BOOLEAN -> byteBuffers.use(prefixLength + 2, byteBuffer -> {
                prefixConsumer.accept(byteBuffer);
                byteBuffer.put(type.getId());
                byteBuffer.put(val.toBoolean()
                        ? (byte) 1
                        : (byte) 0);
                byteBuffer.flip();
                return function.apply(byteBuffer);
            });
            case BYTE -> byteBuffers.use(prefixLength + 2, byteBuffer -> {
                prefixConsumer.accept(byteBuffer);
                byteBuffer.put(type.getId());
                byteBuffer.put(((ValByte) val).getValue());
                byteBuffer.flip();
                return function.apply(byteBuffer);
            });
            case SHORT -> byteBuffers.use(prefixLength + 1 + Short.BYTES, byteBuffer -> {
                prefixConsumer.accept(byteBuffer);
                byteBuffer.put(type.getId());
                byteBuffer.putShort(((ValShort) val).getValue());
                byteBuffer.flip();
                return function.apply(byteBuffer);
            });
            case INTEGER -> byteBuffers.use(prefixLength + 1 + Integer.BYTES, byteBuffer -> {
                prefixConsumer.accept(byteBuffer);
                byteBuffer.put(type.getId());
                byteBuffer.putInt(val.toInteger());
                byteBuffer.flip();
                return function.apply(byteBuffer);
            });
            case LONG, DURATION, DATE -> byteBuffers.use(prefixLength + 1 + Long.BYTES, byteBuffer -> {
                prefixConsumer.accept(byteBuffer);
                byteBuffer.put(type.getId());
                byteBuffer.putLong(val.toLong());
                byteBuffer.flip();
                return function.apply(byteBuffer);
            });
            case FLOAT -> byteBuffers.use(prefixLength + 1 + Float.BYTES, byteBuffer -> {
                prefixConsumer.accept(byteBuffer);
                byteBuffer.put(type.getId());
                byteBuffer.putFloat(val.toFloat());
                byteBuffer.flip();
                return function.apply(byteBuffer);
            });
            case DOUBLE -> byteBuffers.use(prefixLength + 1 + Double.BYTES, byteBuffer -> {
                prefixConsumer.accept(byteBuffer);
                byteBuffer.put(type.getId());
                byteBuffer.putDouble(val.toDouble());
                byteBuffer.flip();
                return function.apply(byteBuffer);
            });
            case STRING, ERR -> {
                final byte[] bytes = val.toString().getBytes(StandardCharsets.UTF_8);
                yield byteBuffers.use(prefixLength + 1 + bytes.length, byteBuffer -> {
                    prefixConsumer.accept(byteBuffer);
                    byteBuffer.put(type.getId());
                    byteBuffer.put(bytes);
                    byteBuffer.flip();
                    return function.apply(byteBuffer);
                });
            }
            case XML -> {
                final ByteBuffer bb = ((ValXml) val).getByteBuffer();
                yield byteBuffers.use(prefixLength + 1 + bb.remaining(), byteBuffer -> {
                    prefixConsumer.accept(byteBuffer);
                    byteBuffer.put(type.getId());
                    byteBuffer.put(bb);
                    byteBuffer.flip();
                    return function.apply(byteBuffer);
                });
            }
        };
    }

//    public static void write(final Val val, final ByteBuffer byteBuffer) {
//        final Type type = val.type();
//        byteBuffer.put(type.getId());
//        switch (type) {
//            case NULL -> {
//            }
//            case BOOLEAN -> byteBuffer.put(val.toBoolean()
//                    ? (byte) 1
//                    : (byte) 0);
//            case BYTE -> byteBuffer.put(((ValByte) val).getValue());
//            case SHORT -> byteBuffer.putShort(((ValShort) val).getValue());
//            case INTEGER -> byteBuffer.putInt(val.toInteger());
//            case LONG -> byteBuffer.putLong(val.toLong());
//            case FLOAT -> byteBuffer.putFloat(val.toFloat());
//            case DOUBLE -> byteBuffer.putDouble(val.toDouble());
//            case DATE -> byteBuffer.putLong(val.toLong());
//            case DURATION -> byteBuffer.putLong(val.toLong());
//            case STRING -> byteBuffer.put(val.toString().getBytes(StandardCharsets.UTF_8));
//            case XML -> byteBuffer.put(((ValXml) val).getByteBuffer());
//            case ERR -> byteBuffer.put(val.toString().getBytes(StandardCharsets.UTF_8));
//        }
//    }

    public static Val read(final ByteBuffer byteBuffer) {
        final byte b = byteBuffer.get();
        final Type type = Type.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(b);
        return switch (type) {
            case NULL -> ValNull.INSTANCE;
            case BOOLEAN -> ValBoolean.create(byteBuffer.get() != 0);
            case BYTE -> ValByte.create(byteBuffer.get());
            case SHORT -> ValShort.create(byteBuffer.getShort());
            case INTEGER -> ValInteger.create(byteBuffer.getInt());
            case LONG -> ValLong.create(byteBuffer.getLong());
            case FLOAT -> ValFloat.create(byteBuffer.getFloat());
            case DOUBLE -> ValDouble.create(byteBuffer.getDouble());
            case DATE -> ValDate.create(byteBuffer.getLong());
            case DURATION -> ValDuration.create(byteBuffer.getLong());
            case STRING -> ValString.create(ByteBufferUtils.toString(byteBuffer));
            case XML -> ValXml.create(ByteBuffer.wrap(ByteBufferUtils.getBytes(byteBuffer)));
            case ERR -> ValErr.create(ByteBufferUtils.toString(byteBuffer));
        };
    }
}
