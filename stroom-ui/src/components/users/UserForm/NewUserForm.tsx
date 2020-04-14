import * as React from "react";
import useForm from "react-hook-form";
import Button from "components/Button";
import { Icon, Input, Select, Switch } from "antd";
import BackConfirmation from "../BackConfirmation";
import EditUserFormProps from "./EditUserFormProps";
import styled from "styled-components";
import { MandatoryIndicator } from "components/FormComponents";

const { Option } = Select;
const { TextArea } = Input;

const Row = styled.div`
  display: flex;
  flex-direction: row;
`;

const Label = styled.label`
  width: 13em;
  text-align: right;
  padding-right: 1em;
  line-height: 2.5em;
`;

// /** The select in the input makes the label drop too low and this fixes that. */
// const LabelForSelect = styled(Label)`
//   line-height: 2.5em;
// `;
const LabelForSwitch = styled(Label)`
  line-height: 1.5em;
`;

const InputAndValidation = styled.div`
  display: flex;
  flex-direction: column;
  height: 5em;
  width: 100%;
`;

const StyledInput = styled(Input)`
  height: 2.5em;
  width: 27.5em;
`;

const StyledPasswordInput = styled(Input.Password)`
  height: 2.5em;
  width: 27.5em;
`;

const StyledSelect = styled(Select)`
  width: 27.5em !important;
`;

const StyledTextArea = styled(TextArea)`
  width: 27.4em !important;
`;

const ValidationMessage = styled.span`
  color: red;
`;

