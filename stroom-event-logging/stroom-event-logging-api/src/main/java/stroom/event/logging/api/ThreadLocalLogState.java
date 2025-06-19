package stroom.event.logging.api;

public class ThreadLocalLogState {

    private static final ThreadLocal<Boolean> THREAD_LOCAL = new ThreadLocal<>();

    public static boolean hasLogged() {
        return THREAD_LOCAL.get() == Boolean.TRUE;
    }

    public static void setLogged(final boolean logged) {
        if (!logged) {
            THREAD_LOCAL.remove();
        } else {
            THREAD_LOCAL.set(logged);
        }
    }
}
