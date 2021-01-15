/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.dashboard.expression.v1;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ScanResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class FunctionFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(FunctionFactory.class);
    private final Map<String, Class<? extends Function>> aliasMap = new HashMap<>();
    private final Map<Class<? extends Function>, FunctionDef> functionDefMap = new HashMap<>();

    public FunctionFactory() {
        scanClassPathForFunctions();
//        manuallyAddFunctions();
    }

    private void manuallyAddFunctions() {
        // Aggregate functions.
        add(Max.class, Max.NAME);
        add(Min.class, Min.NAME);
        add(Sum.class, Sum.NAME);
        add(Average.class, Average.NAME, Average.ALIAS);

        add(Round.class, Round.NAME);
        add(RoundYear.class, RoundYear.NAME);
        add(RoundMonth.class, RoundMonth.NAME);
        add(RoundDay.class, RoundDay.NAME);
        add(RoundHour.class, RoundHour.NAME);
        add(RoundMinute.class, RoundMinute.NAME);
        add(RoundSecond.class, RoundSecond.NAME);

        add(Ceiling.class, Ceiling.NAME);
        add(CeilingYear.class, CeilingYear.NAME);
        add(CeilingMonth.class, CeilingMonth.NAME);
        add(CeilingDay.class, CeilingDay.NAME);
        add(CeilingHour.class, CeilingHour.NAME);
        add(CeilingMinute.class, CeilingMinute.NAME);
        add(CeilingSecond.class, CeilingSecond.NAME);

        add(ParseDate.class, ParseDate.NAME);
        add(FormatDate.class, FormatDate.NAME);

        add(ExtractAuthorityFromUri.class, ExtractAuthorityFromUri.NAME);
        add(ExtractFragmentFromUri.class, ExtractFragmentFromUri.NAME);
        add(ExtractHostFromUri.class, ExtractHostFromUri.NAME);
        add(ExtractPathFromUri.class, ExtractPathFromUri.NAME);
        add(ExtractPortFromUri.class, ExtractPortFromUri.NAME);
        add(ExtractQueryFromUri.class, ExtractQueryFromUri.NAME);
        add(ExtractSchemeFromUri.class, ExtractSchemeFromUri.NAME);
        add(ExtractSchemeSpecificPartFromUri.class, ExtractSchemeSpecificPartFromUri.NAME);
        add(ExtractUserInfoFromUri.class, ExtractUserInfoFromUri.NAME);

        add(Floor.class, Floor.NAME);
        add(FloorYear.class, FloorYear.NAME);
        add(FloorMonth.class, FloorMonth.NAME);
        add(FloorDay.class, FloorDay.NAME);
        add(FloorHour.class, FloorHour.NAME);
        add(FloorMinute.class, FloorMinute.NAME);
        add(FloorSecond.class, FloorSecond.NAME);

        add(Replace.class, Replace.NAME);
        add(Concat.class, Concat.NAME);
        add(Link.class, Link.NAME);
        add(Dashboard.class, Dashboard.NAME);
        add(Annotation.class, Annotation.NAME);
        add(Data.class, Data.NAME);
        add(Stepping.class, Stepping.NAME);

        // String functions.
        add(StringLength.class, StringLength.NAME);
        add(UpperCase.class, UpperCase.NAME);
        add(LowerCase.class, LowerCase.NAME);
        add(EncodeUrl.class, EncodeUrl.NAME);
        add(DecodeUrl.class, DecodeUrl.NAME);
        add(IndexOf.class, IndexOf.NAME);
        add(LastIndexOf.class, LastIndexOf.NAME);
        add(Substring.class, Substring.NAME);
        add(SubstringBefore.class, SubstringBefore.NAME);
        add(SubstringAfter.class, SubstringAfter.NAME);
        add(Decode.class, Decode.NAME);
        add(Include.class, Include.NAME);
        add(Exclude.class, Exclude.NAME);
        add(Hash.class, Hash.NAME);

        // Aggregate string functions.
        add(Joining.class, Joining.NAME);

        add(Count.class, Count.NAME);
        add(CountGroups.class, CountGroups.NAME);
        add(CountUnique.class, CountUnique.NAME);

        add(Power.class, Power.NAME, Power.ALIAS);
        add(Divide.class, Divide.NAME, Divide.ALIAS);
        add(Multiply.class, Multiply.NAME, Multiply.ALIAS);
        add(Modulus.class, Modulus.NAME, Modulus.ALIAS1, Modulus.ALIAS2);
        add(Add.class, Add.NAME, Add.ALIAS);
        add(Subtract.class, Subtract.NAME, Subtract.ALIAS);
        add(Negate.class, Negate.NAME);
        add(Equals.class, Equals.NAME, Equals.ALIAS);
        add(GreaterThan.class, GreaterThan.NAME, GreaterThan.ALIAS);
        add(LessThan.class, LessThan.NAME, LessThan.ALIAS);
        add(GreaterThanOrEqualTo.class, GreaterThanOrEqualTo.NAME, GreaterThanOrEqualTo.ALIAS);
        add(LessThanOrEqualTo.class, LessThanOrEqualTo.NAME, LessThanOrEqualTo.ALIAS);

        add(Variance.class, Variance.NAME);
        add(StDev.class, StDev.NAME);

        add(Random.class, Random.NAME);

        // Child value selectors.
        add(Any.class, Any.NAME);
        add(First.class, First.NAME);
        add(Last.class, Last.NAME);
        add(Nth.class, Nth.NAME);
        add(Top.class, Top.NAME);
        add(Bottom.class, Bottom.NAME);

        // Echo statically mapped values
        add(CurrentUser.class, CurrentUser.NAME);
        add(QueryParam.class, QueryParam.NAME);
        add(QueryParams.class, QueryParams.NAME);

        // Logic
        add(If.class, If.NAME);
        add(Match.class, Match.NAME);
        add(Not.class, Not.NAME);

        // Static values
        add(True.class, True.NAME);
        add(False.class, False.NAME);
        add(Null.class, Null.NAME);
        add(Err.class, Err.NAME);

        // Casting
        add(ToBoolean.class, ToBoolean.NAME);
        add(ToDouble.class, ToDouble.NAME);
        add(ToInteger.class, ToInteger.NAME);
        add(ToLong.class, ToLong.NAME);
        add(ToString.class, ToString.NAME);

        // Type Checking
        add(TypeOf.class, TypeOf.NAME);
        add(IsBoolean.class, IsBoolean.NAME);
        add(IsDouble.class, IsDouble.NAME);
        add(IsInteger.class, IsInteger.NAME);
        add(IsLong.class, IsLong.NAME);
        add(IsString.class, IsString.NAME);
        add(IsNumber.class, IsNumber.NAME);
        add(IsValue.class, IsValue.NAME);
        add(IsNull.class, IsNull.NAME);
        add(IsError.class, IsError.NAME);
    }

    private void scanClassPathForFunctions() {
        // Scan the class path to find all the classes with @FunctionDef
        try (ScanResult result = new ClassGraph()
                .whitelistPackages(Function.class.getPackageName())
                .enableAnnotationInfo()
                .enableClassInfo()
                .ignoreClassVisibility()
                .scan()) {

            result.getClassesWithAnnotation(FunctionDef.class.getName())
                    .forEach(classInfo -> {

                        final Class<?> clazz = classInfo.loadClass();
                        if (Function.class.isAssignableFrom(clazz)) {
                            final Class<? extends Function> functionClazz = (Class<? extends Function>) clazz;

                            final FunctionDef functionDef = clazz.getAnnotation(FunctionDef.class);

                            // Convert to a pojo so we can pass over rest
//                            final FunctionDefinition functionDefinition = convertFunctionDef(functionDef);
                            functionDefMap.put(functionClazz, functionDef);

                            LOGGER.debug("Adding function {}", functionClazz.getName());
                        }
                    });
        }
    }

    private void add(final Class<? extends Function> clazz, final String... names) {
        for (final String name : names) {
            aliasMap.put(name.toLowerCase(), clazz);
        }
    }

    public Function create(final String functionName) {
        final Class<? extends Function> clazz = aliasMap.get(functionName.toLowerCase());
        if (clazz != null) {
            try {
                return clazz.getConstructor(String.class).newInstance(functionName);
            } catch (final NoSuchMethodException | InvocationTargetException | InstantiationException
                    | IllegalAccessException e) {
                throw new RuntimeException(e.getMessage());
            }
        }

        return null;
    }


    public Optional<FunctionDef> getFunctionDefinition(final Class<? extends Function> clazz) {
        return Optional.ofNullable(functionDefMap.get(clazz));
    }
//
//    public List<FunctionDefinition> getFunctionDefinitions(final FunctionCategory functionCategory) {
//        return functionDefMap.values()
//                .stream()
//                .filter(functionDefinition ->
//                        functionCategory.equals(functionDefinition.getFunctionCategory()))
//                .collect(Collectors.toList());
//    }

    public List<FunctionDef> getFunctionDefinitions() {
        return new ArrayList<>(functionDefMap.values());
    }
}
