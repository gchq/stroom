package stroom.proxy.app.handler;

public class LocalByteBuffer {

    private static final ThreadLocal<byte[]> THREAD_LOCAL = new ThreadLocal<>();

    public static byte[] get() {
        byte[] buffer = THREAD_LOCAL.get();
        if (buffer == null) {
            buffer = new byte[4096];
            THREAD_LOCAL.set(buffer);
        }

        return buffer;
    }
}
