package stroom.proxy.app.handler;

public class LocalByteBuffer {

    public static final int BUFFER_SIZE = 8192;

    private static final ThreadLocal<byte[]> THREAD_LOCAL = new ThreadLocal<>();

    public static byte[] get() {
        byte[] buffer = THREAD_LOCAL.get();
        if (buffer == null) {
            buffer = new byte[BUFFER_SIZE];
            THREAD_LOCAL.set(buffer);
        }

        return buffer;
    }
}
