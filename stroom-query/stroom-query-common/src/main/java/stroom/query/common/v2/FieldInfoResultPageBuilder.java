package stroom.query.common.v2;

import stroom.datasource.api.v2.AbstractField;
import stroom.datasource.api.v2.FieldInfo;
import stroom.datasource.api.v2.FindFieldInfoCriteria;
import stroom.util.resultpage.InexactResultPageBuilder;
import stroom.util.resultpage.ResultPageBuilder;
import stroom.util.shared.CriteriaFieldSort;
import stroom.util.shared.ResultPage;
import stroom.util.string.StringMatcher;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class FieldInfoResultPageBuilder {

//    private final FindFieldInfoCriteria criteria;
    private final StringMatcher stringMatcher;

//    private final Set<String> childSet = new HashSet<>();
//    private final List<AbstractField> list = new ArrayList<>();

    private final ResultPageBuilder<FieldInfo> resultPageBuilder;
    private boolean addMore = true;


    private FieldInfoResultPageBuilder(final FindFieldInfoCriteria criteria) {
//        this.criteria = criteria;
        stringMatcher = new StringMatcher(criteria.getStringMatch());

        resultPageBuilder = new InexactResultPageBuilder<>(criteria.getPageRequest());
    }

    public static FieldInfoResultPageBuilder builder(final FindFieldInfoCriteria criteria) {
        return new FieldInfoResultPageBuilder(criteria);
    }

//    public ResultPage<FieldInfo> build() {
//        final ExactResultPageBuilder<FieldInfo> builder = new ExactResultPageBuilder<>(criteria.getPageRequest());
//        final List<AbstractField> fields = sort(list, criteria.getSortList());
//        for (final AbstractField field : fields) {
//            if (stringMatcher.match(field.getName()).isPresent()) {
//                final String id = FieldInfo.FIELDS_PARENT + field.getName();
//                final String ending = id.substring(criteria.getParentPath().length());
//                final int index = ending.indexOf(".");
//                final boolean hasChildren;
//                if (index == -1) {
//                    hasChildren = false;
//                } else {
//                    final String part = ending.substring(0, index);
//                    hasChildren = childSet.contains(part);
//                }
//                final FieldInfo fieldInfo =
//                        new FieldInfo(FieldInfo.FIELDS_PARENT + field.getName(), hasChildren, field.getName(), field);
//                if (!builder.add(fieldInfo)) {
//                    break;
//                }
//            }
//        }
//
//        return builder.build();
//    }

    public FieldInfoResultPageBuilder addAll(final List<AbstractField> fields) {
        for (final AbstractField field : fields) {
            if (!add(field)) {
                break;
            }
        }
        return this;
    }

    public boolean add(final AbstractField field) {
        if (stringMatcher.match(field.getName()).isPresent()) {
            final FieldInfo fieldInfo =
                    new FieldInfo(FieldInfo.FIELDS_PARENT + field.getName(), false, field.getName(), field);
            addMore = resultPageBuilder.add(fieldInfo);
        }
        return addMore;
    }

    public ResultPage<FieldInfo> build() {
        return resultPageBuilder.build();
    }

//    public FieldInfoResultPageBuilder add(final AbstractField field) {
//        final String id = FieldInfo.FIELDS_PARENT + field.getName();
//        if (id.startsWith(criteria.getParentPath())) {
//            final String ending = id.substring(criteria.getParentPath().length());
//            final int index = ending.indexOf(".");
//            if (index == -1) {
//                list.add(field);
//            } else if (stringMatcher.match(field.getName()).isPresent()) {
//                final String part = ending.substring(0, index);
//                childSet.add(part);
//            }
//        }
//        return this;
//    }

    private List<AbstractField> sort(final List<AbstractField> list,
                                     final List<CriteriaFieldSort> sortList) {
        final Comparator<AbstractField> comparator = createComparator(sortList);
        if (comparator == null) {
            return list;
        }

        final List<AbstractField> fields = new ArrayList<>(list);
        fields.sort(comparator);
        return fields;
    }

    private Comparator<AbstractField> createComparator(final List<CriteriaFieldSort> sortList) {
        if (sortList != null && sortList.size() > 0) {
            final CriteriaFieldSort sort = sortList.get(0);
            Comparator<AbstractField> comparator;
            if (FindFieldInfoCriteria.SORT_BY_NAME.equals(sort.getId())) {
                if (sort.isIgnoreCase()) {
                    comparator = Comparator.comparing(AbstractField::getName, String::compareToIgnoreCase);
                } else {
                    comparator = Comparator.comparing(AbstractField::getName);
                }
                if (sort.isDesc()) {
                    comparator = comparator.reversed();
                }
                return comparator;
            }
        }
        return null;
    }
}
