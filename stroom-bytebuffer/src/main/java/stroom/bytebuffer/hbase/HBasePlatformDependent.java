/*
 * Copy of https://github.com/apache/hbase-thirdparty/blob/master/hbase-unsafe/src/main/java/org/apache/hadoop/hbase/unsafe/HBasePlatformDependent.java
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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.function.BiConsumer;

/**
 * Delegate all methods of {@link HBaseUnsafeInternal} and {@link HBaseSignalInternal} so we will
 * not touch the actual {@code sun.misc.Unsafe} and {@code sun.misc.Signal} classes until we
 * actually call the methods.
 */
public final class HBasePlatformDependent {

    private static final String CLASS_NAME = "sun.misc.Unsafe";
    private static final Logger LOG = LoggerFactory.getLogger(HBasePlatformDependent.class);
    private static final boolean AVAIL;
    private static final boolean UNALIGNED;

    static {
        AVAIL = AccessController.doPrivileged(new PrivilegedAction<Boolean>() {

            @Override
            public Boolean run() {
                return checkAvailable();
            }
        });
        UNALIGNED = checkUnaligned();
    }

    private static boolean checkAvailable() {
        try {
            final Class<?> clazz = Class.forName(CLASS_NAME);
            final Field f = clazz.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            final Object theUnsafe = f.get(null);
            if (theUnsafe == null) {
                LOG.warn("Could not get static instance from sun.misc.Unsafe");
                return false;
            }
            // Check for availability of all methods used by UnsafeAccess
            Method m;
            try {
                m = clazz.getDeclaredMethod("arrayBaseOffset", Class.class);
                if (m == null) {
                    LOG.warn("sun.misc.Unsafe is missing arrayBaseOffset(Class)");
                    return false;
                }
                m = clazz.getDeclaredMethod("copyMemory", Object.class, long.class, Object.class,
                        long.class, long.class);
                if (m == null) {
                    LOG.warn("sun.misc.Unsafe is missing copyMemory(Object,long,Object,long,long)");
                    return false;
                }
                m = clazz.getDeclaredMethod("getByte", Object.class, long.class);
                if (m == null) {
                    LOG.warn("sun.misc.Unsafe is missing getByte(Object,long)");
                    return false;
                }
                m = clazz.getDeclaredMethod("getShort", long.class);
                if (m == null) {
                    LOG.warn("sun.misc.Unsafe is missing getShort(long)");
                    return false;
                }
                m = clazz.getDeclaredMethod("getShort", Object.class, long.class);
                if (m == null) {
                    LOG.warn("sun.misc.Unsafe is missing getShort(Object,long)");
                    return false;
                }
                m = clazz.getDeclaredMethod("getInt", long.class);
                if (m == null) {
                    LOG.warn("sun.misc.Unsafe is missing getInt(long)");
                    return false;
                }
                m = clazz.getDeclaredMethod("getInt", Object.class, long.class);
                if (m == null) {
                    LOG.warn("sun.misc.Unsafe is missing getInt(Object,long)");
                    return false;
                }
                m = clazz.getDeclaredMethod("getLong", long.class);
                if (m == null) {
                    LOG.warn("sun.misc.Unsafe is missing getLong(long)");
                    return false;
                }
                m = clazz.getDeclaredMethod("getLong", Object.class, long.class);
                if (m == null) {
                    LOG.warn("sun.misc.Unsafe is missing getLong(Object,long)");
                    return false;
                }
                m = clazz.getDeclaredMethod("putByte", long.class, byte.class);
                if (m == null) {
                    LOG.warn("sun.misc.Unsafe is missing putByte(long,byte)");
                    return false;
                }
                m = clazz.getDeclaredMethod("putByte", Object.class, long.class, byte.class);
                if (m == null) {
                    LOG.warn("sun.misc.Unsafe is missing putByte(Object,long,byte)");
                    return false;
                }
                m = clazz.getDeclaredMethod("putShort", long.class, short.class);
                if (m == null) {
                    LOG.warn("sun.misc.Unsafe is missing putShort(long,short)");
                    return false;
                }
                m = clazz.getDeclaredMethod("putShort", Object.class, long.class, short.class);
                if (m == null) {
                    LOG.warn("sun.misc.Unsafe is missing putShort(Object,long,short)");
                    return false;
                }
                m = clazz.getDeclaredMethod("putInt", long.class, int.class);
                if (m == null) {
                    LOG.warn("sun.misc.Unsafe is missing putInt(long,int)");
                    return false;
                }
                m = clazz.getDeclaredMethod("putInt", Object.class, long.class, int.class);
                if (m == null) {
                    LOG.warn("sun.misc.Unsafe is missing putInt(Object,long,int)");
                    return false;
                }
                m = clazz.getDeclaredMethod("putLong", long.class, long.class);
                if (m == null) {
                    LOG.warn("sun.misc.Unsafe is missing putLong(long,long)");
                    return false;
                }
                m = clazz.getDeclaredMethod("putLong", Object.class, long.class, long.class);
                if (m == null) {
                    LOG.warn("sun.misc.Unsafe is missing putLong(Object,long,long)");
                    return false;
                }
                // theUnsafe is accessible and all methods are available
                return true;
            } catch (final Throwable e) {
                LOG.warn("sun.misc.Unsafe is missing one or more required methods", e);
            }
        } catch (final Throwable e) {
            LOG.warn("sun.misc.Unsafe is not available/accessible", e);
        }
        return false;
    }

