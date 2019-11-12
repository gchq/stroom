package stroom.search.resultsender;

import stroom.search.coprocessor.CompletionState;
import stroom.search.coprocessor.Coprocessors;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;

public interface ResultSender {
    CompletionState sendData(Coprocessors coprocessors,
                             Consumer<NodeResult> consumer,
                             long frequency,
                             CompletionState searchComplete,
                             LinkedBlockingQueue<String> errors);
}
