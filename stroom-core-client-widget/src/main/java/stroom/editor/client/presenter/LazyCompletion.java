package stroom.editor.client.presenter;

import edu.ycp.cs.dh.acegwt.client.ace.AceCompletion;

import java.util.Objects;
import java.util.function.Consumer;

public class LazyCompletion {

    private final Consumer<LazyCompletionCallback> lazyCompleter;
    private AceCompletion aceCompletion = null;

    public LazyCompletion(final Consumer<LazyCompletionCallback> completionCreator) {
        this.lazyCompleter = Objects.requireNonNull(completionCreator);
    }

    public void supplyCompletion(final LazyCompletionCallback callback) {
        if (aceCompletion != null) {
            callback.accept(aceCompletion);
        } else {
            lazyCompleter.accept(aceCompletion -> {
                LazyCompletion.this.aceCompletion = aceCompletion;
                callback.accept(aceCompletion);
            });
        }
    }


    // --------------------------------------------------------------------------------


    public interface LazyCompletionCallback {

        /**
         * This method must be called with the completion.
         * In the event of being unable to provide a completion, pass null.
         */
        void accept(final AceCompletion aceCompletion);
    }
}
