package stroom.annotation.impl;

import org.springframework.stereotype.Component;
import stroom.annotation.shared.Annotation;

import javax.inject.Inject;

@Component
public class AnnotationsService {
    private final AnnotationsDao annotationsDao;

    @Inject
    AnnotationsService(final AnnotationsDao annotationsDao) {
        this.annotationsDao = annotationsDao;
    }

    public Annotation get(Long streamId, Long eventId) {
        return annotationsDao.get(streamId, eventId);
    }
}
