package stroom.state.impl;

import stroom.datasource.api.v2.FindFieldInfoCriteria;
import stroom.datasource.api.v2.QueryField;
import stroom.docref.DocRef;
import stroom.entity.shared.ExpressionCriteria;
import stroom.query.common.v2.FieldInfoResultPageBuilder;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.ValuesConsumer;
import stroom.searchable.api.Searchable;
import stroom.security.api.SecurityContext;
import stroom.state.shared.StateDoc;
import stroom.util.shared.ResultPage;

import com.datastax.oss.driver.api.core.CqlSession;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Optional;

public class SearchableState implements Searchable {

    private final StateDocStore stateDocStore;
    private final CqlSessionFactory cqlSessionFactory;
    private final SecurityContext securityContext;

    @Inject
    public SearchableState(final StateDocStore stateDocStore,
                           final CqlSessionFactory cqlSessionFactory,
                           final SecurityContext securityContext) {
        this.stateDocStore = stateDocStore;
        this.cqlSessionFactory = cqlSessionFactory;
        this.securityContext = securityContext;
    }

    @Override
    public DocRef getDocRef() {
        return new DocRef("Searchable", StateDoc.DOCUMENT_TYPE, "State");
    }

    @Override
    public ResultPage<QueryField> getFieldInfo(final FindFieldInfoCriteria criteria) {
        return FieldInfoResultPageBuilder.builder(criteria).addAll(StateFields.QUERYABLE_FIELDS).build();
    }

    @Override
    public Optional<String> fetchDocumentation(final DocRef docRef) {
        return Optional.empty();
    }

    @Override
    public QueryField getTimeField() {
        return StateFields.EFFECTIVE_TIME_FIELD;
    }

    @Override
    public void search(final ExpressionCriteria criteria, final FieldIndex fieldIndex, final ValuesConsumer consumer) {
        securityContext.useAsRead(() -> {
            final List<DocRef> list = stateDocStore.list();
            for (final DocRef docRef : list) {
                final CqlSession session = cqlSessionFactory.getSession(docRef);
                StateDao.search(session, criteria, fieldIndex, consumer);
                RangedStateDao.search(session, criteria, fieldIndex, consumer);
            }
        });
    }
}
