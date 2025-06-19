/*
 * Copy of https://github.com/apache/hbase-thirdparty/blob/master/hbase-unsafe/src/main/java/org/apache/hadoop/hbase/unsafe/HBaseUnsafeInternal.java
 * to avoid having to pull in all of hbase to use the util methods.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.bytebuffer.hbase;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.security.AccessController;
import java.security.PrivilegedAction;

/**
 * Delegate all the method in sun.misc.Unsafe.
 */
@SuppressWarnings("restriction")
final class HBaseUnsafeInternal {

    private static final Logger LOG = LoggerFactory.getLogger(HBaseUnsafeInternal.class);

    private static final Unsafe UNSAFE;

    static {
        UNSAFE = (Unsafe) AccessController.doPrivileged(new PrivilegedAction<Object>() {
            @Override
            public Object run() {
                try {
                    final Field f = Unsafe.class.getDeclaredField("theUnsafe");
                    f.setAccessible(true);
                    return f.get(null);
                } catch (final Throwable e) {
                    LOG.warn("sun.misc.Unsafe is not accessible", e);
                }
                return null;
            }
        });
    }

    private HBaseUnsafeInternal() {
    }

    public static int getInt(final Object o, final long offset) {
        return UNSAFE.getInt(o, offset);
    }

    public static void putInt(final Object o, final long offset, final int x) {
        UNSAFE.putInt(o, offset, x);
    }

    public static Object getObject(final Object o, final long offset) {
        return UNSAFE.getObject(o, offset);
    }

    public static void putObject(final Object o, final long offset, final Object x) {
        UNSAFE.putObject(o, offset, x);
    }

    public static boolean getBoolean(final Object o, final long offset) {
        return UNSAFE.getBoolean(o, offset);
    }

    public static void putBoolean(final Object o, final long offset, final boolean x) {
        UNSAFE.putBoolean(o, offset, x);
    }

    public static byte getByte(final Object o, final long offset) {
        return UNSAFE.getByte(o, offset);
    }

    public static void putByte(final Object o, final long offset, final byte x) {
        UNSAFE.putByte(o, offset, x);
    }

    public static short getShort(final Object o, final long offset) {
        return UNSAFE.getShort(o, offset);
    }

    public static void putShort(final Object o, final long offset, final short x) {
        UNSAFE.putShort(o, offset, x);
    }

    public static char getChar(final Object o, final long offset) {
        return UNSAFE.getChar(o, offset);
    }

    public static void putChar(final Object o, final long offset, final char x) {
        UNSAFE.putChar(o, offset, x);
    }

    public static long getLong(final Object o, final long offset) {
        return UNSAFE.getLong(o, offset);
    }

    public static void putLong(final Object o, final long offset, final long x) {
        UNSAFE.putLong(o, offset, x);
    }

    public static float getFloat(final Object o, final long offset) {
        return UNSAFE.getFloat(o, offset);
    }

    public static void putFloat(final Object o, final long offset, final float x) {
        UNSAFE.putFloat(o, offset, x);
    }

    public static double getDouble(final Object o, final long offset) {
        return UNSAFE.getDouble(o, offset);
    }

    public static void putDouble(final Object o, final long offset, final double x) {
        UNSAFE.putDouble(o, offset, x);
    }

    public static byte getByte(final long address) {
        return UNSAFE.getByte(address);
    }

    public static void putByte(final long address, final byte x) {
        UNSAFE.putByte(address, x);
    }

    public static short getShort(final long address) {
        return UNSAFE.getShort(address);
    }

    public static void putShort(final long address, final short x) {
        UNSAFE.putShort(address, x);
    }

    public static char getChar(final long address) {
        return UNSAFE.getChar(address);
    }

    public static void putChar(final long address, final char x) {
        UNSAFE.putChar(address, x);
    }

    public static int getInt(final long address) {
        return UNSAFE.getInt(address);
    }

    public static void putInt(final long address, final int x) {
        UNSAFE.putInt(address, x);
    }

    public static long getLong(final long address) {
        return UNSAFE.getLong(address);
    }