    private static boolean checkUnaligned() {
        // When Unsafe itself is not available/accessible consider unaligned as false.
        if (!AVAIL) {
            return false;
        }
        final String arch = System.getProperty("os.arch");
        if ("ppc64".equals(arch) || "ppc64le".equals(arch) || "aarch64".equals(arch)) {
            // java.nio.Bits.unaligned() wrongly returns false on ppc (JDK-8165231),
            return true;
        }
        try {
            // Using java.nio.Bits#unaligned() to check for unaligned-access capability
            final Class<?> clazz = Class.forName("java.nio.Bits");
            final Method m = clazz.getDeclaredMethod("unaligned");
            m.setAccessible(true);
            return (Boolean) m.invoke(null);
        } catch (final Exception e) {
            LOG.warn("java.nio.Bits#unaligned() check failed."
                     + "Unsafe based read/write of primitive types won't be used", e);
        }
        return false;
    }

    /**
     * @return true when running JVM is having sun's Unsafe package available in it and it is
     * accessible.
     */
    public static boolean isUnsafeAvailable() {
        return AVAIL;
    }

    /**
     * @return true when running JVM is having sun's Unsafe package available in it and underlying
     * system having unaligned-access capability.
     */
    public static boolean unaligned() {
        return UNALIGNED;
    }

    private HBasePlatformDependent() {
        // private constructor to avoid instantiation
    }

    public static int getInt(final Object o, final long offset) {
        return HBaseUnsafeInternal.getInt(o, offset);
    }

    public static void putInt(final Object o, final long offset, final int x) {
        HBaseUnsafeInternal.putInt(o, offset, x);
    }

    public static Object getObject(final Object o, final long offset) {
        return HBaseUnsafeInternal.getObject(o, offset);
    }

    public static void putObject(final Object o, final long offset, final Object x) {
        HBaseUnsafeInternal.putObject(o, offset, x);
    }

    public static boolean getBoolean(final Object o, final long offset) {
        return HBaseUnsafeInternal.getBoolean(o, offset);
    }

    public static void putBoolean(final Object o, final long offset, final boolean x) {
        HBaseUnsafeInternal.putBoolean(o, offset, x);
    }

    public static byte getByte(final Object o, final long offset) {
        return HBaseUnsafeInternal.getByte(o, offset);
    }

