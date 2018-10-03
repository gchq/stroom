Themed Modal

```jsx
const { compose, withState } = require("recompose");

const Button = require("../Button").default;

const withModalOpen = compose(
  withState("modalIsOpen", "setModalIsOpen", false)
);

let TestModal = ({ modalIsOpen, setModalIsOpen }) => (
  <React.Fragment>
    <ThemedModal
      isOpen={modalIsOpen}
      header={<h3>This is the header</h3>}
      content={<div>Maybe put something helpful in here</div>}
      actions={
        <React.Fragment>
          <Button text="Nothing" onClick={() => setModalIsOpen(false)} />
          <Button text="Something" onClick={() => setModalIsOpen(false)} />
        </React.Fragment>
      }
      onRequestClose={() => setModalIsOpen(false)}
    />
    <Button onClick={() => setModalIsOpen(!modalIsOpen)} text="Open" />
  </React.Fragment>
);

TestModal = withModalOpen(TestModal);

<TestModal />;
```