    public static void putLong(final long address, final long x) {
        UNSAFE.putLong(address, x);
    }

    public static float getFloat(final long address) {
        return UNSAFE.getFloat(address);
    }

    public static void putFloat(final long address, final float x) {
        UNSAFE.putFloat(address, x);
    }

    public static double getDouble(final long address) {
        return UNSAFE.getDouble(address);
    }

    public static void putDouble(final long address, final double x) {
        UNSAFE.putDouble(address, x);
    }

    public static long getAddress(final long address) {
        return UNSAFE.getAddress(address);
    }

    public static void putAddress(final long address, final long x) {
        UNSAFE.putAddress(address, x);
    }

    public static long allocateMemory(final long bytes) {
        return UNSAFE.allocateMemory(bytes);
    }

    public static long reallocateMemory(final long address, final long bytes) {
        return UNSAFE.reallocateMemory(address, bytes);
    }

    public static void setMemory(final Object o, final long offset, final long bytes, final byte value) {
        UNSAFE.setMemory(o, offset, bytes, value);
    }

    public static void setMemory(final long address, final long bytes, final byte value) {
        UNSAFE.setMemory(address, bytes, value);
    }

    public static void copyMemory(final Object srcBase, final long srcOffset, final Object destBase, final long destOffset,
                                  final long bytes) {
        UNSAFE.copyMemory(srcBase, srcOffset, destBase, destOffset, bytes);
    }

    public static void copyMemory(final long srcAddress, final long destAddress, final long bytes) {
        UNSAFE.copyMemory(srcAddress, destAddress, bytes);
    }

    public static void freeMemory(final long address) {
        UNSAFE.freeMemory(address);
    }

    public static long staticFieldOffset(final Field f) {
        return UNSAFE.staticFieldOffset(f);
    }

    public static long objectFieldOffset(final Field f) {
        return UNSAFE.objectFieldOffset(f);
    }

    public static Object staticFieldBase(final Field f) {
        return UNSAFE.staticFieldBase(f);
    }

    public static boolean shouldBeInitialized(final Class<?> c) {
        return UNSAFE.shouldBeInitialized(c);
    }

    public static void ensureClassInitialized(final Class<?> c) {
        UNSAFE.ensureClassInitialized(c);
    }

    public static int arrayBaseOffset(final Class<?> arrayClass) {
        return UNSAFE.arrayBaseOffset(arrayClass);
    }

    public static int arrayIndexScale(final Class<?> arrayClass) {
        return UNSAFE.arrayIndexScale(arrayClass);
    }

    public static int addressSize() {
        return UNSAFE.addressSize();
    }

    public static int pageSize() {
        return UNSAFE.pageSize();
    }

//    public static Class<?> defineClass(String name, byte[] b, int off, int len, ClassLoader loader,
//                                       ProtectionDomain protectionDomain) {
//        return UNSAFE.defineClass(name, b, off, len, loader, protectionDomain);
//        MethodHandles.lookup().
//    }
//
//    public static Class<?> defineAnonymousClass(Class<?> hostClass, byte[] data, Object[] cpPatches) {
//        return UNSAFE.defineAnonymousClass(hostClass, data, cpPatches);
//    }

    public static Object allocateInstance(final Class<?> cls) throws InstantiationException {
        return UNSAFE.allocateInstance(cls);
    }

    public static void throwException(final Throwable ee) {
        UNSAFE.throwException(ee);
    }

    public static boolean compareAndSwapObject(final Object o, final long offset, final Object expected, final Object x) {
        return UNSAFE.compareAndSwapObject(o, offset, expected, x);
    }

    public static boolean compareAndSwapInt(final Object o, final long offset, final int expected, final int x) {
        return UNSAFE.compareAndSwapInt(o, offset, expected, x);
    }

    public static boolean compareAndSwapLong(final Object o, final long offset, final long expected, final long x) {
        return UNSAFE.compareAndSwapLong(o, offset, expected, x);
    }

    public static Object getObjectVolatile(final Object o, final long offset) {
        return UNSAFE.getObjectVolatile(o, offset);
    }

