Permission Inheritance Picker in a Form

```jsx
const { compose } = require("recompose");
const { connect } = require("react-redux");
const { Field, reduxForm, FormState } = require("redux-form");

const { GlobalStoreState } = require("../../startup/reducers");

const enhance = compose(
  connect(
    ({ form }) => ({
      thisForm: form.permissionInheritanceTest
    }),
    {}
  ),
  reduxForm({
    form: "permissionInheritanceTest"
  })
);

let TestForm = ({ thisForm }) => (
  <form>
    <div>
      <label>Chosen Permission Inheritance</label>
      <Field
        name="permissionInheritance"
        component={({ input: { onChange, value } }) => (
          <PermissionInheritancePicker onChange={onChange} value={value} />
        )}
      />
    </div>
    {thisForm &&
      thisForm.values && (
        <div>
          <div>
            Permission Inheritance: {thisForm.values.permissionInheritance}
          </div>
        </div>
      )}
  </form>
);

TestForm = enhance(TestForm);

<TestForm />;
```
