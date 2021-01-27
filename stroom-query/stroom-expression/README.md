# stroom-expression

[![Build Status](https://travis-ci.org/gchq/stroom-expression.svg?branch=master)](https://travis-ci.org/gchq/stroom-expression)

This library provides Stroom with the functions for manipulating data on Stroom Dashboards.

See the full documentation for all functions [here](https://github.com/gchq/stroom-docs/blob/master/user-guide/dashboards/expressions.md).

Internally an expression function will create a generator to produce values for a cell based on the expression definition. Each generator is supplied with an array of values for the row it belongs to (multiple sets of array values if a row is an aggregated grouping of multiple rows).

The arguments to functions can either be other functions, literal values, or they can refer to fields on the input data using the ${} syntax.

A FieldIndexMap is used to map named fields to the index of the appropriate value within the supplied row value array.

Example:
``` java
FieldIndexMap fim = FieldIndexMap.forFields("name", "age", "occupation")
Generator g = parseExpression("concat(${name}, ${age})")

g.addData("JDoe", 45, "Butcher")

g.eval
```

```
> JDoe45
```

Example showing multiple values supplied to generator due to some external grouping:
``` java
FieldIndexMap fim = FieldIndexMap.forFields("name", "age", "occupation")
Generator g = parseExpression("count()")

g.addData("JDoe", 45, "Butcher")
g.addData("JBloggs", 23, "Butcher")
g.addData("JSmith", 34, "Butcher")
g.addData("JSmith", 24, "Butcher")

g.eval
```

```
> 4
```

Example showing multiple values supplied to generator due to some external grouping:
``` java
FieldIndexMap fim = FieldIndexMap.forFields("name", "age", "occupation")
Generator g = parseExpression("countUnique(${name})")

g.addData("JDoe", 45, "Butcher")
g.addData("JBloggs", 23, "Butcher")
g.addData("JSmith", 34, "Butcher")
g.addData("JSmith", 24, "Butcher")

g.eval
```

```
> 3
```
