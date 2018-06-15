/*
 * Copyright 2018 Crown Copyright
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

import React from 'react';
import PropTypes from 'prop-types';

import { Input, Button, Icon, Popup } from 'semantic-ui-react';
import { compose, withState } from 'recompose';
import 'semantic-ui-css/semantic.min.css';

import './NumericInput.css';

const enhance = compose(
  withState('isDownDisabled', 'setDownDisabled', false),
  withState('isUpDisabled', 'setUpDisabled', false)
);

const getFallBackValue = (defaultValue, placeholder) => {
  let noValueFallback = defaultValue || placeholder;
  noValueFallback = noValueFallback || 0;
  return noValueFallback;
};

const setButtonDisabledStates = (max, min, value, setUpDisabled, setDownDisabled) => {
  if (typeof max !== 'undefined') {
    if (value >= max) {
      setUpDisabled(true);
    } else {
      setUpDisabled(false);
    }
  }

  if (typeof min !== 'undefined') {
    if (value <= min) {
      setDownDisabled(true);
    } else {
      setDownDisabled(false);
    }
  }
};

const NumericInput = enhance(({
  min,
  max,
  defaultValue,
  placeholder,
  value,
  isUpDisabled,
  isDownDisabled,
  setUpDisabled,
  setDownDisabled,
  setValue,
  onChange,
}) => {
  let valueIsBad = false;
  let valueIsBadMessage = '';
  if (value < min) {
    valueIsBad = true;
    valueIsBadMessage = `${valueIsBadMessage}Minimum value is ${min}`;
  }
  if (value > max) {
    valueIsBad = true;
    valueIsBadMessage = `${valueIsBadMessage}Maximum value is ${max}`;
  }

  return (
    <div>
      <div className="row">
        <div className="input-row">
          <Popup
            trigger={
              <Input
                error={valueIsBad}
                size="tiny"
                placeholder={placeholder || 0}
                className="numeric-input"
                value={value}
                  // defaultValue={defaultValue || null}
                onChange={(e, props) => {
                    let newValue = parseInt(props.value, 10); // 10 = the radix, indicating decimal
                    if (isNaN(newValue)) {
                      newValue = props.value;
                    }
                    setValue(newValue)
                    setButtonDisabledStates(max, min, newValue, setUpDisabled, setDownDisabled);
                    onChange(newValue)
                  }}
                action={
                  <div>
                      <Button
                        className="button-top"
                        onClick={(e, props) => {
                          const newValue = parseInt(value || getFallBackValue(defaultValue, placeholder), 10) + 1; // 10 = the radix, indicating decimal
                          setButtonDisabledStates(max, min, newValue, setUpDisabled, setDownDisabled);
                          onChange(newValue)
                        }}
                        disabled={isUpDisabled}
                      >
                        <Icon name="angle up" />
                      </Button>
                      <Button
                        className="button-bottom"
                        onClick={(e, props) => {
                          const newValue = parseInt(value || getFallBackValue(defaultValue, placeholder), 10) - 1; // 10 = the radix, indicating decimal
                          setButtonDisabledStates(max, min, newValue, setUpDisabled, setDownDisabled);
                          onChange(newValue)
                        }}
                        disabled={isDownDisabled}
                      >
                        <Icon name="angle down" />
                      </Button>
                    </div>
                  }
              />
              }
            content={valueIsBadMessage}
            open={valueIsBad}
            style={{ borderColor: 'red' }}
          />
        </div>
      </div>
    </div>
  );
});

NumericInput.propTypes = {
  min: PropTypes.number,
  max: PropTypes.number,
  placeholder: PropTypes.string,
  defaultValue: PropTypes.number,
  value: PropTypes.number
};

export default NumericInput;