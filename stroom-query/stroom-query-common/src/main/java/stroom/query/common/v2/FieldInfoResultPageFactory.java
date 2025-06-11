package stroom.query.common.v2;

import stroom.query.api.datasource.FindFieldCriteria;
import stroom.query.api.datasource.QueryField;
import stroom.util.PredicateUtil;
import stroom.util.resultpage.ResultPageBuilder;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

public class FieldInfoResultPageFactory {

    private final ExpressionPredicateFactory expressionPredicateFactory;

    @Inject
    private FieldInfoResultPageFactory(final ExpressionPredicateFactory expressionPredicateFactory) {
        this.expressionPredicateFactory = expressionPredicateFactory;
    }

    public ResultPage<QueryField> create(final FindFieldCriteria criteria,
                                         final List<QueryField> fields) {
        final ResultPageBuilder<QueryField> resultPageBuilder = new ResultPageBuilder<>(criteria.getPageRequest());
        final Optional<Predicate<QueryField>> optionalNamePredicate = expressionPredicateFactory
                .createOptional(criteria.getFilter(), QueryField::getFldName);
        final List<Predicate<QueryField>> predicates = new ArrayList<>(2);
        optionalNamePredicate.ifPresent(predicates::add);
        if (criteria.getQueryable() != null) {
            predicates.add(queryField -> queryField.queryable() == criteria.getQueryable());
        }
        final Predicate<QueryField> predicate = PredicateUtil.andPredicates(predicates, name -> true);

        for (final QueryField field : fields) {
            if (predicate.test(field)) {
                if (!resultPageBuilder.add(field)) {
                    break;
                }
            }
        }
        return resultPageBuilder.build();
    }
}
