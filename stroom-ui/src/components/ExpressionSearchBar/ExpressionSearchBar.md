Expression Search Bar

```jsx
const { connect } = require("react-redux");
const { compose, lifecycle } = require("recompose");

const {
  ExpressionTerm,
  ExpressionOperator,
  ExpressionBuilder,
  actionCreators: { expressionChanged }
} = require("../ExpressionBuilder");

const {
  testExpression,
  simplestExpression,
  testAndOperator,
  testOrOperator,
  testNotOperator,
  emptyDataSource
} = require("../ExpressionBuilder/queryExpression.testData");

const { testDataSource } = require("../ExpressionBuilder/dataSource.testData");

const enhance = compose(
  connect(
    undefined,
    { expressionChanged }
  ),
  lifecycle({
    componentDidMount() {
      const { expressionChanged, expressionId, testExpression } = this.props;
      expressionChanged(expressionId, testExpression);
    }
  })
);

const TestExpressionSearchBar = enhance(ExpressionSearchBar);

<TestExpressionSearchBar
  testExpression={simplestExpression}
  onSearch={() => console.log("Search called")}
  expressionId="simplestEx"
  searchString="foo1=bar1 foo2=bar2 foo3=bar3 someOtherKey=sometOtherValue"
  dataSource={testDataSource}
/>;
```
