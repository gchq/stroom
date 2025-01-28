package stroom.query.common.v2;

import stroom.datasource.api.v2.FindFieldCriteria;
import stroom.datasource.api.v2.QueryField;
import stroom.util.resultpage.ResultPageBuilder;
import stroom.util.shared.ResultPage;
import stroom.util.string.StringMatcher;

import java.util.List;

public class FieldInfoResultPageBuilder {

    private final StringMatcher stringMatcher;
    private final Boolean queryable;

    private final ResultPageBuilder<QueryField> resultPageBuilder;
    private boolean addMore = true;


    private FieldInfoResultPageBuilder(final FindFieldCriteria criteria) {
        stringMatcher = new StringMatcher(criteria.getStringMatch());
        queryable = criteria.getQueryable();
        resultPageBuilder = new ResultPageBuilder<>(criteria.getPageRequest());
    }

    public static FieldInfoResultPageBuilder builder(final FindFieldCriteria criteria) {
        return new FieldInfoResultPageBuilder(criteria);
    }

    public FieldInfoResultPageBuilder addAll(final List<QueryField> fields) {
        for (final QueryField field : fields) {
            if (!add(field)) {
                break;
            }
        }
        return this;
    }

    public boolean add(final QueryField field) {
        if (queryable == null || field.queryable() == queryable) {
            if (stringMatcher.match(field.getFldName()).isPresent()) {
                addMore = resultPageBuilder.add(field);
            }
        }
        return addMore;
    }

    public ResultPage<QueryField> build() {
        return resultPageBuilder.build();
    }
}
