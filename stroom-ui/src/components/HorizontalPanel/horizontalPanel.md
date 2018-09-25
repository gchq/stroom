div content

```jsx
const loremIpsum = require("lorem-ipsum");
const EnabledCheckbox = () => (
  <label>
    <input type="checkbox" name="checkbox" value="value" />
    &nbsp;Enabled?
  </label>
);

<HorizontalPanel
  title="Some title"
  onClose={() => console.log("closed")}
  content={<div>{loremIpsum({ count: 100, units: "words" })}</div>}
  headerMenuItems={[<EnabledCheckbox />]}
/>;
```

Long Title

```jsx
const loremIpsum = require("lorem-ipsum");
const EnabledCheckbox = () => (
  <label>
    <input type="checkbox" name="checkbox" value="value" />
    &nbsp;Enabled?
  </label>
);

<HorizontalPanel
  title="Some very, very long title"
  onClose={() => console.log("closed")}
  content={<div>{loremIpsum({ count: 100, units: "words" })}</div>}
  headerMenuItems={[<EnabledCheckbox />]}
/>;
```

long title with adjusted columns

```jsx
const loremIpsum = require("lorem-ipsum");
const EnabledCheckbox = () => (
  <label>
    <input type="checkbox" name="checkbox" value="value" />
    &nbsp;Enabled?
  </label>
);

<HorizontalPanel
  title="Some very, very long title"
  onClose={() => console.log("closed")}
  content={<div>{loremIpsum({ count: 100, units: "words" })}</div>}
  headerMenuItems={[<EnabledCheckbox />]}
  titleColumns="8"
  menuColumns="8"
/>;
```

With different sized header

```jsx
const loremIpsum = require("lorem-ipsum");
const EnabledCheckbox = () => (
  <label>
    <input type="checkbox" name="checkbox" value="value" />
    &nbsp;Enabled?
  </label>
);

<HorizontalPanel
  title="A smaller header"
  onClose={() => console.log("closed")}
  content={<div>{loremIpsum({ count: 100, units: "words" })}</div>}
  headerMenuItems={[<EnabledCheckbox />]}
  headerSize="h4"
/>;
```

with lots of content

```jsx
const loremIpsum = require("lorem-ipsum");
const EnabledCheckbox = () => (
  <label>
    <input type="checkbox" name="checkbox" value="value" />
    &nbsp;Enabled?
  </label>
);

<HorizontalPanel
  title="A smaller header"
  onClose={() => console.log("closed")}
  content={<div>{loremIpsum({ count: 6000, units: "words" })}</div>}
  headerMenuItems={[<EnabledCheckbox />]}
  headerSize="h4"
/>;
```
