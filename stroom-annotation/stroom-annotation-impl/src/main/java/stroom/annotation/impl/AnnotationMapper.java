package stroom.annotation.impl;

import stroom.annotation.shared.Annotation;
import stroom.annotation.shared.EventId;
import stroom.query.common.v2.AnnotatedItem;
import stroom.query.common.v2.Item;
import stroom.query.common.v2.ItemMapper;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ref.ErrorConsumer;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.util.List;
import java.util.stream.Stream;


public class AnnotationMapper implements ItemMapper {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AnnotationMapper.class);

    private final AnnotationService annotationService;
    private final ItemMapper parentMapper;
    private final int streamIdIndex;
    private final int eventIdIndex;
    private final boolean outerJoin;
    private final List<Mutator> mutators;
    private final ErrorConsumer errorConsumer;

    public AnnotationMapper(final AnnotationService annotationService,
                            final ItemMapper parentMapper,
                            final int streamIdIndex,
                            final int eventIdIndex,
                            final boolean outerJoin,
                            final List<Mutator> mutators,
                            final ErrorConsumer errorConsumer) {
        this.annotationService = annotationService;
        this.parentMapper = parentMapper;
        this.streamIdIndex = streamIdIndex;
        this.eventIdIndex = eventIdIndex;
        this.outerJoin = outerJoin;
        this.mutators = mutators;
        this.errorConsumer = errorConsumer;
    }

    @Override
    public Stream<Item> create(final Item itm) {
        return parentMapper.create(itm).flatMap(item -> {
            try {
                final long streamId = item.getValue(streamIdIndex).toLong();
                final long eventId = item.getValue(eventIdIndex).toLong();
                final List<Annotation> list = annotationService
                        .getAnnotationsForEvents(new EventId(streamId, eventId));
                if (list.isEmpty() && outerJoin) {
                    // If we don't have any annotations for this row and are allowing an outer join then just return
                    // the row.
                    return Stream.of(item);

                } else {
                    return list
                            .stream()
                            .map(annotation -> {
                                // We need to copy the values array as the mutators alter values in place and if we have
                                // more than one annotation we will overwrite.
                                final Val[] array = item.toArray();
                                final Val[] copy;
                                if (list.size() > 1) {
                                    copy = new Val[array.length];
                                    System.arraycopy(
                                            array,
                                            0,
                                            copy,
                                            0,
                                            array.length);
                                } else {
                                    copy = array;
                                }

                                // Mutate values to add annotation details.
                                for (final Mutator mutator : mutators) {
                                    mutator.mutate(copy, annotation);
                                }

                                return new AnnotatedItem(item.getKey(), copy, annotation.getId());
                            });
                }
            } catch (final RuntimeException e) {
                LOGGER.debug(e.getMessage(), e);
                errorConsumer.add(e);
            }

            return Stream.empty();
        });
    }

    @Override
    public boolean hidesRows() {
        return parentMapper.hidesRows();
    }

    public interface Mutator {

        void mutate(Val[] values, Annotation annotation);
    }
}
