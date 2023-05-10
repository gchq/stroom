package stroom.dashboard.expression.v1.ref;

public class CountIterationReference extends CountReference {

    private final int iteration;

    CountIterationReference(final int index,
                            final int iteration) {
        super(index);
        this.iteration = iteration;
    }
}
