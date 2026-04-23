/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.annotation.impl;

import stroom.annotation.shared.AnnotationDecorationFields;
import stroom.annotation.shared.AnnotationIdentity;
import stroom.query.api.datasource.QueryField;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValLong;
import stroom.query.language.functions.ValNull;
import stroom.query.language.functions.ValString;
import stroom.util.concurrent.LazyValue;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A lazy cache of the queryable values for a single annotation, keyed by
 * their {@link QueryField}.
 */
public class AnnotationValues {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AnnotationValues.class);

    private final AnnotationIdentity annotationIdentity;
    // fieldName => Val
    private final Map<String, Val> values = new ConcurrentHashMap<>();
    private final AtomicBoolean isDeleted = new AtomicBoolean(false);
    private final LazyValue<ValLong> lazyAnnotationIdVal;

    public AnnotationValues(final AnnotationIdentity annotationIdentity) {
        Objects.requireNonNull(annotationIdentity);
        this.annotationIdentity = annotationIdentity;
        this.lazyAnnotationIdVal = LazyValue.initialisedBy(() -> ValLong.create(annotationIdentity.getId()));
    }

    public AnnotationIdentity getAnnotationIdentity() {
        return annotationIdentity;
    }

    public Val getAnnotationIdAsVal() {
        // No need to cache this as V
        return lazyAnnotationIdVal.getValueWithoutLocks();
    }

//    public void clear(final QueryField queryField) {
//        LOGGER.trace(() -> LogUtil.message("clear() - annotationIdentity: {}, queryField: {}",
//                annotationIdentity, queryField));
//        Objects.requireNonNull(queryField);
//        values.remove(queryField.getFldName());
//    }

    public void clear(final String fieldName) {
        LOGGER.trace(() -> LogUtil.message("clear() - annotationIdentity: {}, fieldName: {}",
                annotationIdentity, fieldName));
        Objects.requireNonNull(fieldName);
        values.remove(fieldName);
    }

//    public void put(final QueryField queryField, final Val val) {
//        LOGGER.trace(() -> LogUtil.message("put() - queryField: {}, val: {}", queryField, LogUtil.typedValue(val)));
//        values.put(Objects.requireNonNull(queryField).getFldName(), val);
//    }

    public void put(final Collection<FieldValueEntry> fieldValueEntries) {
        LOGGER.trace(() -> LogUtil.message("put() - annotationIdentity: {}, fieldValueEntries: {}",
                annotationIdentity, fieldValueEntries));
        if (!isDeleted.get()) {
            NullSafe.forEach(fieldValueEntries, fieldValueEntry ->
                    values.put(fieldValueEntry.getFldName(), fieldValueEntry.val));
        } else {
            LOGGER.debug("put() - Putting to a deleted object, annotationIdentity: {}, fieldValueEntries: {}",
                    annotationIdentity, fieldValueEntries);
        }
    }

    /**
     * Get the value of a field.
     * Use {@link AnnotationValues#containsKey(QueryField)} if you need to establish if a field's value
     * is known or not.
     *
     * @return The field's value or {@link ValNull} if not known.
     */
    public Val get(final QueryField queryField) {
        Objects.requireNonNull(queryField);
        final String fieldName = Objects.requireNonNull(queryField.getFldName());
        final Val val;
        if (fieldName.equals(AnnotationDecorationFields.ANNOTATION_ID)) {
            val = getAnnotationIdAsVal();
        } else if (fieldName.equals(AnnotationDecorationFields.ANNOTATION_UUID)) {
            val = ValString.create(annotationIdentity.getUuid());
        } else {
            val = Objects.requireNonNullElse(values.get(queryField.getFldName()), ValNull.INSTANCE);
        }

        LOGGER.trace(() -> LogUtil.message("get() - annotationIdentity: {}, queryField: {}, val: {}",
                annotationIdentity, queryField, LogUtil.typedValue(val)));
        return val;
    }

    public boolean containsKey(final QueryField queryField) {
        Objects.requireNonNull(queryField);
        return values.containsKey(queryField.getFldName());
    }

    public boolean isDeleted() {
        return isDeleted.get();
    }

    public void markDeleted() {
        if (isDeleted.compareAndSet(false, true)) {
            values.clear();
        }
    }

    public int size() {
        return values.size();
    }

    @Override
    public String toString() {

        return "AnnotationValues{" +
               "annotationIdentity='" + annotationIdentity + '\'' +
               ", values=" + values +
               ", deleted=" + isDeleted +
               '}';
    }


    // --------------------------------------------------------------------------------


    public record FieldValueEntry(QueryField queryField, Val val) {

        public FieldValueEntry {
            Objects.requireNonNull(queryField);
            Objects.requireNonNull(val);
        }

        public String getFldName() {
            return queryField.getFldName();
        }
    }
}
