package stroom.docstore.api;

import com.google.inject.Binder;
import com.google.inject.multibindings.MapBinder;

public class DocumentActionHandlerBinder {
    private final MapBinder<DocumentType, DocumentActionHandler> mapBinder;

    private DocumentActionHandlerBinder(final Binder binder) {
        mapBinder = MapBinder.newMapBinder(binder, DocumentType.class, DocumentActionHandler.class);
    }

    public static DocumentActionHandlerBinder create(final Binder binder) {
        return new DocumentActionHandlerBinder(binder);
    }

    public <H extends DocumentActionHandler> DocumentActionHandlerBinder bind(final String name, final Class<H> handler) {
        mapBinder.addBinding(new DocumentType(name)).to(handler);
        return this;
    }
}
