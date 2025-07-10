package stroom.docstore.api;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Stream;

@Singleton
public class DocumentActionHandlers {

    @SuppressWarnings("rawtypes") // Guice doesn't like it
    private final Map<DocumentTypeName, DocumentActionHandler> handlersMap;

    @Inject
    public DocumentActionHandlers(
            @SuppressWarnings("rawtypes") final Map<DocumentTypeName, DocumentActionHandler> handlersMap) {
        this.handlersMap = handlersMap;
    }

    public DocumentActionHandler<?> getHandler(final String type) {
        return handlersMap.get(new DocumentTypeName(type));
    }

    @SuppressWarnings("rawtypes")
    public void forEach(final Consumer<DocumentActionHandler> consumer) {
        handlersMap.values()
                .stream()
                .filter(Objects::nonNull)
                .forEach(consumer);
    }

    @SuppressWarnings("rawtypes")
    public Stream<DocumentActionHandler> stream() {
        return handlersMap.values()
                .stream()
                .filter(Objects::nonNull);
    }
}