    public static void putByte(final Object o, final long offset, final byte x) {
        HBaseUnsafeInternal.putByte(o, offset, x);
    }

    public static short getShort(final Object o, final long offset) {
        return HBaseUnsafeInternal.getShort(o, offset);
    }

    public static void putShort(final Object o, final long offset, final short x) {
        HBaseUnsafeInternal.putShort(o, offset, x);
    }

    public static char getChar(final Object o, final long offset) {
        return HBaseUnsafeInternal.getChar(o, offset);
    }

    public static void putChar(final Object o, final long offset, final char x) {
        HBaseUnsafeInternal.putChar(o, offset, x);
    }

    public static long getLong(final Object o, final long offset) {
        return HBaseUnsafeInternal.getLong(o, offset);
    }

    public static void putLong(final Object o, final long offset, final long x) {
        HBaseUnsafeInternal.putLong(o, offset, x);
    }

    public static float getFloat(final Object o, final long offset) {
        return HBaseUnsafeInternal.getFloat(o, offset);
    }

    public static void putFloat(final Object o, final long offset, final float x) {
        HBaseUnsafeInternal.putFloat(o, offset, x);
    }

    public static double getDouble(final Object o, final long offset) {
        return HBaseUnsafeInternal.getDouble(o, offset);
    }

    public static void putDouble(final Object o, final long offset, final double x) {
        HBaseUnsafeInternal.putDouble(o, offset, x);
    }

    public static byte getByte(final long address) {
        return HBaseUnsafeInternal.getByte(address);
    }

    public static void putByte(final long address, final byte x) {
        HBaseUnsafeInternal.putByte(address, x);
    }

    public static short getShort(final long address) {
        return HBaseUnsafeInternal.getShort(address);
    }

    public static void putShort(final long address, final short x) {
        HBaseUnsafeInternal.putShort(address, x);
    }

    public static char getChar(final long address) {
        return HBaseUnsafeInternal.getChar(address);
    }

    public static void putChar(final long address, final char x) {
        HBaseUnsafeInternal.putChar(address, x);
    }

    public static int getInt(final long address) {
        return HBaseUnsafeInternal.getInt(address);
    }

    public static void putInt(final long address, final int x) {
        HBaseUnsafeInternal.putInt(address, x);
    }

    public static long getLong(final long address) {
        return HBaseUnsafeInternal.getLong(address);
    }

    public static void putLong(final long address, final long x) {
        HBaseUnsafeInternal.putLong(address, x);
    }

    public static float getFloat(final long address) {
        return HBaseUnsafeInternal.getFloat(address);
    }

    public static void putFloat(final long address, final float x) {
        HBaseUnsafeInternal.putFloat(address, x);
    }

    public static double getDouble(final long address) {
        return HBaseUnsafeInternal.getDouble(address);
    }

    public static void putDouble(final long address, final double x) {
        HBaseUnsafeInternal.putDouble(address, x);
    }

    public static long getAddress(final long address) {
        return HBaseUnsafeInternal.getAddress(address);
    }

    public static void putAddress(final long address, final long x) {
        HBaseUnsafeInternal.putAddress(address, x);
    }

    public static long allocateMemory(final long bytes) {
        return HBaseUnsafeInternal.allocateMemory(bytes);
    }

    public static long reallocateMemory(final long address, final long bytes) {
        return HBaseUnsafeInternal.reallocateMemory(address, bytes);
    }

    public static void setMemory(final Object o, final long offset, final long bytes, final byte value) {
        HBaseUnsafeInternal.setMemory(o, offset, bytes, value);
    }

    public static void setMemory(final long address, final long bytes, final byte value) {
        HBaseUnsafeInternal.setMemory(address, bytes, value);
    }

    public static void copyMemory(final Object srcBase, final long srcOffset, final Object destBase, final long destOffset,
                                  final long bytes) {
        HBaseUnsafeInternal.copyMemory(srcBase, srcOffset, destBase, destOffset, bytes);
    }

