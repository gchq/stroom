/*
 * Copyright 2026 Crown Copyright
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

package stroom.query.common.v2;

import stroom.query.api.QueryKey;
import stroom.query.language.functions.ValSerialiser;
import stroom.query.language.functions.ref.KryoDataReader;
import stroom.query.language.functions.ref.KryoDataWriter;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/// Copies a search's join tables from the node that built them to another node that searches the
/// search's first source, so that node can apply the joins to its own rows.
///
/// The tables are written as a stream: for each join line in order, its alias, its fields, then each
/// row as its join key and values. The receiving node builds its own tables from the stream, within
/// its own limits.
public final class JoinTableTransfer {

    private JoinTableTransfer() {
        // Static methods only.
    }

    /// Writes join tables to a stream.
    ///
    /// @param tables       The complete join tables, in the order of the join lines.
    /// @param outputStream The stream to write to, which is flushed but not closed.
    public static void write(final List<JoinTable> tables, final OutputStream outputStream) {
        final Output output = new Output(outputStream);
        final KryoDataWriter writer = new KryoDataWriter(output);
        writer.writeInt(tables.size());
        for (final JoinTable table : tables) {
            writer.writeString(table.getName());
            final List<String> fields = table.getFields();
            writer.writeInt(fields.size());
            fields.forEach(writer::writeString);
            table.forEachRow((key, row) -> {
                writer.writeBoolean(true);
                writer.writeString(key);
                JoinTable.writeRow(writer, row);
            });
            writer.writeBoolean(false);
        }
        output.flush();
    }

    /// Builds join tables from a stream written by [#write(List, OutputStream)].
    ///
    /// @param inputStream The stream to read.
    /// @param queryKey    The key of the search the tables are for.
    /// @param joinTables  Creates the tables.
    /// @return The complete tables, in the order of the join lines. If reading fails, any tables
    /// already created are closed.
    public static List<JoinTable> read(final InputStream inputStream,
                                       final QueryKey queryKey,
                                       final JoinTables joinTables) {
        final List<JoinTable> tables = new ArrayList<>();
        try (final KryoDataReader reader = new KryoDataReader(new Input(inputStream))) {
            final int tableCount = reader.readInt();
            for (int i = 0; i < tableCount; i++) {
                final String alias = reader.readString();
                final int fieldCount = reader.readInt();
                final List<String> fields = new ArrayList<>(fieldCount);
                for (int f = 0; f < fieldCount; f++) {
                    fields.add(reader.readString());
                }
                final JoinTable table = joinTables.create(queryKey, alias, fields);
                tables.add(table);
                while (reader.readBoolean()) {
                    final String key = reader.readString();
                    table.add(key, ValSerialiser.readArray(reader));
                }
                table.complete();
            }
            return tables;
        } catch (final RuntimeException e) {
            tables.forEach(JoinTable::close);
            throw e;
        }
    }
}
