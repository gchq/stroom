package stroom.processor.shared;

import stroom.docref.DocRef;
import stroom.util.shared.Document;

/**
 * A wrapper for a {@link ProcessorFilter} that has a name (which {@link ProcessorFilter}
 * doesn't.
 */
public class ProcessorFilterDoc implements Document {

    public static final String TYPE = ProcessorFilter.ENTITY_TYPE;

    private final ProcessorFilter processorFilter;
    private String name = null;

    public ProcessorFilterDoc(final ProcessorFilter processorFilter) {
        this.processorFilter = processorFilter;
    }

    public ProcessorFilterDoc(final ProcessorFilter processorFilter,
                              final String name) {
        this.processorFilter = processorFilter;
        this.name = name;
    }

    public ProcessorFilter getProcessorFilter() {
        return processorFilter;
    }

    @Override
    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    @Override
    public String getType() {
        return ProcessorFilter.ENTITY_TYPE;
    }

    @Override
    public String getUuid() {
        return processorFilter.getUuid();
    }

    @Override
    public Long getCreateTimeMs() {
        return processorFilter.getCreateTimeMs();
    }

    @Override
    public void setCreateTimeMs(final Long createTimeMs) {
        processorFilter.setCreateTimeMs(createTimeMs);
    }

    @Override
    public String getCreateUser() {
        return processorFilter.getCreateUser();
    }

    @Override
    public void setCreateUser(final String createUser) {
        processorFilter.setCreateUser(createUser);
    }

    @Override
    public Long getUpdateTimeMs() {
        return processorFilter.getUpdateTimeMs();
    }

    @Override
    public void setUpdateTimeMs(final Long updateTimeMs) {
        processorFilter.setUpdateTimeMs(updateTimeMs);
    }

    @Override
    public String getUpdateUser() {
        return processorFilter.getUpdateUser();
    }

    @Override
    public void setUpdateUser(final String updateUser) {
        processorFilter.setUpdateUser(updateUser);
    }

    /**
     * @return A new builder for creating a {@link DocRef} for this document's type.
     */
    public static DocRef.TypedBuilder buildDocRef() {
        return DocRef.builder(TYPE);
    }
}
