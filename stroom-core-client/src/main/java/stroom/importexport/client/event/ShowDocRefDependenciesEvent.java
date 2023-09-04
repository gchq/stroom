package stroom.importexport.client.event;

import stroom.docref.DocRef;
import stroom.importexport.client.event.ShowDocRefDependenciesEvent.Handler;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;

public class ShowDocRefDependenciesEvent extends GwtEvent<Handler> {

    private static Type<Handler> TYPE;

    private final DocRef docRef;
    private final DependencyType dependencyType;

    private ShowDocRefDependenciesEvent(final DocRef docRef,
                                        final DependencyType dependencyType) {
        this.docRef = docRef;
        this.dependencyType = dependencyType;
    }

    public static void fire(final HasHandlers source,
                            final DocRef docRef,
                            final DependencyType dependencyType) {
        source.fireEvent(new ShowDocRefDependenciesEvent(docRef, dependencyType));
    }

    public static Type<Handler> getType() {
        if (TYPE == null) {
            TYPE = new Type<>();
        }
        return TYPE;
    }

    @Override
    public Type<Handler> getAssociatedType() {
        return getType();
    }

    @Override
    protected void dispatch(final Handler handler) {
        handler.onShow(this);
    }

    public DocRef getDocRef() {
        return docRef;
    }

    public DependencyType getDependencyType() {
        return dependencyType;
    }

    // --------------------------------------------------------------------------------


    public interface Handler extends EventHandler {

        void onShow(ShowDocRefDependenciesEvent event);
    }


    // --------------------------------------------------------------------------------


    public enum DependencyType {
        /**
         * Document X depends on something or things
         */
        DEPENDENCY,
        /**
         * Something or things depends on document X
         */
        DEPENDANT;
    }
}
