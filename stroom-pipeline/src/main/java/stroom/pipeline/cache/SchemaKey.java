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

package stroom.pipeline.cache;

import stroom.pipeline.xmlschema.FindXMLSchemaCriteria;

import java.io.Serializable;
import java.util.Objects;

public class SchemaKey implements Serializable {

    private static final long serialVersionUID = 8418383654436897040L;

    private final String schemaLanguage;
    private final String data;
    private final FindXMLSchemaCriteria findXMLSchemaCriteria;

    public SchemaKey(final String schemaLanguage,
                     final String data,
                     final FindXMLSchemaCriteria findXMLSchemaCriteria) {
        this.schemaLanguage = schemaLanguage;
        this.data = data;
        this.findXMLSchemaCriteria = findXMLSchemaCriteria;
    }

    public String getSchemaLanguage() {
        return schemaLanguage;
    }

    public String getData() {
        return data;
    }

    public FindXMLSchemaCriteria getFindXMLSchemaCriteria() {
        return findXMLSchemaCriteria;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final SchemaKey schemaKey = (SchemaKey) o;
        return Objects.equals(schemaLanguage, schemaKey.schemaLanguage) &&
                Objects.equals(data, schemaKey.data) &&
                Objects.equals(findXMLSchemaCriteria, schemaKey.findXMLSchemaCriteria);
    }

    @Override
    public int hashCode() {
        return Objects.hash(schemaLanguage, data, findXMLSchemaCriteria);
    }

    @Override
    public String toString() {
        return "SchemaKey{" +
                "schemaLanguage='" + schemaLanguage + '\'' +
                ", data='" + data + '\'' +
                ", findXMLSchemaCriteria=" + findXMLSchemaCriteria +
                '}';
    }
}
