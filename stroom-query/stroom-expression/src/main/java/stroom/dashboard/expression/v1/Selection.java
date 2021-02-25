package stroom.dashboard.expression.v1;

public interface Selection<T> {

    int size();

    T get(int pos);
}
