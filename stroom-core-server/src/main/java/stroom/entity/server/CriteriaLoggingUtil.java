/*
 * Copyright 2016 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.entity.server;

import java.util.List;

import stroom.entity.shared.CriteriaSet;
import stroom.entity.shared.EntityIdSet;
import stroom.entity.shared.IncludeExcludeEntityIdSet;
import stroom.entity.shared.PageRequest;
import stroom.entity.shared.Range;
import stroom.util.date.DateUtil;
import event.logging.BaseAdvancedQueryItem;
import event.logging.BaseAdvancedQueryOperator.Not;
import event.logging.BaseAdvancedQueryOperator.Or;
import event.logging.Term;
import event.logging.TermCondition;
import event.logging.util.EventLoggingUtil;

public final class CriteriaLoggingUtil {
    private CriteriaLoggingUtil() {
        // Utility class.
    }

    public static void appendPageRequest(final List<BaseAdvancedQueryItem> items, final PageRequest pageRequest) {
        if (pageRequest != null) {
            if (pageRequest.getOffset() != null) {
                final Term term = EventLoggingUtil.createTerm("pageOffset", TermCondition.EQUALS,
                        Long.toString(pageRequest.getOffset()));
                items.add(term);
            }
            if (pageRequest.getLength() != null) {
                final Term term = EventLoggingUtil.createTerm("pageLength", TermCondition.EQUALS,
                        Long.toString(pageRequest.getLength()));
                items.add(term);
            }
        }
    }

    public static void appendStringTerm(final List<BaseAdvancedQueryItem> items, final String name,
            final String value) {
        if (name != null && value != null) {
            items.add(EventLoggingUtil.createTerm(name, TermCondition.EQUALS, value));
        }
    }

    public static void appendBooleanTerm(final List<BaseAdvancedQueryItem> items, final String name,
            final Boolean value) {
        if (name != null && value != null) {
            items.add(EventLoggingUtil.createTerm(name, TermCondition.EQUALS, value.toString()));
        }
    }

    public static void appendLongTerm(final List<BaseAdvancedQueryItem> items, final String name, final Long value) {
        if (name != null && value != null) {
            items.add(EventLoggingUtil.createTerm(name, TermCondition.EQUALS, value.toString()));
        }
    }

    public static void appendDateTerm(final List<BaseAdvancedQueryItem> items, final String name, final Long value) {
        if (name != null && value != null) {
            items.add(
                    EventLoggingUtil.createTerm(name, TermCondition.EQUALS, DateUtil.createNormalDateTimeString(value)));
        }
    }

    public static void appendUserTerm(final List<BaseAdvancedQueryItem> items, final String userId) {
        if (userId != null) {
            items.add(EventLoggingUtil.createTerm("User", TermCondition.EQUALS, userId));
        }
    }

    public static void appendRangeTerm(final List<BaseAdvancedQueryItem> items, final String name,
            final Range<?> range) {
        if (range != null) {
            if (range.getFrom() != null) {
                items.add(EventLoggingUtil.createTerm(name, TermCondition.GREATER_THAN_EQUAL_TO,
                        range.getFrom().toString()));
            }
            if (range.getTo() != null) {
                items.add(EventLoggingUtil.createTerm(name, TermCondition.LESS_THAN, range.getTo().toString()));
            }
        }
    }

    public static void appendIncludeExcludeEntityIdSet(final List<BaseAdvancedQueryItem> items, final String name,
            final IncludeExcludeEntityIdSet<?> includeExcludeEntityIdSet) {
        if (includeExcludeEntityIdSet != null) {
            if (includeExcludeEntityIdSet.getInclude() != null) {
                appendEntityIdSet(items, name, includeExcludeEntityIdSet.getInclude());
            }

            if (includeExcludeEntityIdSet.getExclude() != null) {
                final Not not = new Not();
                appendEntityIdSet(not.getAdvancedQueryItems(), name, includeExcludeEntityIdSet.getInclude());
                if (not.getAdvancedQueryItems().size() > 0) {
                    items.add(not);
                }
            }
        }
    }

    public static void appendEntityIdSet(final List<BaseAdvancedQueryItem> items, final String name,
            final EntityIdSet<?> idSet) {
        appendCriteriaSet(items, name, idSet);
    }

    public static void appendCriteriaSet(final List<BaseAdvancedQueryItem> items, final String name,
            final CriteriaSet<?> set) {
        if (set != null) {
            final Or or = new Or();

            for (final Object obj : set) {
                if (obj != null) {
                    or.getAdvancedQueryItems()
                            .add(EventLoggingUtil.createTerm(name, TermCondition.EQUALS, obj.toString()));
                }
            }

            if (or.getAdvancedQueryItems().size() == 0) {
                if (set.getMatchNull() != null && set.getMatchNull()) {
                    or.getAdvancedQueryItems().add((EventLoggingUtil.createTerm(name, TermCondition.EQUALS, "NULL")));
                }

                items.add(or);

            } else {
                if (set.getMatchNull() != null && set.getMatchNull()) {
                    items.add((EventLoggingUtil.createTerm(name, TermCondition.EQUALS, "NULL")));
                }
            }
        }
    }
}
