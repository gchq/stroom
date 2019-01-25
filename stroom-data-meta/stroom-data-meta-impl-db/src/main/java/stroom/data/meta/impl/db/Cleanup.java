package stroom.data.meta.impl.db;

import stroom.entity.shared.Clearable;

import javax.inject.Inject;

public class Cleanup implements Clearable {
    private final MetaValueServiceImpl metaValueService;
    private final MetaKeyServiceImpl metaKeyService;
    private final MetaServiceImpl dataMetaService;
    private final ProcessorServiceImpl processorService;
    private final MetaTypeServiceImpl dataTypeService;
    private final FeedServiceImpl feedService;

    @Inject
    Cleanup(final MetaValueServiceImpl metaValueService,
            final MetaKeyServiceImpl metaKeyService,
            final MetaServiceImpl dataMetaService,
            final ProcessorServiceImpl processorService,
            final MetaTypeServiceImpl dataTypeService,
            final FeedServiceImpl feedService) {
        this.metaValueService = metaValueService;
        this.metaKeyService = metaKeyService;
        this.dataMetaService = dataMetaService;
        this.processorService = processorService;
        this.dataTypeService = dataTypeService;
        this.feedService = feedService;
    }

    @Override
    public void clear() {
        metaValueService.clear();
        metaKeyService.clear();
        dataMetaService.clear();
        processorService.clear();
        dataTypeService.clear();
        feedService.clear();
    }
}
