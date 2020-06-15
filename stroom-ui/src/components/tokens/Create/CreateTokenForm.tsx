/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use  file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import Button from "components/Button";
import * as React from "react";
import { DatePicker } from "antd";
import styled from "styled-components";
import UserSelect from "components/UserSelect";
import moment from "moment";
import useConfig from "startup/config/useConfig";

const Field = styled.div`
  display: flex;
  margin: 1em;
  flex-direction: row;
`;
const StyledDatePicker = styled(DatePicker)`
  width: 22.9em;
`;

const SectionFields = styled.div`
  width: 45em;
`;
const Label = styled.div`
  width: 15em;
`;

const UserSelectContainer = styled.div`
  width: 25em;
`;

const CreateTokenForm: React.FunctionComponent<{
  onSubmit: (userId: string, expiryDate: string) => void;
  onBack: () => void;
}> = ({ onSubmit, onBack }) => {
  // TODO: make default validity customisable
  const { defaultApiKeyExpiryInMinutes } = useConfig();
  const initialExpiryDate = moment().add(defaultApiKeyExpiryInMinutes, "m");
  const [expiryDate, setExpiryDate] = React.useState(initialExpiryDate);
  const [selectedUser, setSelectedUser] = React.useState("");
  const handleSubmit = (event: React.FormEvent<HTMLFormElement>) => {
    onSubmit(selectedUser, expiryDate.toISOString());
    event.preventDefault();
  };

  const submitIsDisabled = expiryDate === undefined && selectedUser === "";
  return (
    <div className="CreateTokenForm-card">
      <form onSubmit={handleSubmit}>
        <div className="header">
          <Button
            appearance="default"
            icon="arrow-left"
            text="Back"
            onClick={() => onBack()}
          />
        </div>
        <div className="container">
          <div className="section">
            <div className="section__title">
              <h3>Create a new API key</h3>
            </div>
            <SectionFields>
              <Field>
                <Label>Expiry date</Label>
                <StyledDatePicker
                  value={expiryDate}
                  onChange={(date: moment.Moment) => setExpiryDate(date)}
                />
              </Field>
              <Field>
                <Label>User</Label>
                <UserSelectContainer>
                  <UserSelect fuzzy={false} onChange={setSelectedUser} />
                </UserSelectContainer>
              </Field>
            </SectionFields>
          </div>
        </div>
        <div className="footer">
          <Button
            appearance="default"
            action="primary"
            disabled={submitIsDisabled}
            icon="plus"
            type="submit"
            text="Create"
          />
        </div>
      </form>
    </div>
  );
};

export default CreateTokenForm;
