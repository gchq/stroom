package stroom.dashboard.client.table;

import com.google.gwt.core.client.JsonUtils;

public class JSONUtil {
    @SuppressWarnings("unchecked")
    public static <T> T parse(final String json) {
        final Object obj = JsonUtils.safeEval(json).cast();
        return (T) obj;
    }
}
