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

package stroom.cache.server;

import stroom.util.shared.EqualsBuilder;
import stroom.util.shared.HashCodeBuilder;

import java.io.Serializable;

public class SchemaKey implements Serializable {
    private static final long serialVersionUID = 8418383654436897040L;

    private final String schemaLanguage;
    private final String data;
    private final String user;

    public SchemaKey(final String schemaLanguage, final String data, final String user) {
        this.schemaLanguage = schemaLanguage;
        this.data = data;
        this.user = user;
    }

    public String getSchemaLanguage() {
        return schemaLanguage;
    }

    public String getData() {
        return data;
    }

    public String getUser() {
        return user;
    }

    @Override
    public int hashCode() {
        final HashCodeBuilder builder = new HashCodeBuilder();
        builder.append(schemaLanguage);
        builder.append(data);
        builder.append(user);
        return builder.toHashCode();
    }

    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof SchemaKey)) {
            return false;
        }

        final SchemaKey schemaKey = (SchemaKey) o;
        final EqualsBuilder builder = new EqualsBuilder();
        builder.append(schemaLanguage, schemaKey.schemaLanguage);
        builder.append(data, schemaKey.data);
        builder.append(user, schemaKey.user);

        return builder.isEquals();
    }
}
