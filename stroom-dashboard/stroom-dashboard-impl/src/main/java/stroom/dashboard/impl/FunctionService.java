package stroom.dashboard.impl;

import stroom.dashboard.expression.v1.FunctionFactory;
import stroom.dashboard.shared.FunctionSignature;

import java.util.List;

public interface FunctionService {

    List<FunctionSignature> getSignatures();

    FunctionFactory getFunctionFactory();
}
