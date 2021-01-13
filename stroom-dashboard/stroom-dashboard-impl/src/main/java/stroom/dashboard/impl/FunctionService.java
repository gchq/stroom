package stroom.dashboard.impl;

import stroom.dashboard.shared.FunctionDefinition;
import stroom.dashboard.expression.v1.FunctionFactory;

import java.util.List;

public interface FunctionService {

    List<FunctionDefinition> getFunctionDefinitions();

    FunctionFactory getFunctionFactory();
}
