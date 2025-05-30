package stroom.security.api;

import stroom.security.shared.AppPermission;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A set of {@link AppPermission} where one or all must be held depending on the
 * value of operator.
 */
public class AppPermissionSetImpl implements AppPermissionSet {

    private final Operator operator;
    private final Set<AppPermission> appPermissions;

    AppPermissionSetImpl(final Operator operator,
                         final Set<AppPermission> appPermissions) {
        this.operator = operator;
        this.appPermissions = appPermissions;
    }

    @Override
    public Operator getOperator() {
        return operator;
    }

    @Override
    public boolean isAllOf() {
        return operator == Operator.ALL_OF;
    }

    @Override
    public boolean isAtLeastOneOf() {
        return operator == Operator.ONE_OF;
    }

    @Override
    public Iterator<AppPermission> iterator() {
        return appPermissions.iterator();
    }

    @Override
    public Object[] toArray() {
        return appPermissions.toArray();
    }

    @Override
    public <T> T[] toArray(final T[] a) {
        return appPermissions.toArray(a);
    }

    @Override
    public int size() {
        return appPermissions.size();
    }

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
    public Spliterator<AppPermission> spliterator() {
        return appPermissions.spliterator();
    }

    @Override
    public Stream<AppPermission> stream() {
        return appPermissions.stream();
    }

    @Override
    public Stream<AppPermission> parallelStream() {
        return appPermissions.parallelStream();
    }

    @Override
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
        };
        return op + appPermissions.stream()
                .map(AppPermission::getDisplayValue)
                .collect(Collectors.joining(", "));
    }


    // --------------------------------------------------------------------------------


    public static class SingletonAppPermissionSet implements AppPermissionSet {

        private final AppPermission appPermission;

        SingletonAppPermissionSet(final AppPermission appPermission) {
            this.appPermission = Objects.requireNonNull(appPermission);
        }

        @Override
        public Operator getOperator() {
            return Operator.ALL_OF;
        }

        @Override
        public boolean isAllOf() {
            return true;
        }

        @Override
        public boolean isAtLeastOneOf() {
            return false;
        }

        @Override
        public int size() {
            return 1;
        }

        @Override
        public boolean isEmpty() {
            return false;
        }

        @Override
        public boolean contains(final Object o) {
            return Objects.equals(this, o);
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
        public Object[] toArray() {
            return new AppPermission[]{appPermission};
        }

        @Override
        public <T> T[] toArray(final T[] a) {
            return Collections.singleton(appPermission).toArray(a);
        }

        @Override
        public boolean containsAll(final Collection<?> c) {
            for (Object e : c) {
                if (!contains(e)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public String toString() {
            return appPermission.getDisplayValue();
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
    }


    // --------------------------------------------------------------------------------


    public static class EmptyAppPermissionSet implements AppPermissionSet {

        static AppPermissionSet INSTANCE = new EmptyAppPermissionSet();

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

        private EmptyAppPermissionSet() {
        }

        @Override
        public Operator getOperator() {
            return Operator.ALL_OF;
        }

        @Override
        public boolean isAllOf() {
            return true;
        }

        @Override
        public boolean isAtLeastOneOf() {
            return false;
        }

        @Override
        public int size() {
            return 0;
        }

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
        public Object[] toArray() {
            return new Object[0];
        }

        @Override
        public <T> T[] toArray(final T[] a) {
            return Collections.emptySet().toArray(a);
        }

        @Override
        public boolean containsAll(final Collection<?> c) {
            return false;
        }

        @Override
        public String toString() {
            return "";
        }

        @Override
        public boolean equals(final Object obj) {
            if (obj == this) {
                return true;
            } else {
                if (obj instanceof AppPermissionSet appPermissions) {
                    return appPermissions.isEmpty();
                } else {
                    return false;
                }
            }
        }
    }

    // --------------------------------------------------------------------------------


    public enum Operator {
        /**
         * I.e. permA AND permB
         */
        ALL_OF,
        /**
         * I.e. permA OR permB
         */
        ONE_OF,
        ;
    }
}
