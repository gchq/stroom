package stroom.dashboard.client.table;

import com.google.gwt.core.client.JsonUtils;

public class JsonUtil {
    protected JsonUtil() {
    }

    @SuppressWarnings("unchecked")
    public static <T> T decode(final String json) {
        final Object obj = JsonUtils.safeEval(json).cast();
        return (T) obj;
    }

    public static native String encode(final Object obj) /*-{
        return $wnd.JSON.stringify(obj);
    }-*/;
}
