package stroom.annotations.impl;

import org.springframework.stereotype.Component;

import javax.inject.Inject;

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
