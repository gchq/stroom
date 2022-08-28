package stroom.dashboard.expression.v1;

public interface ChildData {

    Val first();

    Val last();

    Val nth(int pos);

    Val top(String delimiter, int limit);

    Val bottom(String delimiter, int limit);

    Val count();
}
