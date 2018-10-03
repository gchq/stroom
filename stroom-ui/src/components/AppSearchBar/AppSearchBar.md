App Search as Form Field

```jsx
const { connect } = require("react-redux");
const { compose } = require("recompose");
const { Field, reduxForm } = require("redux-form");

const enhanceForm = compose(
  connect(
    ({ form }) => ({
      thisForm: form.appSearchBarTest
    }),
    {}
  ),
  reduxForm({
    form: "appSearchBarTest",
    enableReinitialize: true,
    touchOnChange: true
  })
);

let AppSearchAsForm = ({ pickerId, typeFilters, thisForm }) => (
  <form>
    <div>
      <label htmlFor="someName">Some Name</label>
      <Field name="someName" component="input" type="text" />
    </div>
    <div>
      <label>Chosen Doc Ref</label>
      <Field
        name="chosenDocRef"
        component={({ input: { onChange, value } }) => (
          <AppSearchBar
            pickerId={pickerId}
            typeFilters={typeFilters}
            onChange={onChange}
            value={value}
          />
        )}
      />
    </div>
    {thisForm &&
      thisForm.values && (
        <div>
          <h3>Form Values Observed</h3>
          Name: {thisForm.values.someName}
          <br />
          Chosen Doc Ref: {thisForm.values.chosenDocRef &&
            thisForm.values.chosenDocRef.name}
        </div>
      )}
  </form>
);

AppSearchAsForm = enhanceForm(AppSearchAsForm);

<AppSearchAsForm pickerId="docRefForm1" />;
```

App Search as Picker

```jsx
const { connect } = require("react-redux");
const { compose, withState } = require("recompose");

const enhancePicker = withState("pickedDocRef", "setPickedDocRef", undefined);

let AppSearchAsPicker = ({
  pickerId,
  typeFilters,
  pickedDocRef,
  setPickedDocRef
}) => (
  <div>
    <AppSearchBar
      pickerId={pickerId}
      typeFilters={typeFilters}
      onChange={setPickedDocRef}
      value={pickedDocRef}
    />
    <div>Picked Doc Ref: {pickedDocRef && pickedDocRef.name}</div>
  </div>
);

AppSearchAsPicker = enhancePicker(AppSearchAsPicker);

<div>
  <AppSearchAsPicker
    pickerId="docRefPicker4"
    typeFilters={["Feed", "Dictionary"]}
  />
  <AppSearchAsPicker pickerId="docRefForm5" typeFilters={["Folder"]} />
</div>;
```

App Search as Navigator

```jsx
class AppSearchAsNavigator extends React.Component {
  constructor(props) {
    super(props);

    this.displayRef = React.createRef();
    this.state = {
      chosenDocRef: undefined
    };
  }
  render() {
    const { pickerId, chosenDocRef, setChosenDocRef } = this.props;

    return (
      <div>
        <AppSearchBar
          pickerId={pickerId}
          onChange={d => {
            console.log("App Search Bar Chose a Value", d);
            this.setState({ chosenDocRef: d });
            this.displayRef.current.focus();
          }}
          value={this.state.chosenDocRef}
        />
        <div tabIndex={0} ref={this.displayRef}>
          {this.state.chosenDocRef
            ? `Would be opening ${this.state.chosenDocRef.name}...`
            : "no doc ref chosen"}
        </div>
      </div>
    );
  }
}

<AppSearchAsNavigator pickerId="global-search" />;
```