    public static void putObjectVolatile(final Object o, final long offset, final Object x) {
        UNSAFE.putObjectVolatile(o, offset, x);
    }

    public static int getIntVolatile(final Object o, final long offset) {
        return UNSAFE.getIntVolatile(o, offset);
    }

    public static void putIntVolatile(final Object o, final long offset, final int x) {
        UNSAFE.putIntVolatile(o, offset, x);
    }

    public static boolean getBooleanVolatile(final Object o, final long offset) {
        return UNSAFE.getBooleanVolatile(o, offset);
    }

    public static void putBooleanVolatile(final Object o, final long offset, final boolean x) {
        UNSAFE.putBooleanVolatile(o, offset, x);
    }

    public static byte getByteVolatile(final Object o, final long offset) {
        return UNSAFE.getByteVolatile(o, offset);
    }

    public static void putByteVolatile(final Object o, final long offset, final byte x) {
        UNSAFE.putByteVolatile(o, offset, x);
    }

    public static short getShortVolatile(final Object o, final long offset) {
        return UNSAFE.getShortVolatile(o, offset);
    }

    public static void putShortVolatile(final Object o, final long offset, final short x) {
        UNSAFE.putShortVolatile(o, offset, x);
    }

    public static char getCharVolatile(final Object o, final long offset) {
        return UNSAFE.getCharVolatile(o, offset);
    }

    public static void putCharVolatile(final Object o, final long offset, final char x) {
        UNSAFE.putCharVolatile(o, offset, x);
    }

    public static long getLongVolatile(final Object o, final long offset) {
        return UNSAFE.getLongVolatile(o, offset);
    }

    public static void putLongVolatile(final Object o, final long offset, final long x) {
        UNSAFE.putLongVolatile(o, offset, x);
    }

    public static float getFloatVolatile(final Object o, final long offset) {
        return UNSAFE.getFloatVolatile(o, offset);
    }

    public static void putFloatVolatile(final Object o, final long offset, final float x) {
        UNSAFE.putFloatVolatile(o, offset, x);
    }

    public static double getDoubleVolatile(final Object o, final long offset) {
        return UNSAFE.getDoubleVolatile(o, offset);
    }

    public static void putDoubleVolatile(final Object o, final long offset, final double x) {
        UNSAFE.putDoubleVolatile(o, offset, x);
    }

    public static void putOrderedObject(final Object o, final long offset, final Object x) {
        UNSAFE.putOrderedObject(o, offset, x);
    }

    public static void putOrderedInt(final Object o, final long offset, final int x) {
        UNSAFE.putOrderedInt(o, offset, x);
    }

    public static void putOrderedLong(final Object o, final long offset, final long x) {
        UNSAFE.putOrderedLong(o, offset, x);
    }

    public static void unpark(final Object thread) {
        UNSAFE.unpark(thread);
    }

    public static void park(final boolean isAbsolute, final long time) {
        UNSAFE.park(isAbsolute, time);
    }

    public static int getLoadAverage(final double[] loadavg, final int nelems) {
        return UNSAFE.getLoadAverage(loadavg, nelems);
    }

    public static int getAndAddInt(final Object o, final long offset, final int delta) {
        return UNSAFE.getAndAddInt(o, offset, delta);
    }

    public static long getAndAddLong(final Object o, final long offset, final long delta) {
        return UNSAFE.getAndAddLong(o, offset, delta);
    }

    public static int getAndSetInt(final Object o, final long offset, final int newValue) {
        return UNSAFE.getAndSetInt(o, offset, newValue);
    }

    public static long getAndSetLong(final Object o, final long offset, final long newValue) {
        return UNSAFE.getAndSetLong(o, offset, newValue);
    }

    public static Object getAndSetObject(final Object o, final long offset, final Object newValue) {
        return UNSAFE.getAndSetObject(o, offset, newValue);
    }

    public static void loadFence() {
        UNSAFE.loadFence();
    }

    public static void storeFence() {
        UNSAFE.storeFence();
    }

    public static void fullFence() {
        UNSAFE.fullFence();
    }

}
