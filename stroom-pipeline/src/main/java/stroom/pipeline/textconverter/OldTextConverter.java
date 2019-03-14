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

package stroom.pipeline.textconverter;

import stroom.docref.HasDisplayValue;
import stroom.util.shared.HasPrimitiveValue;
import stroom.util.shared.PrimitiveValueConverter;
import stroom.entity.shared.SQLNameConstants;
import stroom.importexport.shared.ExternalFile;
import stroom.importexport.migration.DocumentEntity;

/**
 * Used for legacy migration
 **/
@Deprecated
public class OldTextConverter extends DocumentEntity {
    public static final String DATA = SQLNameConstants.DATA;
    public static final String ENTITY_TYPE = "TextConverter";

    private String description;
    private String data;
    private byte pConverterType = TextConverterType.NONE.getPrimitiveValue();

    public String getDescription() {
        return description;
    }

    public void setDescription(final String description) {
        this.description = description;
    }

    @ExternalFile(extensionProvider = OldTextConverterExtensionProvider.class)
    public String getData() {
        return data;
    }

    public void setData(final String data) {
        this.data = data;
    }

    public byte getPConverterType() {
        return pConverterType;
    }

    public void setPConverterType(final byte pConverterType) {
        this.pConverterType = pConverterType;
    }

    public TextConverterType getConverterType() {
        return TextConverterType.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(pConverterType);
    }

    public void setConverterType(final TextConverterType converterType) {
        this.pConverterType = converterType.getPrimitiveValue();
    }

    public enum TextConverterType implements HasDisplayValue, HasPrimitiveValue {
        NONE("None", 0, "txt"), DATA_SPLITTER("Data Splitter", 1, "xml"), XML_FRAGMENT("XML Fragment", 3, "xml");

        public static final PrimitiveValueConverter<TextConverterType> PRIMITIVE_VALUE_CONVERTER = new PrimitiveValueConverter<>(
                TextConverterType.values());

        // Add fudge to cope with old DATA_SPLITTER_2 enumeration that mapped to
        // 4.
        static {
            PRIMITIVE_VALUE_CONVERTER.put((byte) 4, DATA_SPLITTER);
        }

        private final String displayValue;
        private final byte primitiveValue;
        private final String fileExtension;

        TextConverterType(final String displayValue, final int primitiveValue, final String fileExtension) {
            this.displayValue = displayValue;
            this.primitiveValue = (byte) primitiveValue;
            this.fileExtension = fileExtension;
        }

        @Override
        public String getDisplayValue() {
            return displayValue;
        }

        @Override
        public byte getPrimitiveValue() {
            return primitiveValue;
        }

        public String getFileExtension() {
            return fileExtension;
        }
    }
}
