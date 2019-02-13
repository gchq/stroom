package stroom.processor.impl.db;

import stroom.docref.DocRef;
import stroom.entity.shared.BaseResultList;
import stroom.processor.StreamProcessorFilterService;
import stroom.processor.shared.FindStreamProcessorFilterCriteria;
import stroom.processor.shared.Processor;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.QueryData;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
class CachedStreamProcessorFilterServiceImpl implements CachedStreamProcessorFilterService {
//        extends StreamProcessorFilterServiceImpl implements CachedStreamProcessorFilterService {

    // TODO implement a cache

    private final StreamProcessorFilterService streamProcessorFilterService;

    @Inject
    CachedStreamProcessorFilterServiceImpl(
            final StreamProcessorFilterService streamProcessorFilterService) {
        this.streamProcessorFilterService = streamProcessorFilterService;
    }

    @Override
    public BaseResultList<ProcessorFilter> find(final FindStreamProcessorFilterCriteria criteria) {
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public ProcessorFilter createFilter(final Processor streamProcessor,
                                        final QueryData queryData,
                                        final boolean enabled,
                                        final int priority) {
        return streamProcessorFilterService.createFilter(streamProcessor, queryData, enabled, priority);
    }

    @Override
    public ProcessorFilter createFilter(final DocRef pipelineRef,
                                        final QueryData queryData,
                                        final boolean enabled,
                                        final int priority) {
        return streamProcessorFilterService.createFilter(pipelineRef, queryData, enabled, priority);
    }

//    /**
//     * Creates the passes record object in the persistence implementation
//     *
//     * @param processorFilter Object to persist.
//     * @return The persisted object including any changes such as auto IDs
//     */
//    @Override
//    public ProcessorFilter create(final ProcessorFilter processorFilter) {
//        return create(processorFilter);
//    }

//    /**
//     * Update the passed record in the persistence implementation
//     *
//     * @param processorFilter The record to update.
//     * @return The record as it now appears in the persistence implementation
//     */
//    @Override
//    public ProcessorFilter update(final ProcessorFilter processorFilter) {
//        return streamProcessorFilterService.update(processorFilter);
//    }

//    /**
//     * Delete the entity associated with the passed id value.
//     *
//     * @param id The unique identifier for the entity to delete.
//     * @return True if the entity was deleted. False if the id doesn't exist.
//     */
//    @Override
//    public boolean delete(final int id) {
//        return streamProcessorFilterService.delete(id);
//    }

//    /**
//     * Fetch a record from the persistence implementation using its unique id value.
//     *
//     * @param id The id to uniquely identify the required record with
//     * @return The record associated with the id in the database, if it exists.
//     */
//    @Override
//    public Optional<ProcessorFilter> fetch(final int id) {
//        throw new UnsupportedOperationException("TODO");
//    }
}
