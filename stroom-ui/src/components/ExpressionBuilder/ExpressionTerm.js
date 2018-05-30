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

import { compose } from 'redux';
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
    DocRefModalPicker,
    docRefPicked
} from '../DocExplorer';

import {
    expressionItemUpdated,
    requestExpressionItemDelete,
    joinDictionaryTermId
} from './redux';

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

/**
 * Higher Order Component to cope with sync'ing the pickedDocRef state with the dictionary in our expression.
 * 
 * Having this HOC allows the Expression Term component to be stateless functional
 * @param {Component} WrappedComponent 
 */
let withPickedDocRef = () => {
    return (WrappedComponent) => {
        let WithPickedDocRef = class extends Component {
            static propTypes = {
                docRefPicked : PropTypes.func.isRequired,
                pickedDocRefs : PropTypes.object.isRequired, // picked dictionary
                expressionId : PropTypes.string.isRequired, // the ID of the overall expression
                term : PropTypes.object.isRequired, // the operator that this particular element is to represent
            }

            componentDidMount() {
                this.checkDictionary();
            }

            componentDidUpdate(prevProps, prevState, snapshot) {
                this.checkDictionary();
            }

            /**
             * This functions is used to check that the picked doc ref matches the one from the expression.
             * If it doesn't then it triggers a docRefPicked action to ensure that it then does match.
             * From that point on the expressions and pickedDocRefs should both be monitoring the same state.
             */
            checkDictionary() {
                let {
                    expressionId,
                    term,
                    pickedDocRefs,
                    docRefPicked
                } = this.props;
                let pickerId = joinDictionaryTermId(expressionId, term.uuid);

                if (term.condition === 'IN_DICTIONARY') {
                    // Get the current state of the picked doc refs
                    let picked = pickedDocRefs[pickerId];

                    // If the dictionary is set on the term, but not set or equal to the 'picked' one, update it
                    if (!!term.dictionary) {
                        if (!picked || picked.uuid !== term.dictionary.uuid) {
                            docRefPicked(pickerId, term.dictionary);
                        }
                    }
                }
            }

            render() {
                return <WrappedComponent {...this.props} />
            }
        }

        return connect(
            (state) => ({
                // terms are nested, so take all their props from parent
                pickedDocRefs : state.explorerTree.pickedDocRefs
            }),
            {
                docRefPicked
            }
        )(WithPickedDocRef)
    }
}

