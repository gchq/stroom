Doc Type Filters in a Test Form

```jsx
const { compose } = require("recompose");
const { connect } = require("react-redux");

const { Field, reduxForm, FormState } = require("redux-form");

const { GlobalStoreState } = require("../../startup/reducers");

const enhance = compose(
  connect(
    ({ form }) => ({
      thisForm: form.docTypeFilterTest,
      initialValues: {
        docTypes: []
      }
    }),
    {}
  ),
  reduxForm({
    form: "docTypeFilterTest"
  })
);

let TestForm = ({ thisForm }) => (
  <form>
    <div>
      <label>Chosen Doc Type</label>
      <Field
        name="docType"
        component={({ input: { onChange, value } }) => (
          <DocRefTypePicker
            pickerId="test1"
            onChange={onChange}
            value={value}
          />
        )}
      />
    </div>
    {thisForm &&
      thisForm.values && (
        <div>
          <div>Doc Type: {thisForm.values.docType}</div>
        </div>
      )}
  </form>
);
TestForm = enhance(TestForm);
<TestForm />;
```
