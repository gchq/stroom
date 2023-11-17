package stroom.query.common.v2;

import stroom.datasource.api.v2.FieldInfo;
import stroom.datasource.api.v2.FindFieldInfoCriteria;
import stroom.datasource.api.v2.QueryField;
import stroom.util.resultpage.InexactResultPageBuilder;
import stroom.util.resultpage.ResultPageBuilder;
import stroom.util.shared.ResultPage;
import stroom.util.string.StringMatcher;

import java.util.List;

public class FieldInfoResultPageBuilder {

    private final StringMatcher stringMatcher;

    private final ResultPageBuilder<FieldInfo> resultPageBuilder;
    private boolean addMore = true;


    private FieldInfoResultPageBuilder(final FindFieldInfoCriteria criteria) {
        stringMatcher = new StringMatcher(criteria.getStringMatch());
        resultPageBuilder = new InexactResultPageBuilder<>(criteria.getPageRequest());
    }

    public static FieldInfoResultPageBuilder builder(final FindFieldInfoCriteria criteria) {
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
        if (stringMatcher.match(field.getName()).isPresent()) {
            addMore = resultPageBuilder.add(FieldInfo.create(field));
        }
        return addMore;
    }

    public ResultPage<FieldInfo> build() {
        return resultPageBuilder.build();
    }
}
