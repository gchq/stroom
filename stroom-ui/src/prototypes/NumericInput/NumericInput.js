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

import React, { Component } from 'react'
import PropTypes from 'prop-types'

import { Input, Button, Icon, Popup } from 'semantic-ui-react'
import 'semantic-ui-css/semantic.min.css'

import './NumericInput.css'

class NumericInput extends Component {
  componentWillMount () {
    if (this.props.value) {
      this.setState({ value: this.props.value })
    }

    this.setState(
      {
        isDownDisabled: false,
        isUpDisabled: false
      },
      () => this.setButtonDisabledStates()
    )
  }

  setButtonDisabledStates = () => {
    if (typeof this.props.max !== 'undefined') {
      if (this.state.value >= this.props.max) {
        this.setState({ isUpDisabled: true })
      } else {
        this.setState({ isUpDisabled: false })
      }
    }

    if (typeof this.props.min !== 'undefined') {
      if (this.state.value <= this.props.min) {
        this.setState({ isDownDisabled: true })
      } else {
        this.setState({ isDownDisabled: false })
      }
    }
  };

  getFallBackValue () {
    let noValueFallback = this.props.defaultValue
      ? this.props.defaultValue
      : this.props.placeholder
    noValueFallback = noValueFallback || 0
    return noValueFallback
  }

  handleUp = (e, upButtonProps) => {
    const newValue =
      (this.state.value ? this.state.value : this.getFallBackValue()) + 1
    this.setState({ value: newValue }, () => this.setButtonDisabledStates())
  };

  handleDown = (e, downButtonProps) => {
    const newValue =
      (this.state.value ? this.state.value : this.getFallBackValue()) - 1
    this.setState({ value: newValue }, () => this.setButtonDisabledStates())
  };

  handleValueChange = (e, inputProps) => {
    let newValue = parseInt(inputProps.value, 10) // 10 = the radix, indicating decimal
    if (isNaN(newValue)) {
      newValue = inputProps.value
    }

    this.setState({ value: newValue }, () => this.setButtonDisabledStates())
  };

  render () {
    const { value, isUpDisabled, isDownDisabled } = this.state
    const { min, max, defaultValue, placeholder } = this.props

    let valueIsBad = value < min || value > max
    let valueIsBadMessage = ''
    if (value < min) {
      valueIsBad = true
      valueIsBadMessage = valueIsBadMessage + 'Minimum value is ' + min
    }
    if (value > max) {
      valueIsBad = true
      valueIsBadMessage = valueIsBadMessage + 'Maximum value is ' + max
    }

    return (
      <div>
        <div className='row'>
          <div className='input-row'>
            <Popup
              trigger={<Input
                error={valueIsBad}
                size='tiny'
                placeholder={placeholder || 0}
                className='numeric-input'
                value={value}
                defaultValue={defaultValue || null}
                onChange={this.handleValueChange}
                action={
                  <div>
                    <Button
                      className='button-top'
                      onClick={this.handleUp}
                      disabled={isUpDisabled}
                    >
                      <Icon name='angle up' />
                    </Button>
                    <Button
                      className='button-bottom'
                      onClick={this.handleDown}
                      disabled={isDownDisabled}
                    >
                      <Icon name='angle down' />
                    </Button>
                  </div>
                }
              />}
              content={valueIsBadMessage}
              open={valueIsBad}
              style={{borderColor: 'red'}}
            />
          </div>
        </div>
      </div>
    )
  }
}

NumericInput.propTypes = {
  min: PropTypes.number,
  max: PropTypes.number,
  placeholder: PropTypes.string,
  defaultValue: PropTypes.number,
  value: PropTypes.number
}

export default NumericInput
