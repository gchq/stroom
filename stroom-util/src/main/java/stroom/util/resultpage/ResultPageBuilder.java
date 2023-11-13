package stroom.util.resultpage;

import stroom.util.shared.ResultPage;
import stroom.util.shared.ResultPage.ResultConsumer;

public interface ResultPageBuilder<T> extends ResultConsumer<T> {

    int size();

    ResultPage<T> build();
}
