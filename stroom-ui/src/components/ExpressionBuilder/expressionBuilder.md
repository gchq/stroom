Populated Editable

```jsx
const {
  enhanceWithTestExpression,
  testExpression,
  testDataSource
} = require("./test");

const TestExpressionBuilder = enhanceWithTestExpression(ExpressionBuilder);

<TestExpressionBuilder
  testExpression={testExpression}
  showModeToggle
  dataSourceUuid="testDs"
  expressionId="populatedExEdit"
  dataSource={testDataSource}
/>;
```

Populated ReadOnly

```jsx
const {
  enhanceWithTestExpression,
  testExpression,
  testDataSource
} = require("./test");

const TestExpressionBuilder = enhanceWithTestExpression(ExpressionBuilder);
<TestExpressionBuilder
  testExpression={testExpression}
  dataSourceUuid="testDs"
  expressionId="populatedExRO"
  dataSource={testDataSource}
/>;
```

Simplest Editable

```jsx
const {
  enhanceWithTestExpression,
  simplestExpression,
  testDataSource
} = require("./test");

const TestExpressionBuilder = enhanceWithTestExpression(ExpressionBuilder);
<TestExpressionBuilder
  testExpression={simplestExpression}
  showModeToggle
  dataSourceUuid="testDs"
  expressionId="simplestEx"
  dataSource={testDataSource}
/>;
```

Missing Data Source (read only)

```jsx
const {
  enhanceWithTestExpression,
  testExpression,
  testDataSource
} = require("./test");

const TestExpressionBuilder = enhanceWithTestExpression(ExpressionBuilder);
<TestExpressionBuilder
  testExpression={testExpression}
  dataSourceUuid="missingDs"
  expressionId="populatedExNoDs"
  dataSource={testDataSource}
/>;
```

Missing Expression

```jsx
const { testDataSource } = require("./test");

<ExpressionBuilder
  dataSourceUuid="testDs"
  expressionId="missingEx"
  dataSource={testDataSource}
/>;
```

Hide mode toggle

```jsx
const {
  enhanceWithTestExpression,
  testExpression,
  testDataSource
} = require("./test");

const TestExpressionBuilder = enhanceWithTestExpression(ExpressionBuilder);
<TestExpressionBuilder
  testExpression={testExpression}
  showModeToggle={false}
  dataSourceUuid="testDs"
  expressionId="simplestEx"
  dataSource={testDataSource}
/>;
```

Hide mode toggle but be in edit mode

```jsx
const {
  enhanceWithTestExpression,
  testExpression,
  testDataSource
} = require("./test");

const TestExpressionBuilder = enhanceWithTestExpression(ExpressionBuilder);
<TestExpressionBuilder
  testExpression={testExpression}
  showModeToggle={false}
  editMode
  dataSourceUuid="testDs"
  expressionId="simplestEx"
  dataSource={testDataSource}
/>;
```
