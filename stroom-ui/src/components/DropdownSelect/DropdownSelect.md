Drop Down Select in a Form

```jsx
const { compose } = require("recompose");
const { connect } = require("react-redux");
const { reduxForm, Field, FormState } = require("redux-form");

const { GlobalStoreState } = require("../../startup/reducers");
const { DropdownOptionProps } = require("./DropdownSelect");

const enhance = compose(
  connect(
    ({ form }) => ({
      thisForm: form.dropdownSelectTest
    }),
    {}
  ),
  reduxForm({
    form: "dropdownSelectTest"
  })
);

const toSimpleOption = c => ({
  value: c.toLowerCase(),
  text: c
});

const colourOptions = [
  "Red",
  "Orange",
  "Yellow",
  "Green",
  "Blue",
  "Indigo",
  "Violet"
].map(toSimpleOption);

const ColorOption = ({ option: { text, value }, onClick, inFocus }) => (
  <div className={`hoverable ${inFocus ? "inFocus" : ""}`} onClick={onClick}>
    <span style={{ backgroundColor: value, width: "2rem" }}>&nbsp;</span>
    {text}
  </div>
);

const weekdayOptions = [
  "Monday",
  "Tuesday",
  "Wednesday",
  "Thursday",
  "Friday",
  "Saturday",
  "Sunday"
].map(toSimpleOption);

let TestForm = ({ thisForm }) => (
  <div>
    <form>
      <div>
        <label>Colour</label>
        <Field
          name="colour"
          component={({ input: { onChange, value } }) => (
            <DropdownSelect
              pickerId="colourPicker"
              onChange={onChange}
              value={value}
              options={colourOptions}
              OptionComponent={ColorOption}
            />
          )}
        />
      </div>
      <div>
        <label>Weekday</label>
        <Field
          name="weekday"
          component={({ input: { onChange, value } }) => (
            <DropdownSelect
              pickerId="wdPicker"
              onChange={onChange}
              value={value}
              options={weekdayOptions}
            />
          )}
        />
      </div>
    </form>
    {thisForm &&
      thisForm.values && (
        <form>
          <header>Values Read from the Form State</header>
          <div>
            <label>Colour:</label>
            <input
              readOnly
              value={thisForm.values.colour || ""}
              onChange={() => {}}
            />
          </div>
          <div>
            <label>Weekday:</label>
            <input
              readOnly
              value={thisForm.values.weekday || ""}
              onChange={() => {}}
            />
          </div>
        </form>
      )}
  </div>
);

TestForm = enhance(TestForm);

<TestForm />;
```