const UserForm: React.FunctionComponent<EditUserFormProps> = ({
                                                                onSubmit,
                                                                onBack,
                                                                onCancel,
                                                                onValidate,
                                                              }) => {
  const {
    register,
    handleSubmit,
    watch,
    errors,
    formState,
    setValue,
    triggerValidation,
  } = useForm({
    mode: "onBlur",
  });
  const [showBackConfirmation, setShowBackConfirmation] = React.useState(false);

  const password = watch("password");
  const verifyPassword = watch("verifyPassword");
  const email = watch("email");

  //TODO this is used in LoginForm too -- move it somewhere?
  const handleInputChange = async (name: string, value: string) => {
    setValue(name, value);
    await triggerValidation({ name });
  };

  React.useEffect(() => {
    register({ name: "firstName", type: "custom" });
    register({ name: "lastName", type: "custom" });
    register({ name: "email", type: "custom" }, { required: true });
    register({ name: "comments", type: "custom" });
    register(
      { name: "password", type: "custom" },
      {
        required: true,
        validate: async value => await onValidate(value, verifyPassword, email),
      },
    );
    register(
      { name: "verifyPassword", type: "custom" },
      {
        required: true,
        validate: async value => await onValidate(password, value, email),
      },
    );
  }, [register, verifyPassword, password, email, onValidate]);

  return (
    <form onSubmit={handleSubmit(onSubmit)}>
      <div className="header">
        <Button
          type="button"
          onClick={() =>
            formState.dirty ? setShowBackConfirmation(true) : onBack()
          }
          icon="arrow-left"
          text="Back"
        />
      </div>
      <div>
        <div className="container">
          <input name="id" type="hidden"/>
          <Row>
            <Label>First name:</Label>
            <InputAndValidation>
              <StyledInput
                name="firstName"
                type="text"
                onChange={async e =>
                  handleInputChange("firstName", e.target.value)
                }
              />
            </InputAndValidation>
          </Row>
          <Row>
            <Label>Last name:</Label>
            <InputAndValidation>
              <StyledInput
                name="lastName"
                type="text"
                onChange={async e =>
                  handleInputChange("lastName", e.target.value)
                }
              />
            </InputAndValidation>
          </Row>
          <Row>
            <Label>
              <MandatoryIndicator/>
              Email:
            </Label>
            <InputAndValidation>
              <StyledInput
                name="email"
                onChange={async e => handleInputChange("email", e.target.value)}
              />
              <ValidationMessage>
                {errors.email && errors.email.message}
              </ValidationMessage>
            </InputAndValidation>
          </Row>

          <Row>
            <Label>Account status:</Label>
            <InputAndValidation>
              <StyledSelect>
                <Option value="enabled">Active</Option>
                <Option value="disabled">Disabled</Option>
                <Option disabled value="inactive">
                  Inactive (because of disuse)
                </Option>
                <Option disabled value="locked">
                  Locked (because of failed logins)
                </Option>
              </StyledSelect>
            </InputAndValidation>
          </Row>

          <Row>
            <LabelForSwitch>Never expires?</LabelForSwitch>
            <InputAndValidation>
              <div>
                <Switch ref={register}/>
              </div>
            </InputAndValidation>
          </Row>

          <Row>
            <Label>
              <MandatoryIndicator/>
              Password:
            </Label>
            <InputAndValidation>
              <StyledPasswordInput
                name="password"
                type="password"
                prefix={
                  <Icon type="lock" style={{ color: "rgba(0,0,0,.25)" }}/>
                }
                onChange={async e =>
                  handleInputChange("password", e.target.value)
                }
                // ref={register({
                //   required: true,
                //   validate: value => onValidate(value, verifyPassword, email),
                // })}
              />
              <ValidationMessage>
                {errors.password && errors.password.message}
              </ValidationMessage>
            </InputAndValidation>
          </Row>
          <Row>
            <Label>
              <MandatoryIndicator/>
              Verify password:
            </Label>
            <InputAndValidation>
              <StyledPasswordInput
                name="verifyPassword"
                type="password"
                prefix={
                  <Icon type="lock" style={{ color: "rgba(0,0,0,.25)" }}/>
                }
                onChange={async e =>
                  handleInputChange("verifyPassword", e.target.value)
                }
                // ref={register({
                //   required: true,
                //   validate: value => onValidate(value, verifyPassword, email),
                // })}
              />
              <ValidationMessage>
                {errors.verifyPassword && errors.verifyPassword.message}
              </ValidationMessage>
            </InputAndValidation>
          </Row>

          <Row>
            <LabelForSwitch>
              Force a password change at next login?
            </LabelForSwitch>
            <InputAndValidation>
              <div>
                <Switch ref={register}/>
              </div>
            </InputAndValidation>
          </Row>

          <Row>
            <Label>Comments:</Label>
            <InputAndValidation>
              <StyledTextArea
                rows={3}
                className="section__fields__comments"
                name="comments"
                onChange={async e =>
                  handleInputChange("comments", e.target.value)
                }
              />
            </InputAndValidation>
          </Row>

          {/* {showCalculatedFields && !!userBeingEdited ? (
              <React.Fragment>
                {!!userBeingEdited.loginCount ? (
                  <div className="section">
                    <div className="section__title">
                      <h3>Audit</h3>
                    </div>
                    <div className="section__fields--copy-only">
                      <div className="section__fields_row">
                        <LoginFailureCopy
                          attemptCount={userBeingEdited.loginCount}
                        />
                        <LoginStatsCopy
                          lastLogin={userBeingEdited.lastLogin}
                          loginCount={userBeingEdited.loginCount}
                          dateFormat={dateFormat}
                        />
                      </div>
                    </div>
                  </div>
                ) : (
                  undefined
                )}

                <div className="section">
                  <div className="section__title">
                    <h3>Audit</h3>
                  </div>
                  <div className="section__fields--copy-only">
                    <div className="section__fields__rows">
                      <AuditCopy
                        createdOn={userBeingEdited.createdOn}
                        createdBy={userBeingEdited.createdByUser}
                        updatedOn={userBeingEdited.updatedOn}
                        updatedBy={userBeingEdited.updatedByUser}
                        dateFormat={dateFormat}
                      />
                    </div>
                  </div>
                </div>
              </React.Fragment>
            ) : (
              undefined
            )} */}
        </div>
      </div>

      <div className="footer">
        <Button
          appearance="contained"
          action="primary"
          type="submit"
          disabled={!formState.dirty || !formState.isValid}
          icon="save"
          text="Save"
          // isLoading={isSaving}
        />
        <Button
          appearance="contained"
          action="secondary"
          icon="times"
          onClick={() => onCancel()}
          text="Cancel"
        />
      </div>
      <BackConfirmation
        isOpen={showBackConfirmation}
        onGoBack={() => onBack()}
        hasErrors={!formState.isValid}
        onSaveAndGoBack={onSubmit}
        onContinueEditing={() => setShowBackConfirmation(false)}
      />
    </form>
  );
};

export default UserForm;
