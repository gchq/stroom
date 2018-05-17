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
import React, { Component } from 'react';
import PropTypes from 'prop-types'

import { connect } from 'react-redux'

import {
    Input,
    Button,
    Icon,
    Dropdown
} from 'semantic-ui-react';

import { DragSource } from 'react-dnd';

import { ItemTypes } from './dragDropTypes';
import { displayValues } from './conditions';
import {
    DocRefDropdownPicker,
    DocRefModalPicker
} from '../docExplorer';


import {
    expressionItemUpdated,
    expressionItemDeleted
} from './redux/expressionReducer';

const dragSource = {
    beginDrag(props) {
        return {
            ...props.term
        };
    }
};

function dragCollect(connect, monitor) {
    return {
        connectDragSource: connect.dragSource(),
        isDragging: monitor.isDragging()
    }
}

class ExpressionTerm extends Component {
    static propTypes = {
        // Props
        dataSource: PropTypes.object.isRequired, // complete definition of the data source
        expressionId : PropTypes.string.isRequired, // the ID of the overall expression
        term : PropTypes.object.isRequired, // the operator that this particular element is to represent
        isEnabled: PropTypes.bool.isRequired, // a combination of any parent enabled state, and its own

        // Actions
        expressionItemUpdated : PropTypes.func.isRequired,
        expressionItemDeleted : PropTypes.func.isRequired,

        // React DnD
        connectDragSource: PropTypes.func.isRequired,
        isDragging: PropTypes.bool.isRequired
    }

    onDeleteTerm() {
        this.props.expressionItemDeleted(this.props.expressionId, this.props.term.uuid);
    }

    onEnabledChange() {
        this.onTermUpdated({
            enabled: !this.props.term.enabled
        });
    }

    onTermUpdated(updates) {
        this.props.expressionItemUpdated(this.props.expressionId, this.props.term.uuid, updates);
    }

    onFieldChange(event, data) {
        this.onTermUpdated({
            field: data.value
        });
    }

    onConditionChange(event, data) {
        this.onTermUpdated({
            condition: data.value
        })
    }

    onFromValueChange(event, data) {
        let parts = this.props.term.value.split(',');
        let existingToValue = (parts.length == 2) ? parts[1] : undefined;
        let newValue = data.value + ',' + existingToValue;

        this.onTermUpdated({
            value: newValue
        })
    }

    onToValueChange(event, data) {
        let parts = this.props.term.value.split(',');
        let existingFromValue = (parts.length == 2) ? parts[0] : undefined;
        let newValue = existingFromValue + ',' + data.value;

        this.onTermUpdated({
            value: newValue
        })
    }

    onSingleValueChange(event, data) {
        this.onTermUpdated({
            value: data.value
        })
    }

    onMultipleValueChange(event, data) {
        this.onTermUpdated({
            value: data.value.join()
        })
    }

    onDictionaryChange(docRef) {
        this.onTermUpdated({
            value: undefined,
            dictionary : docRef
        })
    }
    
    render() {
        const { connectDragSource, isDragging } = this.props;

        let className = 'expression-item';
        if (!this.props.isEnabled) {
            className += ' expression-item--disabled';
        }

        let enabledButton;
        if (this.props.term.enabled) {
            enabledButton = <Button 
                                compact 
                                color='blue'
                                onClick={this.onEnabledChange.bind(this)}
                                >Enabled 
                            </Button>
        } else {
            enabledButton = <Button 
                                compact
                                basic
                                onClick={this.onEnabledChange.bind(this)}
                                >Disabled 
                            </Button>
        }

        let fieldOptions = this.props.dataSource.fields.map(f => {
            return {
                value: f.name,
                text: f.name
            }
        })

        let field = this.props.dataSource.fields.filter(f => f.name === this.props.term.field)[0];
        let conditionOptions = [];
        let valueType='text';
        if (!!field) {
            conditionOptions = field.conditions.map(c => {
                return {
                    value: c,
                    text: displayValues[c]
                }
            })

            switch(field.type) {
                case 'FIELD':
                case 'ID':
                    valueType='text';
                    break;
                case 'NUMERIC_FIELD':
                    valueType='number';
                    break;
                case 'DATE_FIELD':
                    valueType='datetime-local';
                    break;
            }
        }

        let valueWidget;
        switch (this.props.term.condition) {
            case 'CONTAINS':
            case 'EQUALS':
            case 'GREATER_THAN':
            case 'GREATER_THAN_OR_EQUAL_TO':
            case 'LESS_THAN':
            case 'LESS_THAN_OR_EQUAL_TO': {
                valueWidget = <Input 
                                placeholder='value' 
                                type={valueType}
                                value={this.props.term.value} 
                                onChange={this.onSingleValueChange.bind(this)}
                                />;// some single selection
                break;
            }
            case 'BETWEEN': {
                let splitValues = this.props.term.value.split(',');
                let fromValue = (splitValues.length == 2) ? splitValues[0] : undefined;
                let toValue = (splitValues.length == 2) ? splitValues[1] : undefined;
                valueWidget = (
                    <span>
                        <Input 
                            placeholder='from'  
                            type={valueType}
                            value={fromValue} 
                            onChange={this.onFromValueChange.bind(this)}
                            />
                        <span className='input-between__divider'>to</span>
                        <Input 
                            placeholder='to'  
                            type={valueType}
                            value={toValue} 
                            onChange={this.onToValueChange.bind(this)}
                            />
                    </span>
                );// some between selection
                break;
            }
            case 'IN': {
                let hasValues = (!!this.props.term.value && this.props.term.value.length > 0)
                let splitValues = hasValues ? this.props.term.value.split(',') : [];
                let keyedValues = hasValues ? splitValues.map(s => {
                    return {key: s, value: s, text: s}
                }) : []
                valueWidget = <Dropdown
                                    options={keyedValues}
                                    multiple
                                    value={splitValues}
                                    placeholder={'type multiple values'}
                                    search={(options, query) => [{key:query, value:query, text:query}]}
                                    selection
                                    onChange={this.onMultipleValueChange.bind(this)}
                                />
                break;
            }
            case 'IN_DICTIONARY': {
                let dictUuid = '';
                if (this.props.term.dictionary) {
                    dictUuid = this.props.term.dictionary.uuid;
                }
                valueWidget = <DocRefModalPicker 
                                pickerId={this.props.expressionId + ' -' + this.props.term.uuid}
                                value={this.props.term.dictionary} 
                                onChange={this.onDictionaryChange.bind(this)}
                                typeFilter='dictionary'
                                />;
                break;
            }
        }

        return connectDragSource(    
            <div className={className}>
                <span><Icon color='grey' name='list layout'/></span>
                <Dropdown placeholder='field' 
                            selection 
                            options={fieldOptions} 
                            onChange={this.onFieldChange.bind(this)}
                            value={this.props.term.field}
                            />
                <Dropdown placeholder='condition' 
                            selection
                            options={conditionOptions} 
                            onChange={this.onConditionChange.bind(this)}
                            value={this.props.term.condition}
                            />
                {valueWidget}
                <Button.Group floated='right'>
                    {enabledButton}
                    <Button 
                        compact
                        icon='trash'
                        onClick={this.onDeleteTerm.bind(this)}
                        />
                </Button.Group>
            </div>
        )
    }
}

export default connect(
    (state) => ({
        // terms are nested, so take all their props from parent
    }),
    {
        expressionItemUpdated,
        expressionItemDeleted
    }
)
    (DragSource(ItemTypes.TERM, dragSource, dragCollect)(ExpressionTerm));