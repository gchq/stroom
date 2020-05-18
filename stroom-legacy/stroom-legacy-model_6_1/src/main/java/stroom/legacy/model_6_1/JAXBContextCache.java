package stroom.legacy.model_6_1;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class JAXBContextCache {
    private static final Map<Class<?>, JAXBContext> map = new ConcurrentHashMap<>();

    public static JAXBContext get(final Class<?> clazz) {
        return map.computeIfAbsent(clazz, k -> {
            try {
                return JAXBContext.newInstance(clazz);
            } catch (final JAXBException e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        });
    }
}
