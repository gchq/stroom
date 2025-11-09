package stroom.pathways.shared;

public interface TracePersistence extends TracesStore {

    TraceWriter createWriter();
}
