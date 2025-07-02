package stroom.query.language.functions;

class ArrayValues implements Values {

    private final Val[] array;

    ArrayValues(final Val[] array) {
        this.array = array;
    }

    @Override
    public Val getValue(final int index) {
        return array[index];
    }

    @Override
    public int size() {
        return array.length;
    }

    @Override
    public Val[] toArray() {
        return array;
    }
}
