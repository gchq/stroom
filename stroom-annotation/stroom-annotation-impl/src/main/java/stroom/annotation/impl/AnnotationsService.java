package stroom.annotation.impl;

import org.springframework.stereotype.Component;
import stroom.annotation.shared.Annotation;
import stroom.annotation.shared.AnnotationDetail;
import stroom.annotation.shared.AnnotationEntry;
import stroom.annotation.shared.CreateEntryRequest;
import stroom.security.SecurityContext;

import javax.inject.Inject;

@Component
public class AnnotationsService {
    private final AnnotationsDao annotationsDao;
    private final SecurityContext securityContext;

    @Inject
    AnnotationsService(final AnnotationsDao annotationsDao,
                       final SecurityContext securityContext) {
        this.annotationsDao = annotationsDao;
        this.securityContext = securityContext;
    }

    public Annotation get(String id) {
        final String[] parts = id.split(":");
        final long metaId = Long.parseLong(parts[0]);
        final long eventId = Long.parseLong(parts[1]);
        return annotationsDao.get(metaId, eventId);
    }

//    public Annotation get(Long metaId, Long eventId) {
//        Annotation annotation = new Annotation();
//        annotation.setMetaId(metaId);
//        annotation.setEventId(eventId);
//        annotation.setCreateTime(System.currentTimeMillis());
//        annotation.setCreateUser(securityContext.getUserId());
//        annotation = annotationsDao.get(annotation);
//        return annotation;
//    }

    public AnnotationDetail getDetail(String id) {
        final String[] parts = id.split(":");
        final long metaId = Long.parseLong(parts[0]);
        final long eventId = Long.parseLong(parts[1]);
        return getDetail(metaId, eventId);
    }

    public AnnotationDetail getDetail(Long metaId, Long eventId) {
//        Annotation annotation = new Annotation();
//        annotation.setMetaId(metaId);
//        annotation.setEventId(eventId);
//        annotation.setCreateTime(System.currentTimeMillis());
//        annotation.setCreateUser(securityContext.getUserId());
        return annotationsDao.getDetail(metaId, eventId);
    }

    public AnnotationDetail createEntry(final CreateEntryRequest request) {
        return annotationsDao.createEntry(request, securityContext.getUserId());

//        entry.setCreateTime(System.currentTimeMillis());
//        entry.setCreateUser(securityContext.getUserId());
//        return annotationsDao.createEntry(annotation, entry);
    }
}