    public static void copyMemory(final long srcAddress, final long destAddress, final long bytes) {
        HBaseUnsafeInternal.copyMemory(srcAddress, destAddress, bytes);
    }

    public static void freeMemory(final long address) {
        HBaseUnsafeInternal.freeMemory(address);
    }

    public static long staticFieldOffset(final Field f) {
        return HBaseUnsafeInternal.staticFieldOffset(f);
    }

    public static long objectFieldOffset(final Field f) {
        return HBaseUnsafeInternal.objectFieldOffset(f);
    }

    public static Object staticFieldBase(final Field f) {
        return HBaseUnsafeInternal.staticFieldBase(f);
    }

    public static boolean shouldBeInitialized(final Class<?> c) {
        return HBaseUnsafeInternal.shouldBeInitialized(c);
    }

    public static void ensureClassInitialized(final Class<?> c) {
        HBaseUnsafeInternal.ensureClassInitialized(c);
    }

    public static int arrayBaseOffset(final Class<?> arrayClass) {
        return HBaseUnsafeInternal.arrayBaseOffset(arrayClass);
    }

    public static int arrayIndexScale(final Class<?> arrayClass) {
        return HBaseUnsafeInternal.arrayIndexScale(arrayClass);
    }

    public static int addressSize() {
        return HBaseUnsafeInternal.addressSize();
    }

    public static int pageSize() {
        return HBaseUnsafeInternal.pageSize();
    }

//    public static Class<?> defineClass(String name, byte[] b, int off, int len, ClassLoader loader,
//                                       ProtectionDomain protectionDomain) {
//        return HBaseUnsafeInternal.defineClass(name, b, off, len, loader, protectionDomain);
//    }

//    public static Class<?> defineAnonymousClass(Class<?> hostClass, byte[] data, Object[] cpPatches) {
//        return HBaseUnsafeInternal.defineAnonymousClass(hostClass, data, cpPatches);
//    }

    public static Object allocateInstance(final Class<?> cls) throws InstantiationException {
        return HBaseUnsafeInternal.allocateInstance(cls);
    }

    public static void throwException(final Throwable ee) {
        HBaseUnsafeInternal.throwException(ee);
    }

    public static boolean compareAndSwapObject(final Object o, final long offset, final Object expected, final Object x) {
        return HBaseUnsafeInternal.compareAndSwapObject(o, offset, expected, x);
    }

    public static boolean compareAndSwapInt(final Object o, final long offset, final int expected, final int x) {
        return HBaseUnsafeInternal.compareAndSwapInt(o, offset, expected, x);
    }

    public static boolean compareAndSwapLong(final Object o, final long offset, final long expected, final long x) {
        return HBaseUnsafeInternal.compareAndSwapLong(o, offset, expected, x);
    }

    public static Object getObjectVolatile(final Object o, final long offset) {
        return HBaseUnsafeInternal.getObjectVolatile(o, offset);
    }

    public static void putObjectVolatile(final Object o, final long offset, final Object x) {
        HBaseUnsafeInternal.putObjectVolatile(o, offset, x);
    }

    public static int getIntVolatile(final Object o, final long offset) {
        return HBaseUnsafeInternal.getIntVolatile(o, offset);
    }

    public static void putIntVolatile(final Object o, final long offset, final int x) {
        HBaseUnsafeInternal.putIntVolatile(o, offset, x);
    }

    public static boolean getBooleanVolatile(final Object o, final long offset) {
        return HBaseUnsafeInternal.getBooleanVolatile(o, offset);
    }

    public static void putBooleanVolatile(final Object o, final long offset, final boolean x) {
        HBaseUnsafeInternal.putBooleanVolatile(o, offset, x);
    }

    public static byte getByteVolatile(final Object o, final long offset) {
        return HBaseUnsafeInternal.getByteVolatile(o, offset);
    }

    public static void putByteVolatile(final Object o, final long offset, final byte x) {
        HBaseUnsafeInternal.putByteVolatile(o, offset, x);
    }

