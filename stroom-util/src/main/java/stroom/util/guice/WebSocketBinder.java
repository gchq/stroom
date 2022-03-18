package stroom.util.guice;

import stroom.util.shared.IsWebSocket;

import com.google.inject.Binder;
import com.google.inject.multibindings.MapBinder;

import java.util.Objects;

public class WebSocketBinder {

    private final MapBinder<WebSocketType, IsWebSocket> mapBinder;

    private WebSocketBinder(final Binder binder) {
        mapBinder = MapBinder.newMapBinder(binder, WebSocketType.class, IsWebSocket.class);
    }

    public static WebSocketBinder create(final Binder binder) {
        return new WebSocketBinder(binder);
    }

    public <T extends IsWebSocket> WebSocketBinder bind(final Class<T> resourceClass) {
        mapBinder.addBinding(new WebSocketType(resourceClass)).to(resourceClass);
        return this;
    }

    public static class WebSocketType {

        private final Class<?> webSocketClass;

        <T extends IsWebSocket> WebSocketType(final Class<T> webSocketClass) {
            this.webSocketClass = webSocketClass;
        }

        public Class<?> getWebSocketClass() {
            return webSocketClass;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final WebSocketType that = (WebSocketType) o;
            return Objects.equals(webSocketClass, that.webSocketClass);
        }

        @Override
        public int hashCode() {
            return Objects.hash(webSocketClass);
        }
    }
}
