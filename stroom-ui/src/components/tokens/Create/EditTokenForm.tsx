/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
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

import * as React from "react";
import CopyToClipboard from "react-copy-to-clipboard";
import Toggle from "react-toggle";
import { ByCopy, OnCopyMs } from "components/auditCopy";
import Button from "components/Button";
import Loader from "components/Loader";
import { Token } from "../api/types";
import useConfig from "startup/config/useConfig";

const EditTokenForm: React.FunctionComponent<{
  onBack: () => void;
  onChangeState: (id: number, newState: boolean) => void;
  token: Token;
}> = ({ onBack, onChangeState, token }) => {
  const {
    uiPreferences: { dateFormat },
  } = useConfig();
  return (
    <form>
      <div className="header">
        <Button icon="arrow-left" onClick={() => onBack()}>
          Back
        </Button>
      </div>
      {token === undefined ? (
        <div className="loader-container">
          <Loader message="Loading token" />
        </div>
      ) : (
        <div className="container">
          <div className="section">
            <div className="section__title">
              <h3>Details</h3>
            </div>
            <div className="section__fields">
              <div className="section__fields__row">
                <div className="field-container">
                  <div className="label-container">
                    <label>Enabled</label>
                  </div>
                  <Toggle
                    icons={false}
                    checked={token.enabled}
                    onChange={() =>
                      !!token.id
                        ? onChangeState(token.id, !token.enabled)
                        : undefined
                    }
                  />
                </div>
              </div>
              <ByCopy by={token.userId} verb="Issued to" />
              <OnCopyMs
                on={token.expiresOnMs}
                verb="Expires"
                dateFormat={dateFormat}
              />
            </div>
          </div>
          <div className="section">
            <div className="section__title">
              <h3>Audit</h3>
            </div>
            <div className="section__fields">
              <OnCopyMs
                on={token.createTimeMs}
                verb="Issued"
                dateFormat={dateFormat}
              />
              <ByCopy by={token.createUser} verb="Issued by" />
              <OnCopyMs
                on={token.updateTimeMs}
                verb="Updated"
                dateFormat={dateFormat}
              />
              <ByCopy by={token.updateUser} verb="Updated by" />
            </div>
          </div>
          <div className="section">
            <div className="section__title">
              <h3>API key</h3>
            </div>
            <div className="section__fields--copy-only constrained">
              <textarea value={token.data} disabled />
              <CopyToClipboard text={token.data}>
                <Button
                  appearance="contained"
                  action="primary"
                  type="button"
                  icon="copy"
                >
                  Copy key
                </Button>
              </CopyToClipboard>
            </div>
          </div>
        </div>
      )}
    </form>
  );
};

export default EditTokenForm;
