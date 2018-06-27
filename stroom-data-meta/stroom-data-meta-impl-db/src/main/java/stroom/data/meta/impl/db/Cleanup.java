package stroom.data.meta.impl.db;

import stroom.entity.shared.Clearable;

import javax.inject.Inject;

public class Cleanup implements Clearable {
    private final MetaValueServiceImpl metaValueService;
    private final MetaKeyServiceImpl metaKeyService;
    private final DataMetaServiceImpl dataMetaService;
    private final ProcessorServiceImpl processorService;
    private final DataTypeServiceImpl dataTypeService;
    private final FeedServiceImpl feedService;

    @Inject
    Cleanup(final MetaValueServiceImpl metaValueService,
            final MetaKeyServiceImpl metaKeyService,
            final DataMetaServiceImpl dataMetaService,
            final ProcessorServiceImpl processorService,
            final DataTypeServiceImpl dataTypeService,
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
