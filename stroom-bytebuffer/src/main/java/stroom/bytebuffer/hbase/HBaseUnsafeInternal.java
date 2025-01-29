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
                    Field f = Unsafe.class.getDeclaredField("theUnsafe");
                    f.setAccessible(true);
                    return f.get(null);
                } catch (Throwable e) {
                    LOG.warn("sun.misc.Unsafe is not accessible", e);
                }
                return null;
            }
        });
    }

    private HBaseUnsafeInternal() {
    }

    public static int getInt(Object o, long offset) {
        return UNSAFE.getInt(o, offset);
    }

    public static void putInt(Object o, long offset, int x) {
        UNSAFE.putInt(o, offset, x);
    }

    public static Object getObject(Object o, long offset) {
        return UNSAFE.getObject(o, offset);
    }

    public static void putObject(Object o, long offset, Object x) {
        UNSAFE.putObject(o, offset, x);
    }

    public static boolean getBoolean(Object o, long offset) {
        return UNSAFE.getBoolean(o, offset);
    }

    public static void putBoolean(Object o, long offset, boolean x) {
        UNSAFE.putBoolean(o, offset, x);
    }

    public static byte getByte(Object o, long offset) {
        return UNSAFE.getByte(o, offset);
    }

    public static void putByte(Object o, long offset, byte x) {
        UNSAFE.putByte(o, offset, x);
    }

    public static short getShort(Object o, long offset) {
        return UNSAFE.getShort(o, offset);
    }

    public static void putShort(Object o, long offset, short x) {
        UNSAFE.putShort(o, offset, x);
    }

    public static char getChar(Object o, long offset) {
        return UNSAFE.getChar(o, offset);
    }

    public static void putChar(Object o, long offset, char x) {
        UNSAFE.putChar(o, offset, x);
    }

    public static long getLong(Object o, long offset) {
        return UNSAFE.getLong(o, offset);
    }

    public static void putLong(Object o, long offset, long x) {
        UNSAFE.putLong(o, offset, x);
    }

    public static float getFloat(Object o, long offset) {
        return UNSAFE.getFloat(o, offset);
    }

    public static void putFloat(Object o, long offset, float x) {
        UNSAFE.putFloat(o, offset, x);
    }

    public static double getDouble(Object o, long offset) {
        return UNSAFE.getDouble(o, offset);
    }

    public static void putDouble(Object o, long offset, double x) {
        UNSAFE.putDouble(o, offset, x);
    }

    public static byte getByte(long address) {
        return UNSAFE.getByte(address);
    }

    public static void putByte(long address, byte x) {
        UNSAFE.putByte(address, x);
    }

    public static short getShort(long address) {
        return UNSAFE.getShort(address);
    }

    public static void putShort(long address, short x) {
        UNSAFE.putShort(address, x);
    }

    public static char getChar(long address) {
        return UNSAFE.getChar(address);
    }

    public static void putChar(long address, char x) {
        UNSAFE.putChar(address, x);
    }

    public static int getInt(long address) {
        return UNSAFE.getInt(address);
    }

    public static void putInt(long address, int x) {
        UNSAFE.putInt(address, x);
    }

    public static long getLong(long address) {
        return UNSAFE.getLong(address);
    }

    public static void putLong(long address, long x) {
        UNSAFE.putLong(address, x);
    }

    public static float getFloat(long address) {
        return UNSAFE.getFloat(address);
    }

    public static void putFloat(long address, float x) {
        UNSAFE.putFloat(address, x);
    }

    public static double getDouble(long address) {
        return UNSAFE.getDouble(address);
    }

    public static void putDouble(long address, double x) {
        UNSAFE.putDouble(address, x);
    }

    public static long getAddress(long address) {
        return UNSAFE.getAddress(address);
    }

    public static void putAddress(long address, long x) {
        UNSAFE.putAddress(address, x);
    }

    public static long allocateMemory(long bytes) {
        return UNSAFE.allocateMemory(bytes);
    }

    public static long reallocateMemory(long address, long bytes) {
        return UNSAFE.reallocateMemory(address, bytes);
    }

    public static void setMemory(Object o, long offset, long bytes, byte value) {
        UNSAFE.setMemory(o, offset, bytes, value);
    }

    public static void setMemory(long address, long bytes, byte value) {
        UNSAFE.setMemory(address, bytes, value);
    }

    public static void copyMemory(Object srcBase, long srcOffset, Object destBase, long destOffset,
                                  long bytes) {
        UNSAFE.copyMemory(srcBase, srcOffset, destBase, destOffset, bytes);
    }

    public static void copyMemory(long srcAddress, long destAddress, long bytes) {
        UNSAFE.copyMemory(srcAddress, destAddress, bytes);
    }

    public static void freeMemory(long address) {
        UNSAFE.freeMemory(address);
    }

    public static long staticFieldOffset(Field f) {
        return UNSAFE.staticFieldOffset(f);
    }

    public static long objectFieldOffset(Field f) {
        return UNSAFE.objectFieldOffset(f);
    }

    public static Object staticFieldBase(Field f) {
        return UNSAFE.staticFieldBase(f);
    }

    public static boolean shouldBeInitialized(Class<?> c) {
        return UNSAFE.shouldBeInitialized(c);
    }

    public static void ensureClassInitialized(Class<?> c) {
        UNSAFE.ensureClassInitialized(c);
    }

    public static int arrayBaseOffset(Class<?> arrayClass) {
        return UNSAFE.arrayBaseOffset(arrayClass);
    }

    public static int arrayIndexScale(Class<?> arrayClass) {
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

    public static Object allocateInstance(Class<?> cls) throws InstantiationException {
        return UNSAFE.allocateInstance(cls);
    }

    public static void throwException(Throwable ee) {
        UNSAFE.throwException(ee);
    }

    public static boolean compareAndSwapObject(Object o, long offset, Object expected, Object x) {
        return UNSAFE.compareAndSwapObject(o, offset, expected, x);
    }

    public static boolean compareAndSwapInt(Object o, long offset, int expected, int x) {
        return UNSAFE.compareAndSwapInt(o, offset, expected, x);
    }

    public static boolean compareAndSwapLong(Object o, long offset, long expected, long x) {
        return UNSAFE.compareAndSwapLong(o, offset, expected, x);
    }

    public static Object getObjectVolatile(Object o, long offset) {
        return UNSAFE.getObjectVolatile(o, offset);
    }

    public static void putObjectVolatile(Object o, long offset, Object x) {
        UNSAFE.putObjectVolatile(o, offset, x);
    }

    public static int getIntVolatile(Object o, long offset) {
        return UNSAFE.getIntVolatile(o, offset);
    }

    public static void putIntVolatile(Object o, long offset, int x) {
        UNSAFE.putIntVolatile(o, offset, x);
    }

    public static boolean getBooleanVolatile(Object o, long offset) {
        return UNSAFE.getBooleanVolatile(o, offset);
    }

    public static void putBooleanVolatile(Object o, long offset, boolean x) {
        UNSAFE.putBooleanVolatile(o, offset, x);
    }

    public static byte getByteVolatile(Object o, long offset) {
        return UNSAFE.getByteVolatile(o, offset);
    }

    public static void putByteVolatile(Object o, long offset, byte x) {
        UNSAFE.putByteVolatile(o, offset, x);
    }

    public static short getShortVolatile(Object o, long offset) {
        return UNSAFE.getShortVolatile(o, offset);
    }

    public static void putShortVolatile(Object o, long offset, short x) {
        UNSAFE.putShortVolatile(o, offset, x);
    }

    public static char getCharVolatile(Object o, long offset) {
        return UNSAFE.getCharVolatile(o, offset);
    }

    public static void putCharVolatile(Object o, long offset, char x) {
        UNSAFE.putCharVolatile(o, offset, x);
    }

    public static long getLongVolatile(Object o, long offset) {
        return UNSAFE.getLongVolatile(o, offset);
    }

    public static void putLongVolatile(Object o, long offset, long x) {
        UNSAFE.putLongVolatile(o, offset, x);
    }

    public static float getFloatVolatile(Object o, long offset) {
        return UNSAFE.getFloatVolatile(o, offset);
    }

    public static void putFloatVolatile(Object o, long offset, float x) {
        UNSAFE.putFloatVolatile(o, offset, x);
    }

    public static double getDoubleVolatile(Object o, long offset) {
        return UNSAFE.getDoubleVolatile(o, offset);
    }

    public static void putDoubleVolatile(Object o, long offset, double x) {
        UNSAFE.putDoubleVolatile(o, offset, x);
    }

    public static void putOrderedObject(Object o, long offset, Object x) {
        UNSAFE.putOrderedObject(o, offset, x);
    }

    public static void putOrderedInt(Object o, long offset, int x) {
        UNSAFE.putOrderedInt(o, offset, x);
    }

    public static void putOrderedLong(Object o, long offset, long x) {
        UNSAFE.putOrderedLong(o, offset, x);
    }

    public static void unpark(Object thread) {
        UNSAFE.unpark(thread);
    }

    public static void park(boolean isAbsolute, long time) {
        UNSAFE.park(isAbsolute, time);
    }

    public static int getLoadAverage(double[] loadavg, int nelems) {
        return UNSAFE.getLoadAverage(loadavg, nelems);
    }

    public static int getAndAddInt(Object o, long offset, int delta) {
        return UNSAFE.getAndAddInt(o, offset, delta);
    }

    public static long getAndAddLong(Object o, long offset, long delta) {
        return UNSAFE.getAndAddLong(o, offset, delta);
    }

    public static int getAndSetInt(Object o, long offset, int newValue) {
        return UNSAFE.getAndSetInt(o, offset, newValue);
    }

    public static long getAndSetLong(Object o, long offset, long newValue) {
        return UNSAFE.getAndSetLong(o, offset, newValue);
    }

    public static Object getAndSetObject(Object o, long offset, Object newValue) {
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
