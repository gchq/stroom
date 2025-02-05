package stroom.core.receive;

import stroom.util.NullSafe;
import stroom.util.io.AbstractDirChangeMonitor;
import stroom.util.io.SimplePathCreator;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.function.Predicate;

@Singleton
public class TemplateConfigDirChangeMonitorImpl extends AbstractDirChangeMonitor {

    private static final Predicate<Path> FILE_INCLUDE_FILTER = path ->
            path != null
            && Files.isRegularFile(path)
            && path.getFileName().toString().endsWith(".json");

    private final Provider<AutoContentCreationConfig> autoContentCreationConfigProvider;
    private final Provider<ContentAutoCreationService> contentAutoCreationServiceProvider;

    @Inject
    public TemplateConfigDirChangeMonitorImpl(
            final Provider<AutoContentCreationConfig> autoContentCreationConfigProvider,
            final SimplePathCreator simplePathCreator,
            final Provider<ContentAutoCreationService> contentAutoCreationServiceProvider) {
        super(
                getDataFeedDir(autoContentCreationConfigProvider, simplePathCreator),
                FILE_INCLUDE_FILTER,
                EnumSet.allOf(EventType.class));
        this.autoContentCreationConfigProvider = autoContentCreationConfigProvider;
        this.contentAutoCreationServiceProvider = contentAutoCreationServiceProvider;
    }

    private static Path getDataFeedDir(
            final Provider<AutoContentCreationConfig> autoContentCreationConfigProvider,
            final SimplePathCreator simplePathCreator) {

        final AutoContentCreationConfig autoContentCreationConfig = autoContentCreationConfigProvider.get();
        return NullSafe.get(
                autoContentCreationConfig.getTemplateConfigDir(),
                simplePathCreator::toAppPath);
    }

    @Override
    protected void onInitialisation() {

    }

    @Override
    protected void onEntryModify(final Path path) {

    }

    @Override
    protected void onEntryCreate(final Path path) {

    }

    @Override
    protected void onEntryDelete(final Path path) {

    }

    @Override
    protected void onOverflow() {

    }
}
