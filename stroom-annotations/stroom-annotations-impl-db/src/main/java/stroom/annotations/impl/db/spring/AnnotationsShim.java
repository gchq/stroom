package stroom.annotations.impl.db.spring;

import com.google.inject.Guice;
import com.google.inject.Injector;
import stroom.annotations.impl.AnnotationsModule;
import stroom.annotations.impl.db.AnnotationsConfig;
import stroom.annotations.impl.db.AnnotationsDbModule;
import stroom.search.extraction.AnnotationsDecoratorFactory;

import javax.inject.Inject;

public class AnnotationsShim {
    private static AnnotationsShim INSTANCE;

    @Inject
    private AnnotationsDecoratorFactory receiverDecoratorFactory;

    private AnnotationsShim(final AnnotationsConfig annotationsConfig) {
        final AnnotationsDbModule annotationsDbModule = new AnnotationsDbModule(annotationsConfig);
        final Injector injector = Guice.createInjector(annotationsDbModule, new AnnotationsModule());
        injector.injectMembers(this);
    }

    public static void create(final AnnotationsConfig annotationsConfig) {
        INSTANCE = new AnnotationsShim(annotationsConfig);
    }

    static AnnotationsShim getInstance() {
        if (INSTANCE == null) {
            synchronized (AnnotationsShim.class) {
                if (INSTANCE == null) {
                    INSTANCE = new AnnotationsShim(new AnnotationsConfig());
                }
            }
        }

        return INSTANCE;
    }

    AnnotationsDecoratorFactory getReceiverDecoratorFactory() {
        return receiverDecoratorFactory;
    }
}
