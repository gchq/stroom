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

package stroom.security.shared;

import stroom.util.shared.NullSafe;
import stroom.util.shared.SerialisationTestConstructor;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A set of {@link AppPermission} where one or all must be held depending on the
 * value of operator.
 */
@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class AppPermissionSetImpl implements AppPermissionSet {

    @JsonProperty
    private final AppPermissionOperator operator;
    @JsonProperty
    private final Set<AppPermission> appPermissions;

    @JsonCreator
    AppPermissionSetImpl(@JsonProperty("operator") final AppPermissionOperator operator,
                         @JsonProperty("appPermissions") final Collection<AppPermission> appPermissions) {
        if (operator != AppPermissionOperator.ALL_OF && operator != AppPermissionOperator.ONE_OF) {
            throw new IllegalArgumentException("Unexpected operator " + operator);
        }
        if (NullSafe.size(appPermissions) < 2) {
            throw new IllegalArgumentException("Two or more appPermissions required: " + appPermissions);
        }
        this.operator = operator;
        this.appPermissions = Collections.unmodifiableSet(NullSafe.stream(appPermissions)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(AppPermission.class))));
    }

    @SerialisationTestConstructor
    private AppPermissionSetImpl() {
        this(AppPermissionOperator.ALL_OF,
                Arrays
                        .stream(new AppPermission[]{AppPermission.ADMINISTRATOR, AppPermission.ANNOTATIONS})
                        .collect(Collectors.toList()));
    }

    @Override
    public Set<AppPermission> asSet() {
        return appPermissions;
    }

    @JsonIgnore
    @Override
    public boolean isAllOf() {
        return operator.isAllOf();
    }

    @JsonIgnore
    @Override
    public boolean isAtLeastOneOf() {
        return operator.isAtLeastOneOf();
    }

    public AppPermissionOperator getOperator() {
        return operator;
    }

    @Override
    public Iterator<AppPermission> iterator() {
        return appPermissions.iterator();
    }

    @Override
    public int size() {
        return appPermissions.size();
    }

    @JsonIgnore
    @Override
    public boolean isEmpty() {
        return appPermissions.isEmpty();
    }

    @Override
    public boolean contains(final Object appPermission) {
        return appPermissions.contains(appPermission);
    }

    @Override
    public boolean containsAll(final Collection<?> c) {
        return appPermissions.containsAll(c);
    }

    @Override
    public Stream<AppPermission> stream() {
        return appPermissions.stream();
    }

    public Stream<AppPermission> parallelStream() {
        return appPermissions.parallelStream();
    }

    public void forEach(final Consumer<? super AppPermission> action) {
        appPermissions.forEach(action);
    }

    @Override
    public boolean equals(final Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }
        final AppPermissionSetImpl that = (AppPermissionSetImpl) object;
        return operator == that.operator && Objects.equals(appPermissions, that.appPermissions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(operator, appPermissions);
    }

    @Override
    public String toString() {
        final String op = switch (operator) {
            case ALL_OF -> "All of: ";
            case ONE_OF -> "One of: ";
            default -> throw new IllegalStateException("Unexpected operator " + operator);
        };
        return op + appPermissions.stream()
                .map(AppPermission::name)
                .collect(Collectors.joining(", "));
    }


    // --------------------------------------------------------------------------------


    @JsonPropertyOrder(alphabetic = true)
    @JsonInclude(Include.NON_NULL)
    public static class SingletonAppPermissionSet implements AppPermissionSet {

        private static final AppPermissionOperator OPERATOR = AppPermissionOperator.SINGLE;

        @JsonProperty
        private final AppPermission appPermission;

        @JsonCreator
        SingletonAppPermissionSet(@JsonProperty("appPermission") final AppPermission appPermission) {
            this.appPermission = Objects.requireNonNull(appPermission);
        }

        @SerialisationTestConstructor
        private SingletonAppPermissionSet() {
            this(AppPermission.ADMINISTRATOR);
        }

        @Override
        public Set<AppPermission> asSet() {
            return Collections.singleton(appPermission);
        }

        @JsonIgnore
        @Override
        public boolean isAllOf() {
            return OPERATOR.isAllOf();
        }

        @JsonIgnore
        @Override
        public boolean isAtLeastOneOf() {
            return OPERATOR.isAtLeastOneOf();
        }

        @Override
        public int size() {
            return 1;
        }

        @JsonIgnore
        @Override
        public boolean isEmpty() {
            return false;
        }

        @Override
        public boolean contains(final Object o) {
            return Objects.equals(appPermission, o);
        }

        @Override
        public Iterator<AppPermission> iterator() {
            return new Iterator<>() {
                boolean hasNext = true;

                @Override
                public boolean hasNext() {
                    return hasNext;
                }

                @Override
                public AppPermission next() {
                    if (hasNext) {
                        hasNext = false;
                        return appPermission;
                    } else {
                        throw new NoSuchElementException();
                    }
                }
            };
        }

        @Override
        public boolean containsAll(final Collection<?> c) {
            for (final Object e : c) {
                if (!contains(e)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public String toString() {
            return appPermission.name();
        }

        @Override
        public boolean equals(final Object object) {
            if (this == object) {
                return true;
            }
            if (object == null || getClass() != object.getClass()) {
                return false;
            }
            final SingletonAppPermissionSet that = (SingletonAppPermissionSet) object;
            return appPermission == that.appPermission;
        }

        @Override
        public int hashCode() {
            return Objects.hash(appPermission);
        }

        @Override
        public Stream<AppPermission> stream() {
            return Stream.of(appPermission);
        }
    }


    // --------------------------------------------------------------------------------


    @JsonPropertyOrder(alphabetic = true)
    @JsonInclude(Include.NON_NULL)
    public static class EmptyAppPermissionSet implements AppPermissionSet {

        private static final AppPermissionOperator OPERATOR = AppPermissionOperator.EMPTY;
        static final AppPermissionSet INSTANCE = new EmptyAppPermissionSet(OPERATOR);

        // This field is pointless, but can't seem to get jackson to play ball without it.
        // Empty perm sets are unlikely to be used anyway, so not worth losing sleep over.
        @JsonProperty
        private final AppPermissionOperator operator;

        private static final Iterator<AppPermission> ITERATOR = new Iterator<>() {
            @Override
            public boolean hasNext() {
                return false;
            }

            @Override
            public AppPermission next() {
                throw new NoSuchElementException();
            }
        };

        @JsonCreator
        EmptyAppPermissionSet(@JsonProperty("operator") final AppPermissionOperator operator) {
            // This field is pointless, but can't seem to get jackson to play ball without it.
            // Empty perm sets are unlikely to be used anyway, so not worth losing sleep over.
            if (operator != AppPermissionOperator.EMPTY) {
                throw new IllegalArgumentException("Only EMPTY allowed");
            }
            this.operator = operator;
        }

        @SerialisationTestConstructor
        private EmptyAppPermissionSet() {
            this(AppPermissionOperator.EMPTY);
        }

        @Override
        public Set<AppPermission> asSet() {
            return Collections.emptySet();
        }

        public AppPermissionOperator getOperator() {
            return operator;
        }

        @JsonIgnore
        @Override
        public boolean isAllOf() {
            return operator.isAllOf();
        }

        @JsonIgnore
        @Override
        public boolean isAtLeastOneOf() {
            return operator.isAtLeastOneOf();
        }

        @Override
        public int size() {
            return 0;
        }

        @JsonIgnore
        @Override
        public boolean isEmpty() {
            return true;
        }

        @Override
        public boolean contains(final Object o) {
            return false;
        }

        @Override
        public Iterator<AppPermission> iterator() {
            return ITERATOR;
        }

        @Override
        public boolean containsAll(final Collection<?> c) {
            return false;
        }

        @Override
        public Stream<AppPermission> stream() {
            return Stream.empty();
        }

        @Override
        public String toString() {
            return "";
        }

        @Override
        public boolean equals(final Object object) {
            return object instanceof EmptyAppPermissionSet;
        }

        @Override
        public int hashCode() {
            return 1;
        }
    }
}
