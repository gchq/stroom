package stroom.annotation.impl;

import org.springframework.stereotype.Component;
import stroom.annotation.api.AnnotationDataSource;
import stroom.annotation.shared.AnnotationDetail;
import stroom.annotation.shared.CreateEntryRequest;
import stroom.annotation.shared.EventId;
import stroom.annotation.shared.EventLink;
import stroom.annotation.shared.SetAssignedToRequest;
import stroom.annotation.shared.SetStatusRequest;
import stroom.dashboard.expression.v1.Val;
import stroom.datasource.api.v2.DataSource;
import stroom.datasource.api.v2.DataSourceField;
import stroom.entity.shared.ExpressionCriteria;
import stroom.entity.shared.PermissionException;
import stroom.query.api.v2.DocRef;
import stroom.query.api.v2.ExpressionOperator;
import stroom.search.extraction.ExpressionFilter;
import stroom.searchable.api.Searchable;
import stroom.security.SecurityContext;
import stroom.security.shared.PermissionNames;

import javax.inject.Inject;
import java.util.List;
import java.util.function.Consumer;

@Component
public class AnnotationService implements Searchable {
    private static final DocRef ANNOTATIONS_PSEUDO_DOC_REF = new DocRef("Searchable", "Annotations", "Annotations");

    private final AnnotationDao annotationDao;
    private final SecurityContext securityContext;

    @Inject
    AnnotationService(final AnnotationDao annotationDao,
                      final SecurityContext securityContext) {
        this.annotationDao = annotationDao;
        this.securityContext = securityContext;
    }

    @Override
    public DocRef getDocRef() {
        try {
            checkPermission();
            return ANNOTATIONS_PSEUDO_DOC_REF;
        } catch (final PermissionException e) {
            return null;
        }
    }

    @Override
    public DataSource getDataSource() {
        checkPermission();
        return new DataSource(AnnotationDataSource.FIELDS);
    }

    @Override
    public void search(final ExpressionCriteria criteria, final DataSourceField[] fields, final Consumer<Val[]> consumer) {
        checkPermission();

        final ExpressionFilter expressionFilter = new ExpressionFilter.Builder()
                .addReplacementFilter(AnnotationDataSource.CURRENT_USER_FUNCTION, securityContext.getUserId())
                .build();

        ExpressionOperator expression = criteria.getExpression();
        expression = expressionFilter.copy(expression);
        criteria.setExpression(expression);

        annotationDao.search(criteria, fields, consumer);
    }

    AnnotationDetail getDetail(Long annotationId) {
        checkPermission();
        return annotationDao.getDetail(annotationId);
    }

    AnnotationDetail getDetail(Long streamId, Long eventId) {
        checkPermission();
        List<AnnotationDetail> list = annotationDao.getAnnotationDetailsForEvents(streamId, eventId);

        // TODO : @66 Just use the first for filtering for now until we decide what it means to have multiple annotations against an event.
        if (list.size() > 0) {
            return list.get(0);
        }
        return null;
    }

    AnnotationDetail createEntry(final CreateEntryRequest request) {
        checkPermission();
        return annotationDao.createEntry(request, securityContext.getUserId());
    }

    List<EventId> getLinkedEvents(final Long annotationId) {
        checkPermission();
        return annotationDao.getLinkedEvents(annotationId);
    }

    List<EventId> link(final EventLink eventLink) {
        checkPermission();
        return annotationDao.link(eventLink);
    }

    List<EventId> unlink(final EventLink eventLink) {
        checkPermission();
        return annotationDao.unlink(eventLink);
    }

    Integer setStatus(SetStatusRequest request) {
        checkPermission();
        return annotationDao.setStatus(request, securityContext.getUserId());
    }

    Integer setAssignedTo(SetAssignedToRequest request) {
        checkPermission();
        return annotationDao.setAssignedTo(request, securityContext.getUserId());
    }

    private void checkPermission() {
        if (!securityContext.hasAppPermission(PermissionNames.ANNOTATIONS)) {
            throw new PermissionException(securityContext.getUserId(), "You do not have permission to use annotations");
        }
    }
}
