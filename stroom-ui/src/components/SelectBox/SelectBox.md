Select Box

```jsx
const { compose, withState } = require("recompose");

const enhance = compose(withState("value", "onChange", undefined));
let TestSelectBox = ({ value, onChange, options }) => (
  <div>
    <SelectBox value={value} onChange={onChange} options={options} />
    <header>Observed Value</header>
    <div>{value}</div>
  </div>
);

TestSelectBox = enhance(TestSelectBox);

<TestSelectBox
  options={["red", "green", "blue"].map(o => ({
    text: o,
    value: o
  }))}
/>;
```
