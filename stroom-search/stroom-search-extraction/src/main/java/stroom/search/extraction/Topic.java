package stroom.search.extraction;

import java.util.function.Consumer;
import java.util.function.Supplier;

interface Topic<T> extends Consumer<T>, Supplier<T> {
}
