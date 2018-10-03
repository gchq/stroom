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
      <label>Chosen Doc Types</label>
      <Field
        name="docTypes"
        component={({ input: { onChange, value } }) => (
          <DocTypeFilters onChange={onChange} value={value} />
        )}
      />
    </div>
    {thisForm &&
      thisForm.values && (
        <div>
          <div>Doc Types: {thisForm.values.docTypes.join(",")}</div>
        </div>
      )}
  </form>
);
TestForm = enhance(TestForm);
<TestForm />;
```