    public static short getShortVolatile(final Object o, final long offset) {
        return HBaseUnsafeInternal.getShortVolatile(o, offset);
    }

    public static void putShortVolatile(final Object o, final long offset, final short x) {
        HBaseUnsafeInternal.putShortVolatile(o, offset, x);
    }

    public static char getCharVolatile(final Object o, final long offset) {
        return HBaseUnsafeInternal.getCharVolatile(o, offset);
    }

    public static void putCharVolatile(final Object o, final long offset, final char x) {
        HBaseUnsafeInternal.putCharVolatile(o, offset, x);
    }

    public static long getLongVolatile(final Object o, final long offset) {
        return HBaseUnsafeInternal.getLongVolatile(o, offset);
    }

    public static void putLongVolatile(final Object o, final long offset, final long x) {
        HBaseUnsafeInternal.putLongVolatile(o, offset, x);
    }

    public static float getFloatVolatile(final Object o, final long offset) {
        return HBaseUnsafeInternal.getFloatVolatile(o, offset);
    }

    public static void putFloatVolatile(final Object o, final long offset, final float x) {
        HBaseUnsafeInternal.putFloatVolatile(o, offset, x);
    }

    public static double getDoubleVolatile(final Object o, final long offset) {
        return HBaseUnsafeInternal.getDoubleVolatile(o, offset);
    }

    public static void putDoubleVolatile(final Object o, final long offset, final double x) {
        HBaseUnsafeInternal.putDoubleVolatile(o, offset, x);
    }

    public static void putOrderedObject(final Object o, final long offset, final Object x) {
        HBaseUnsafeInternal.putOrderedObject(o, offset, x);
    }

    public static void putOrderedInt(final Object o, final long offset, final int x) {
        HBaseUnsafeInternal.putOrderedInt(o, offset, x);
    }

    public static void putOrderedLong(final Object o, final long offset, final long x) {
        HBaseUnsafeInternal.putOrderedLong(o, offset, x);
    }

    public static void unpark(final Object thread) {
        HBaseUnsafeInternal.unpark(thread);
    }

    public static void park(final boolean isAbsolute, final long time) {
        HBaseUnsafeInternal.park(isAbsolute, time);
    }

    public static int getLoadAverage(final double[] loadavg, final int nelems) {
        return HBaseUnsafeInternal.getLoadAverage(loadavg, nelems);
    }

    public static int getAndAddInt(final Object o, final long offset, final int delta) {
        return HBaseUnsafeInternal.getAndAddInt(o, offset, delta);
    }

    public static long getAndAddLong(final Object o, final long offset, final long delta) {
        return HBaseUnsafeInternal.getAndAddLong(o, offset, delta);
    }

    public static int getAndSetInt(final Object o, final long offset, final int newValue) {
        return HBaseUnsafeInternal.getAndSetInt(o, offset, newValue);
    }

    public static long getAndSetLong(final Object o, final long offset, final long newValue) {
        return HBaseUnsafeInternal.getAndSetLong(o, offset, newValue);
    }

    public static Object getAndSetObject(final Object o, final long offset, final Object newValue) {
        return HBaseUnsafeInternal.getAndSetObject(o, offset, newValue);
    }

    public static void loadFence() {
        HBaseUnsafeInternal.loadFence();
    }

    public static void storeFence() {
        HBaseUnsafeInternal.storeFence();
    }

    public static void fullFence() {
        HBaseUnsafeInternal.fullFence();
    }

    /**
     * Delegate {@code sun.misc.Signal}.
     *
     * @param signal  the name of the signal, such as 'HUP'.
     * @param handler the handler of the signal, the first parameter is the number of the signal,
     *                while the second one is the name of the sinal.
     */
    public static void handle(final String signal, final BiConsumer<Integer, String> handler) {
        HBaseSignalInternal.handle(signal, handler);
    }
}
