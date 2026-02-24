/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.planb.impl.serde.val;

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
import stroom.util.shared.NullSafe;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

public class ValSerdeUtil {

    private static final byte[] EMPTY_BYTES = new byte[0];

    public static byte[] getBytes(final Val val) {
        final String string = val.toString();
        return NullSafe.getOrElse(string, str -> str.getBytes(StandardCharsets.UTF_8), EMPTY_BYTES);
    }

    public static <R> R write(final Val val,
                              final ByteBuffers byteBuffers,
                              final Function<ByteBuffer, R> function) {
        return write(val, byteBuffers, function, Addition.NONE, Addition.NONE);
    }

    public static class Addition {

        public static final Addition NONE = new Addition(0, bb -> {
        });

        private final int length;
        private final Consumer<ByteBuffer> consumer;

        public Addition(final int length, final Consumer<ByteBuffer> consumer) {
            this.length = length;
            this.consumer = consumer;
        }

        public int getLength() {
            return length;
        }

        public Consumer<ByteBuffer> getConsumer() {
            return consumer;
        }
    }

    public static <R> R write(final Val val,
                              final ByteBuffers byteBuffers,
                              final Function<ByteBuffer, R> function,
                              final Addition prefix,
                              final Addition suffix) {
        final int len = prefix.length + 1 + suffix.length;
        final Type type = val.type();
        return switch (type) {
            case NULL -> byteBuffers.use(len, byteBuffer -> {
                prefix.getConsumer().accept(byteBuffer);
                byteBuffer.put(type.getId());
                suffix.getConsumer().accept(byteBuffer);
                byteBuffer.flip();
                return function.apply(byteBuffer);
            });
            case BOOLEAN -> byteBuffers.use(len + Byte.BYTES, byteBuffer -> {
                prefix.getConsumer().accept(byteBuffer);
                byteBuffer.put(type.getId());
                byteBuffer.put(val.toBoolean()
                        ? (byte) 1
                        : (byte) 0);
                suffix.getConsumer().accept(byteBuffer);
                byteBuffer.flip();
                return function.apply(byteBuffer);
            });
            case BYTE -> byteBuffers.use(len + Byte.BYTES, byteBuffer -> {
                prefix.getConsumer().accept(byteBuffer);
                byteBuffer.put(type.getId());
                byteBuffer.put(((ValByte) val).getValue());
                suffix.getConsumer().accept(byteBuffer);
                byteBuffer.flip();
                return function.apply(byteBuffer);
            });
            case SHORT -> byteBuffers.use(len + Short.BYTES, byteBuffer -> {
                prefix.getConsumer().accept(byteBuffer);
                byteBuffer.put(type.getId());
                byteBuffer.putShort(((ValShort) val).getValue());
                suffix.getConsumer().accept(byteBuffer);
                byteBuffer.flip();
                return function.apply(byteBuffer);
            });
            case INTEGER -> byteBuffers.use(len + Integer.BYTES, byteBuffer -> {
                prefix.getConsumer().accept(byteBuffer);
                byteBuffer.put(type.getId());
                byteBuffer.putInt(val.toInteger());
                suffix.getConsumer().accept(byteBuffer);
                byteBuffer.flip();
                return function.apply(byteBuffer);
            });
            case LONG, DURATION, DATE -> byteBuffers.use(len + Long.BYTES, byteBuffer -> {
                prefix.getConsumer().accept(byteBuffer);
                byteBuffer.put(type.getId());
                byteBuffer.putLong(val.toLong());
                suffix.getConsumer().accept(byteBuffer);
                byteBuffer.flip();
                return function.apply(byteBuffer);
            });
            case FLOAT -> byteBuffers.use(len + Float.BYTES, byteBuffer -> {
                prefix.getConsumer().accept(byteBuffer);
                byteBuffer.put(type.getId());
                byteBuffer.putFloat(val.toFloat());
                suffix.getConsumer().accept(byteBuffer);
                byteBuffer.flip();
                return function.apply(byteBuffer);
            });
            case DOUBLE -> byteBuffers.use(len + Double.BYTES, byteBuffer -> {
                prefix.getConsumer().accept(byteBuffer);
                byteBuffer.put(type.getId());
                byteBuffer.putDouble(val.toDouble());
                suffix.getConsumer().accept(byteBuffer);
                byteBuffer.flip();
                return function.apply(byteBuffer);
            });
            case STRING, ERR -> {
                final byte[] bytes = val.toString().getBytes(StandardCharsets.UTF_8);
                yield byteBuffers.use(len + bytes.length, byteBuffer -> {
                    prefix.getConsumer().accept(byteBuffer);
                    byteBuffer.put(type.getId());
                    byteBuffer.put(bytes);
                    suffix.getConsumer().accept(byteBuffer);
                    byteBuffer.flip();
                    return function.apply(byteBuffer);
                });
            }
            case XML -> {
                final byte[] bytes = ((ValXml) val).getBytes();
                yield byteBuffers.use(len + bytes.length, byteBuffer -> {
                    prefix.getConsumer().accept(byteBuffer);
                    byteBuffer.put(type.getId());
                    byteBuffer.put(bytes);
                    suffix.getConsumer().accept(byteBuffer);
                    byteBuffer.flip();
                    return function.apply(byteBuffer);
                });
            }
        };
    }

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
            case XML -> ValXml.create(ByteBufferUtils.getBytes(byteBuffer));
            case ERR -> ValErr.create(ByteBufferUtils.toString(byteBuffer));
        };
    }

    public static void writeBoolean(final Val val, final ByteBuffer byteBuffer) {
        byteBuffer.put(getBoolean(val)
                ? (byte) 1
                : (byte) 0);
    }

    public static void writeByte(final Val val, final ByteBuffer byteBuffer) {
        byteBuffer.put(getByte(val));
    }

    public static void writeShort(final Val val, final ByteBuffer byteBuffer) {
        byteBuffer.putShort(getShort(val));
    }

    public static void writeInteger(final Val val, final ByteBuffer byteBuffer) {
        byteBuffer.putInt(getInteger(val));
    }

    public static void writeLong(final Val val, final ByteBuffer byteBuffer) {
        byteBuffer.putLong(getLong(val));
    }

    public static void writeFloat(final Val val, final ByteBuffer byteBuffer) {
        byteBuffer.putFloat(getFloat(val));
    }

    public static void writeDouble(final Val val, final ByteBuffer byteBuffer) {
        byteBuffer.putDouble(getDouble(val));
    }

    public static void writeDate(final Val val, final ByteBuffer byteBuffer) {
        byteBuffer.putLong(getDate(val));
    }

    private static boolean getBoolean(final Val val) {
        try {
            if (Type.BOOLEAN.equals(val.type())) {
                final ValBoolean valBoolean = (ValBoolean) val;
                return Objects.requireNonNullElse(valBoolean.toBoolean(), false);
            } else {
                return Objects.requireNonNullElse(val.toBoolean(), false);
            }
        } catch (final NumberFormatException | NullPointerException e) {
            throw new RuntimeException("Expected val to be a byte but could not parse '" +
                                       val +
                                       "' as boolean");
        }
    }

    private static byte getByte(final Val val) {
        try {
            if (Type.BYTE.equals(val.type())) {
                final ValByte valByte = (ValByte) val;
                return valByte.getValue();
            } else {
                return Byte.parseByte(val.toString());
            }
        } catch (final NumberFormatException | NullPointerException e) {
            throw new RuntimeException("Expected val to be a byte but could not parse '" +
                                       val +
                                       "' as byte");
        }
    }

    private static short getShort(final Val val) {
        try {
            if (Type.SHORT.equals(val.type())) {
                final ValShort valShort = (ValShort) val;
                return valShort.getValue();
            } else {
                return Short.parseShort(val.toString());
            }
        } catch (final NumberFormatException | NullPointerException e) {
            throw new RuntimeException("Expected val to be a short but could not parse '" +
                                       val +
                                       "' as short");
        }
    }

    private static int getInteger(final Val val) {
        try {
            if (Type.INTEGER.equals(val.type())) {
                final ValInteger valInteger = (ValInteger) val;
                return valInteger.toInteger();
            } else {
                return val.toInteger();
            }
        } catch (final NumberFormatException | NullPointerException e) {
            throw new RuntimeException("Expected val to be an integer but could not parse '" +
                                       val +
                                       "' as integer");
        }
    }

    private static long getLong(final Val val) {
        try {
            if (Type.LONG.equals(val.type())) {
                final ValLong valLong = (ValLong) val;
                return valLong.toLong();
            } else {
                return val.toLong();
            }
        } catch (final NumberFormatException | NullPointerException e) {
            throw new RuntimeException("Expected val to be a long but could not parse '" +
                                       val +
                                       "' as long");
        }
    }

    private static float getFloat(final Val val) {
        try {
            if (Type.FLOAT.equals(val.type())) {
                final ValFloat valFloat = (ValFloat) val;
                return valFloat.toFloat();
            } else {
                return Float.parseFloat(val.toString());
            }
        } catch (final NumberFormatException | NullPointerException e) {
            throw new RuntimeException("Expected val to be a float but could not parse '" +
                                       val +
                                       "' as float");
        }
    }

    private static double getDouble(final Val val) {
        try {
            if (Type.DOUBLE.equals(val.type())) {
                final ValDouble valDouble = (ValDouble) val;
                return valDouble.toDouble();
            } else {
                return Double.parseDouble(val.toString());
            }
        } catch (final NumberFormatException | NullPointerException e) {
            throw new RuntimeException("Expected val to be a double but could not parse '" +
                                       val +
                                       "' as double");
        }
    }

    private static long getDate(final Val val) {
        try {
            if (Type.DATE.equals(val.type())) {
                final ValDate valDate = (ValDate) val;
                return valDate.toLong();
            } else {
                return val.toLong();
            }
        } catch (final NumberFormatException | NullPointerException e) {
            throw new RuntimeException("Expected val to be a date but could not parse '" +
                                       val +
                                       "' as date");
        }
    }

}
