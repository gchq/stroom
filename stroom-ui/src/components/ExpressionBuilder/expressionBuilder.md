```jsx
const PropTypes = require("prop-types");
const { connect } = require("react-redux");
const { compose, lifecycle } = require("recompose");

const {
  ExpressionTerm,
  ExpressionOperator,
  ExpressionBuilder,
  actionCreators: expressionBuilderActionCreators
} = require("./index");

const {
  actionCreators: folderExplorerActionCreators
} = require("../FolderExplorer");

const { expressionChanged } = expressionBuilderActionCreators;

const {
  testExpression,
  simplestExpression,
  testAndOperator,
  testOrOperator,
  testNotOperator,
  emptyDataSource
} = require("./queryExpression.testData");

const { testDataSource } = require("./dataSource.testData");

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

const TestExpressionBuilder = enhance(ExpressionBuilder);

<div>
  <h1>Populated Editable</h1>
  <TestExpressionBuilder
    testExpression={testExpression}
    showModeToggle
    dataSourceUuid="testDs"
    expressionId="populatedExEdit"
    dataSource={testDataSource}
  />
  <h1>Populated ReadOnly</h1>
  <TestExpressionBuilder
    testExpression={testExpression}
    dataSourceUuid="testDs"
    expressionId="populatedExRO"
    dataSource={testDataSource}
  />
  <h1>Simplest Editable</h1>
  <TestExpressionBuilder
    testExpression={simplestExpression}
    showModeToggle
    dataSourceUuid="testDs"
    expressionId="simplestEx"
    dataSource={testDataSource}
  />
  <h1>Missing Data Source (read only)</h1>
  <TestExpressionBuilder
    testExpression={testExpression}
    dataSourceUuid="missingDs"
    expressionId="populatedExNoDs"
    dataSource={testDataSource}
  />
  <h1>Missing Expression</h1>
  <ExpressionBuilder
    dataSourceUuid="testDs"
    expressionId="missingEx"
    dataSource={testDataSource}
  />
  <h1>Hide mode toggle</h1>
  <TestExpressionBuilder
    testExpression={testExpression}
    showModeToggle={false}
    dataSourceUuid="testDs"
    expressionId="simplestEx"
    dataSource={testDataSource}
  />
  <h1>Hide mode toggle but be in edit mode</h1>
  <TestExpressionBuilder
    testExpression={testExpression}
    showModeToggle={false}
    editMode
    dataSourceUuid="testDs"
    expressionId="simplestEx"
    dataSource={testDataSource}
  />
</div>;
```
