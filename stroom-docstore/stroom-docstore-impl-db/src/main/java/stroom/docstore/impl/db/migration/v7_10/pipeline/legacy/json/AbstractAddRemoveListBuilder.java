package stroom.docstore.impl.db.migration.v7_10.pipeline.legacy.json;

import stroom.util.shared.AbstractBuilder;
import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.List;

public abstract class AbstractAddRemoveListBuilder<E, T extends AbstractAddRemove<E>, B
        extends AbstractAddRemoveListBuilder<E, T, ?>>
        extends AbstractBuilder<T, B> {

    private final List<E> addList = new ArrayList<>();
    private final List<E> removeList = new ArrayList<>();

    public AbstractAddRemoveListBuilder() {

    }

    public AbstractAddRemoveListBuilder(final T init) {
        if (init != null) {
            addList.addAll(NullSafe.list(init.add));
            removeList.addAll(NullSafe.list(init.remove));
        }
    }

    public List<E> getAddList() {
        return addList;
    }

    List<E> copyAddList() {
        return NullSafe.isEmptyCollection(addList)
                ? null
                : new ArrayList<>(addList);
    }

    public List<E> getRemoveList() {
        return removeList;
    }

    List<E> copyRemoveList() {
        return NullSafe.isEmptyCollection(removeList)
                ? null
                : new ArrayList<>(removeList);
    }
}
