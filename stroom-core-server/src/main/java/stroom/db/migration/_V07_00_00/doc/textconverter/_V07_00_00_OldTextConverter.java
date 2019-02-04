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

package stroom.db.migration._V07_00_00.doc.textconverter;

import stroom.db.migration._V07_00_00.doc.pipeline._V07_00_00_OldTextConverterExtensionProvider;
import stroom.db.migration._V07_00_00.docref._V07_00_00_HasDisplayValue;
import stroom.db.migration._V07_00_00.entity.shared._V07_00_00_Copyable;
import stroom.db.migration._V07_00_00.entity.shared._V07_00_00_DocumentEntity;
import stroom.db.migration._V07_00_00.entity.shared._V07_00_00_ExternalFile;
import stroom.db.migration._V07_00_00.entity.shared._V07_00_00_HasData;
import stroom.db.migration._V07_00_00.entity.shared._V07_00_00_HasPrimitiveValue;
import stroom.db.migration._V07_00_00.entity.shared._V07_00_00_PrimitiveValueConverter;
import stroom.db.migration._V07_00_00.entity.shared._V07_00_00_SQLNameConstants;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Lob;
import javax.persistence.Table;
import javax.persistence.Transient;

@Entity
@Table(name = "TXT_CONV")
public class _V07_00_00_OldTextConverter
        extends _V07_00_00_DocumentEntity
        implements _V07_00_00_Copyable<_V07_00_00_OldTextConverter>, _V07_00_00_HasData {

    public static final String TABLE_NAME = _V07_00_00_SQLNameConstants.TEXT + SEP + _V07_00_00_SQLNameConstants.CONVERTER;
    public static final String FOREIGN_KEY = FK_PREFIX + TABLE_NAME + ID_SUFFIX;
    public static final String CONVERTER_TYPE = _V07_00_00_SQLNameConstants.CONVERTER + SEP + _V07_00_00_SQLNameConstants.TYPE;
    public static final String DATA = _V07_00_00_SQLNameConstants.DATA;
    public static final String ENTITY_TYPE = "TextConverter";

    private static final long serialVersionUID = 4519634323788508083L;

    private String description;
    private String data;
    private byte pConverterType = _V07_00_00_TextConverterType.NONE.getPrimitiveValue();

    @Column(name = _V07_00_00_SQLNameConstants.DESCRIPTION)
    @Lob
    public String getDescription() {
        return description;
    }

    public void setDescription(final String description) {
        this.description = description;
    }

    @Column(name = DATA, length = Integer.MAX_VALUE)
    @Lob
    @_V07_00_00_ExternalFile(extensionProvider = _V07_00_00_OldTextConverterExtensionProvider.class)
    @Override
    public String getData() {
        return data;
    }

    @Override
    public void setData(final String data) {
        this.data = data;
    }

    @Column(name = CONVERTER_TYPE, nullable = false)
    public byte getPConverterType() {
        return pConverterType;
    }

    public void setPConverterType(final byte pConverterType) {
        this.pConverterType = pConverterType;
    }

    @Transient
    public _V07_00_00_TextConverterType getConverterType() {
        return _V07_00_00_TextConverterType.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(pConverterType);
    }

    public void setConverterType(final _V07_00_00_TextConverterType converterType) {
        this.pConverterType = converterType.getPrimitiveValue();
    }

    /**
     * @return generic UI drop down value
     */
    @Transient
    @Override
    public String getDisplayValue() {
        return String.valueOf(getName());
    }

    @Override
    public void copyFrom(final _V07_00_00_OldTextConverter textConverter) {
        this.setDescription(textConverter.description);
        this.setPConverterType(textConverter.pConverterType);
        this.setData(textConverter.data);

        super.copyFrom(textConverter);
    }

    @Transient
    @Override
    public final String getType() {
        return ENTITY_TYPE;
    }

    public enum _V07_00_00_TextConverterType implements _V07_00_00_HasDisplayValue, _V07_00_00_HasPrimitiveValue {
        NONE("None", 0, "txt"), DATA_SPLITTER("Data Splitter", 1, "xml"), XML_FRAGMENT("XML Fragment", 3, "xml");

        public static final _V07_00_00_PrimitiveValueConverter<_V07_00_00_TextConverterType> PRIMITIVE_VALUE_CONVERTER =
                new _V07_00_00_PrimitiveValueConverter<>(_V07_00_00_TextConverterType.values());

        // Add fudge to cope with old DATA_SPLITTER_2 enumeration that mapped to
        // 4.
        static {
            PRIMITIVE_VALUE_CONVERTER.put((byte) 4, DATA_SPLITTER);
        }

        private final String displayValue;
        private final byte primitiveValue;
        private final String fileExtension;

        _V07_00_00_TextConverterType(final String displayValue, final int primitiveValue, final String fileExtension) {
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
