Dialog Action Buttons

```jsx
const { withStateHandlers } = require("recompose");

const enhance = withStateHandlers(
  ({}) => ({ hasConfirmed: false, hasCancelled: false }),
  {
    onCancel: () => () => ({ hasCancelled: true }),
    onConfirm: () => () => ({ hasConfirmed: true }),
    onReset: () => () => ({ hasConfirmed: false, hasCancelled: false })
  }
);

let TestHarness = ({
  hasConfirmed,
  hasCancelled,
  onCancel,
  onConfirm,
  onReset
}) => (
  <div>
    <DialogActionButtons onCancel={onCancel} onConfirm={onConfirm} />
    <form>
      <div>
        <label>Has Confirmed</label>
        <input type="checkbox" checked={hasConfirmed} onChange={() => {}} />
      </div>
      <div>
        <label>Has Cancelled</label>
        <input type="checkbox" checked={hasCancelled} onChange={() => {}} />
      </div>
    </form>
    <button onClick={onReset}>Reset</button>
  </div>
);

TestHarness = enhance(TestHarness);

<TestHarness />;
```
