package stroom.data.meta.impl.db;

import stroom.entity.shared.Clearable;

import javax.inject.Inject;

public class Cleanup implements Clearable {
    private final MetaValueServiceImpl metaValueService;
    private final MetaKeyServiceImpl metaKeyService;
    private final StreamMetaServiceImpl streamMetaService;
    private final ProcessorServiceImpl processorService;
    private final StreamTypeServiceImpl streamTypeService;
    private final FeedServiceImpl feedService;

    @Inject
    Cleanup(final MetaValueServiceImpl metaValueService,
            final MetaKeyServiceImpl metaKeyService,
            final StreamMetaServiceImpl streamMetaService,
            final ProcessorServiceImpl processorService,
            final StreamTypeServiceImpl streamTypeService,
            final FeedServiceImpl feedService) {
        this.metaValueService = metaValueService;
        this.metaKeyService = metaKeyService;
        this.streamMetaService = streamMetaService;
        this.processorService = processorService;
        this.streamTypeService = streamTypeService;
        this.feedService = feedService;
    }

    @Override
    public void clear() {
        metaValueService.clear();
        metaKeyService.clear();
        streamMetaService.clear();
        processorService.clear();
        streamTypeService.clear();
        feedService.clear();
    }
}
