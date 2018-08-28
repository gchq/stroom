package stroom.explorer.impl.db;

import org.jooq.Condition;

import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import static org.jooq.impl.DSL.and;
import static org.jooq.impl.DSL.not;
import static org.jooq.impl.DSL.or;

public final class QueryBuilderUtil {
//    public static Condition appendCriteria(boolean nullIsACriterion, StringBuilder queryText, String tableAlias, List<Object> parameterValues, Map<String, Object> criteria, boolean whereWasAppended) {
//        return appendCriteria(nullIsACriterion, queryText, tableAlias, parameterValues, criteria, whereWasAppended, true);
//    }
//
//    public static Condition appendCriteria(boolean nullIsACriterion, StringBuilder queryText, String tableAlias, List<Object> parameterValues, Map<String, Object> criteria, boolean whereWasAppended, boolean startWithWhereOrAnd) {
//        if (criteria != null) {
//            Iterator i$ = criteria.entrySet().iterator();
//
//            while(true) {
//                Entry criterion;
//                do {
//                    if (!i$.hasNext()) {
//                        return whereWasAppended;
//                    }
//
//                    criterion = (Entry)i$.next();
//                } while(criterion.getValue() == null && !nullIsACriterion);
//
//                if (startWithWhereOrAnd) {
//                    queryText.append(whereWasAppended ? " and " : " where ");
//                }
//
//                whereWasAppended = true;
//                startWithWhereOrAnd = true;
//                String aliasedPropertyName = buildAliasedPropertyName(tableAlias, (String)criterion.getKey());
//                if (criterion.getValue() != null) {
//                    queryText.append(aliasedPropertyName + " = " + buildIndexedPlaceHolder(parameterValues) + " ");
//                    parameterValues.add(criterion.getValue());
//                } else {
//                    queryText.append(aliasedPropertyName + " is null ");
//                }
//            }
//        } else {
//            return whereWasAppended;
//        }
//    }
//
//    public static Condition appendValidityConditions(String tableAlias, String validFromPropertyName, Date validFrom, String validToPropertyName, Date validTo, StringBuilder queryText, List<Object> parameters) {
//        Date now = new Date();
//        if (validFromPropertyName != null) {
//            validFromPropertyName = buildAliasedPropertyName(tableAlias, validFromPropertyName);
//            queryText.append(" (" + validFromPropertyName + " is null or " + validFromPropertyName + " <= " + buildIndexedPlaceHolder(parameters) + ") and ");
//            parameters.add(validFrom != null ? validFrom : now);
//        }
//
//        validToPropertyName = buildAliasedPropertyName(tableAlias, validToPropertyName);
//        queryText.append(" (" + validToPropertyName + " is null or " + validToPropertyName + " > " + buildIndexedPlaceHolder(parameters) + ") ");
//        parameters.add(validTo != null ? validTo : now);
//    }
//
//    public static Condition buildAliasedPropertyName(String tableAlias, String propertyName) {
//        return (tableAlias != null ? tableAlias + "." : "") + propertyName;
//    }
//
//    public static Condition buildIndexedPlaceHolder(List<Object> parameters) {
//        return "?" + (parameters.size() + 1);
//    }
//
//    private QueryBuilderUtil() {
//    }
}