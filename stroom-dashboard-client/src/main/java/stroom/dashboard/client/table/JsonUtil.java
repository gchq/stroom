package stroom.dashboard.client.table;

import com.google.gwt.core.client.JsonUtils;

public class JsonUtil {
    protected JsonUtil() {}

    @SuppressWarnings("unchecked")
    public static <T> T parse(final String json) {
        final Object obj = JsonUtils.safeEval(json).cast();
        return (T) obj;
    }

    public static native String stringify(Object obj) /*-{
        return $wnd.JSON.stringify(obj);
    }-*/;
}
