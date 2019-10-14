package stroom.annotation.impl;

import org.springframework.stereotype.Component;
import stroom.annotation.api.AnnotationDataSource;
import stroom.annotation.shared.AnnotationDetail;
import stroom.annotation.shared.CreateEntryRequest;
import stroom.dashboard.expression.v1.Val;
import stroom.datasource.api.v2.DataSource;
import stroom.datasource.api.v2.DataSourceField;
import stroom.entity.shared.ExpressionCriteria;
import stroom.entity.shared.PermissionException;
import stroom.query.api.v2.DocRef;
import stroom.query.api.v2.ExpressionOperator;
import stroom.search.extraction.ExpressionReplacer;
import stroom.searchable.api.Searchable;
import stroom.security.SecurityContext;
import stroom.security.shared.PermissionNames;

import javax.inject.Inject;
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

        final ExpressionReplacer expressionReplacer = new ExpressionReplacer(AnnotationDataSource.ANNOTATION_FIELD_PREFIX, false, securityContext.getUserId());
        ExpressionOperator expression = criteria.getExpression();
        expression = expressionReplacer.copy(expression);
        criteria.setExpression(expression);

        annotationDao.search(criteria, fields, consumer);
    }

    AnnotationDetail getDetail(Long annotationId) {
        checkPermission();
        return annotationDao.getDetail(annotationId);
    }

    AnnotationDetail getDetail(Long metaId, Long eventId) {
        checkPermission();
        return annotationDao.getDetail(metaId, eventId);
    }

    AnnotationDetail createEntry(final CreateEntryRequest request) {
        checkPermission();
        return annotationDao.createEntry(request, securityContext.getUserId());
    }

    private void checkPermission() {
        if (!securityContext.hasAppPermission(PermissionNames.ANNOTATIONS)) {
            throw new PermissionException(securityContext.getUserId(), "You do not have permission to use annotations");
        }
    }
}
