```jsx
const { withState } = require("recompose");

const SingleValueWidget = require("./SingleValueWidget").default;
const InValueWidget = require("./InValueWidget").default;
const BetweenValueWidget = require("./BetweenValueWidget").default;

const withControlledValue = withState("value", "onChange", "");

const CSingleValueWidget = withControlledValue(SingleValueWidget);
const CInValueWidget = withControlledValue(InValueWidget);
const CBetweenValueWidget = withControlledValue(BetweenValueWidget);

["text", "number", "datetime-local"].map(valueType => (
  <div key={valueType}>
    <h1>Value Type {valueType}</h1>
    <div>
      <label>Single Value</label>
      <CSingleValueWidget valueType={valueType} />
    </div>

    <div>
      <label>In</label>
      <CInValueWidget valueType={valueType} />
    </div>

    <div>
      <label>Between Values</label>
      <CBetweenValueWidget valueType={valueType} />
    </div>
  </div>
));
```
