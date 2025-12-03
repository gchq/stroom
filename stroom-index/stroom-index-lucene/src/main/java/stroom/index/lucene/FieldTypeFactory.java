/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.index.lucene;

import stroom.index.shared.LuceneIndexField;

import org.apache.lucene.document.FieldType;
import org.apache.lucene.index.IndexOptions;

final class FieldTypeFactory {

//    private static final int DEFAULT_PRECISION_STEP = 4;

    private FieldTypeFactory() {
        // Utility.
    }

    static FieldType createBasic() {
        final FieldType fieldType = new FieldType();
        fieldType.setIndexOptions(IndexOptions.DOCS);
        fieldType.setTokenized(true);
        fieldType.setStored(false);
        fieldType.setStoreTermVectors(false);
        fieldType.setStoreTermVectorOffsets(false);
        fieldType.setStoreTermVectorPositions(false);
        fieldType.setStoreTermVectorPayloads(false);
        fieldType.setOmitNorms(true);
        return fieldType;
    }

    static FieldType create(final LuceneIndexField indexField) {
        final FieldType fieldType = new FieldType();

        // Set the index options.
        IndexOptions indexOptions = IndexOptions.NONE;

        if (indexField.isIndexed()) {
            indexOptions = IndexOptions.DOCS;
        }
        if (indexField.isTermPositions()) {
            indexOptions = IndexOptions.DOCS_AND_FREQS_AND_POSITIONS;
        }
        fieldType.setIndexOptions(indexOptions);

        // We always tokenize fields.
        fieldType.setTokenized(true);

        // Set stored property.
        fieldType.setStored(indexField.isStored());

        // Set term vector properties.
        fieldType.setStoreTermVectors(indexField.isTermPositions());
        fieldType.setStoreTermVectorOffsets(false);
        fieldType.setStoreTermVectorPositions(indexField.isTermPositions());
        fieldType.setStoreTermVectorPayloads(false);

//        if (IndexFieldType.ID.equals(indexField.getFieldType())) {
//            fieldType.setNumericPrecisionStep(Integer.MAX_VALUE);
//            fieldType.setNumericType(NumericType.LONG);
//        } else if (IndexFieldType.INTEGER_FIELD.equals(indexField.getFieldType())) {
//            fieldType.setNumericPrecisionStep(DEFAULT_PRECISION_STEP);
//            fieldType.setNumericType(NumericType.INT);
//        } else if (IndexFieldType.LONG_FIELD.equals(indexField.getFieldType())) {
//            fieldType.setNumericPrecisionStep(DEFAULT_PRECISION_STEP);
//            fieldType.setNumericType(NumericType.LONG);
//        } else if (IndexFieldType.FLOAT_FIELD.equals(indexField.getFieldType())) {
//            fieldType.setNumericPrecisionStep(DEFAULT_PRECISION_STEP);
//            fieldType.setNumericType(NumericType.FLOAT);
//        } else if (IndexFieldType.DOUBLE_FIELD.equals(indexField.getFieldType())) {
//            fieldType.setNumericPrecisionStep(DEFAULT_PRECISION_STEP);
//            fieldType.setNumericType(NumericType.DOUBLE);
//        } else if (IndexFieldType.DATE_FIELD.equals(indexField.getFieldType())) {
//            fieldType.setNumericPrecisionStep(DEFAULT_PRECISION_STEP);
//            fieldType.setNumericType(NumericType.LONG);
//        } else if (IndexFieldType.NUMERIC_FIELD.equals(indexField.getFieldType())) {
//            fieldType.setNumericPrecisionStep(DEFAULT_PRECISION_STEP);
//            fieldType.setNumericType(NumericType.LONG);
//        }

        // We never do scoring.
        fieldType.setOmitNorms(true);

        fieldType.freeze();

        return fieldType;
    }
}
