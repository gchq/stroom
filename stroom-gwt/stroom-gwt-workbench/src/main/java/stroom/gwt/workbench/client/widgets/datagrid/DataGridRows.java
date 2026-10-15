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

package stroom.gwt.workbench.client.widgets.datagrid;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/// The demo rows of the `Widgets/Data Grid/DataGrid` stories.
final class DataGridRows {

    private static final String[] ROLES = {"Engineer", "Designer", "Product", "QA", "Manager"};

    private DataGridRows() {
        // Static utility
    }

    /// Fifteen people.
    ///
    /// @return A new list of the people.
    static List<Person> people() {
        return new ArrayList<>(Arrays.asList(
                new Person(1, "Alice Johnson", "Engineer", 34, true),
                new Person(2, "Bob Smith", "Designer", 28, true),
                new Person(3, "Carol Williams", "Product", 41, false),
                new Person(4, "David Brown", "Engineer", 25, true),
                new Person(5, "Eve Davis", "QA", 36, true),
                new Person(6, "Frank Miller", "Engineer", 31, false),
                new Person(7, "Grace Wilson", "Designer", 29, true),
                new Person(8, "Henry Moore", "Manager", 45, true),
                new Person(9, "Isla Taylor", "Engineer", 27, true),
                new Person(10, "Jack Anderson", "Product", 33, false),
                new Person(11, "Karen Thomas", "QA", 38, true),
                new Person(12, "Liam Jackson", "Engineer", 22, true),
                new Person(13, "Mia White", "Designer", 30, true),
                new Person(14, "Noah Harris", "Engineer", 26, false),
                new Person(15, "Olivia Martin", "Manager", 43, true)));
    }

    /// Generated rows: `Person 1` to `Person <count>`.
    ///
    /// @param count The number of people.
    /// @return A new list of the people.
    static List<Person> generatedPeople(final int count) {
        final List<Person> people = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            people.add(new Person(i + 1,
                    "Person " + (i + 1),
                    ROLES[i % ROLES.length],
                    20 + (i % 45),
                    i % 3 != 0));
        }
        return people;
    }

    /// Rows whose descriptions are long enough to wrap.
    ///
    /// @return A new list of the notes.
    static List<Note> notes() {
        return new ArrayList<>(Arrays.asList(
                new Note(1, "suppressWarnings",
                        "If XSLT cannot be found to match the name pattern suppress warnings."),
                new Note(2, "usePool",
                        "Advanced: Choose whether or not you want to use cached XSLT templates to improve "
                        + "performance. This description is deliberately long so that it wraps onto more "
                        + "than one line at the story width."),
                new Note(3, "xslt", "The XSLT to use."),
                new Note(4, "xsltNamePattern", "A name pattern to load XSLT dynamically.")));
    }

    /// A row of the people grids.
    static final class Person {

        private final int id;
        private final String name;
        private final String role;
        private final int age;
        private final boolean active;

        /// Creates a person.
        ///
        /// @param id     The id.
        /// @param name   The name.
        /// @param role   The role.
        /// @param age    The age.
        /// @param active Whether they are active.
        Person(final int id, final String name, final String role, final int age, final boolean active) {
            this.id = id;
            this.name = name;
            this.role = role;
            this.age = age;
            this.active = active;
        }

        /// @return The id.
        int getId() {
            return id;
        }

        /// @return The name.
        String getName() {
            return name;
        }

        /// @return The role.
        String getRole() {
            return role;
        }

        /// @return The age.
        int getAge() {
            return age;
        }

        /// @return Whether they are active.
        boolean isActive() {
            return active;
        }

        /// @return `Yes` or `No`, as the `Active` column shows it.
        String getActiveText() {
            return active
                    ? "Yes"
                    : "No";
        }
    }

    /// A row of the notes grids.
    static final class Note {

        private final int id;
        private final String name;
        private final String description;

        /// Creates a note.
        ///
        /// @param id          The id, shown as its value.
        /// @param name        The name.
        /// @param description The description.
        Note(final int id, final String name, final String description) {
            this.id = id;
            this.name = name;
            this.description = description;
        }

        /// @return The id.
        int getId() {
            return id;
        }

        /// @return The name.
        String getName() {
            return name;
        }

        /// @return The description.
        String getDescription() {
            return description;
        }
    }
}