const ExpressionTerm = (props) => {
    
    const {
        connectDragSource,
        isDragging,
        term,
        isEnabled,
        dataSource,
        expressionId,

        requestExpressionItemDelete,
        expressionItemUpdated
    } = props;
    let pickerId = joinDictionaryTermId(expressionId, term.uuid);

    let onTermUpdated = (updates) => {
        expressionItemUpdated(expressionId, term.uuid, updates);
    }

    let onDeleteTerm = () => {
        requestExpressionItemDelete(expressionId, term.uuid);
    }

    let onEnabledChange = () =>{
        onTermUpdated({
            enabled: !term.enabled
        });
    }

    let onFieldChange = (event, data) => {
        onTermUpdated({
            field: data.value
        });
    }

    let onConditionChange = (event, data) => {
        onTermUpdated({
            condition: data.value
        })
    }

    let onFromValueChange = (event, data) => {
        let parts = term.value.split(',');
        let existingToValue = (parts.length == 2) ? parts[1] : undefined;
        let newValue = data.value + ',' + existingToValue;

        onTermUpdated({
            value: newValue
        })
    }

    let onToValueChange = (event, data) => {
        let parts = term.value.split(',');
        let existingFromValue = (parts.length == 2) ? parts[0] : undefined;
        let newValue = existingFromValue + ',' + data.value;

        onTermUpdated({
            value: newValue
        })
    }

    let onSingleValueChange = (event, data) => {
        onTermUpdated({
            value: data.value
        })
    }

    let onMultipleValueChange = (event, data) => {
        onTermUpdated({
            value: data.value.join()
        })
    }

    let className = 'expression-item';
    if (!isEnabled) {
        className += ' expression-item--disabled';
    }

    let enabledButton;
    if (term.enabled) {
        enabledButton = <Button 
                            icon='checkmark'
                            compact 
                            color='blue'
                            onClick={onEnabledChange}
                            />
    } else {
        enabledButton = <Button 
                            icon='checkmark'
                            compact
                            basic
                            onClick={onEnabledChange}
                            />
    }

    let fieldOptions = dataSource.fields.map(f => {
        return {
            value: f.name,
            text: f.name
        }
    })

    let field = dataSource.fields.filter(f => f.name === term.field)[0];
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
    switch (term.condition) {
        case 'CONTAINS':
        case 'EQUALS':
        case 'GREATER_THAN':
        case 'GREATER_THAN_OR_EQUAL_TO':
        case 'LESS_THAN':
        case 'LESS_THAN_OR_EQUAL_TO': {
            valueWidget = <Input 
                            placeholder='value' 
                            type={valueType}
                            value={term.value || ''} 
                            onChange={onSingleValueChange.bind(this)}
                            />;// some single selection
            break;
        }
        case 'BETWEEN': {
            let splitValues = term.value.split(',');
            let fromValue = (splitValues.length == 2) ? splitValues[0] : undefined;
            let toValue = (splitValues.length == 2) ? splitValues[1] : undefined;
            valueWidget = (
                <span>
                    <Input 
                        placeholder='from'  
                        type={valueType}
                        value={fromValue} 
                        onChange={onFromValueChange.bind(this)}
                        />
                    <span className='input-between__divider'>to</span>
                    <Input 
                        placeholder='to'  
                        type={valueType}
                        value={toValue} 
                        onChange={onToValueChange.bind(this)}
                        />
                </span>
            );// some between selection
            break;
        }
        case 'IN': {
            let hasValues = (!!term.value && term.value.length > 0)
            let splitValues = hasValues ? term.value.split(',') : [];
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
                                onChange={onMultipleValueChange.bind(this)}
                            />
            break;
        }
        case 'IN_DICTIONARY': {
            let dictUuid = '';
            if (term.dictionary) {
                dictUuid = term.dictionary.uuid;
            }
            valueWidget = <DocRefModalPicker 
                            pickerId={pickerId}
                            typeFilter='dictionary'
                            />;
            break;
        }
    }

    return connectDragSource(    
        <div className={className}>
            <span><Icon color='grey' name='bars'/></span>
            <Dropdown placeholder='field' 
                        selection 
                        options={fieldOptions} 
                        onChange={onFieldChange.bind(this)}
                        value={term.field}
                        />
            <Dropdown placeholder='condition' 
                        selection
                        options={conditionOptions} 
                        onChange={onConditionChange.bind(this)}
                        value={term.condition}
                        />
            {valueWidget}
            <Button.Group floated='right'>
                {enabledButton}
                <Button 
                    compact
                    icon='trash'
                    onClick={onDeleteTerm.bind(this)}
                    />
            </Button.Group>
        </div>
    )
}
ExpressionTerm.propTypes = {
    // Props
    dataSource: PropTypes.object.isRequired, // complete definition of the data source
    expressionId : PropTypes.string.isRequired, // the ID of the overall expression
    term : PropTypes.object.isRequired, // the operator that this particular element is to represent
    isEnabled: PropTypes.bool.isRequired, // a combination of any parent enabled state, and its own

    // Actions
    expressionItemUpdated : PropTypes.func.isRequired,
    requestExpressionItemDelete : PropTypes.func.isRequired,

    // React DnD
    connectDragSource: PropTypes.func.isRequired,
    isDragging: PropTypes.bool.isRequired
}

export default compose(
    connect(
        (state) => ({
            // state
        }),
        {
            expressionItemUpdated,
            requestExpressionItemDelete
        }
    ),
    DragSource(ItemTypes.TERM, dragSource, dragCollect),
    withPickedDocRef()
)(ExpressionTerm);