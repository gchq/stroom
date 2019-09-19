package stroom.annotations.impl.db.spring;

import org.springframework.stereotype.Component;
import stroom.query.api.v2.Query;
import stroom.search.coprocessor.Receiver;
import stroom.search.extraction.AnnotationsDecoratorFactory;

@Component
public class TempReceiverDecoratorFactory implements AnnotationsDecoratorFactory {
    @Override
    public Receiver create(final Receiver receiver, final Query query) {
        return AnnotationsShim.getInstance().getReceiverDecoratorFactory().create(receiver, query);
    }
}
