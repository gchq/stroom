package stroom.query.language.functions;

import stroom.query.language.functions.ref.StoredValues;

import java.net.InetAddress;
import java.util.function.Supplier;

@FunctionDef(
        name = HostAddress.NAME,
        commonCategory = FunctionCategory.STRING,
        commonReturnType = ValString.class,
        signatures = @FunctionSignature(
                description = "Returns the host address (IP) for the given host string.",
                args = {
                        @FunctionArg(
                                name = "host",
                                argType = ValString.class,
                                description = "The host address or name.")
                }
        )
)
class HostAddress extends AbstractFunction {

    static final String NAME = "hostAddress";

    public HostAddress() {
        super(NAME, 1, 1);
    }

    public HostAddress(final String functionName) {
        super(NAME, 1, 1);
    }

    public HostAddress(final Object expressionContext, final String functionName) {
        super(NAME, 1, 1);
    }

    @Override
    public Generator createGenerator() {
        final Param param = params[0];
        if (param instanceof Function) {
            final Generator gen = ((Function) param).createGenerator();
            return new Generator() {
                @Override
                public void set(Val[] values, StoredValues storedValues) {
                    gen.set(values, storedValues);
                }

                @Override
                public Val eval(StoredValues storedValues, Supplier<ChildData> childDataSupplier) {
                    final Val val = gen.eval(storedValues, childDataSupplier);
                    return resolveHostAddress(val);
                }

                @Override
                public void merge(StoredValues existingValues, StoredValues newValues) {
                    gen.merge(existingValues, newValues);
                }
            };
        } else if (param instanceof Val) {
            final Val val = (Val) param;
            return new Generator() {
                @Override
                public void set(Val[] values, StoredValues storedValues) {
                    // No-op
                }

                @Override
                public Val eval(StoredValues storedValues, Supplier<ChildData> childDataSupplier) {
                    return resolveHostAddress(val);
                }

                @Override
                public void merge(StoredValues existingValues, StoredValues newValues) {
                    // No-op
                }
            };
        } else {
            throw new RuntimeException("Invalid parameter type for HostAddress: " + param.getClass());
        }
    }

    private Val resolveHostAddress(Val val) {
        if (val == null || val.type().isError()) {
            return val;
        }
        final String host = val.toString();
        try {
            return ValString.create(InetAddress.getByName(host).getHostAddress());
        } catch (Exception e) {
            return ValErr.create(e.getMessage());
        }
    }

    @Override
    public boolean hasAggregate() {
        return false;
    }
}
