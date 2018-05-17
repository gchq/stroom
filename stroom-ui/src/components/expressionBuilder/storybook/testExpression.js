const testExpression = {
    "type": "operator",
    "op": "OR",
    "children": [
        {
            "type": "term",
            "field": "colour",
            "condition": "CONTAINS",
            "value": "red",
            "dictionary": null,
            "enabled": true
        },
        {
            "type": "term",
            "field": "colour",
            "condition": "IN",
            "value": "blue",
            "dictionary": null,
            "enabled": true
        },
        {
            "type": "operator",
            "op": "AND",
            "enabled" : true,
            "children": [
                {
                    "type": "term",
                    "field": "numberOfDoors",
                    "condition": "BETWEEN",
                    "value": "1,5",
                    "dictionary": null,
                    "enabled": true
                },
                {
                    "type": "term",
                    "field": "createUser",
                    "condition": "EQUALS",
                    "value": "me",
                    "dictionary": null,
                    "enabled": true
                }
            ]
        },
        {
            "type": "operator",
            "op": "OR",
            "enabled" : false,
            "children": [
                {
                    "type": "term",
                    "field": "id",
                    "condition": "CONTAINS",
                    "value": "bob",
                    "dictionary": null,
                    "enabled": false
                },
                {
                    "type": "term",
                    "field": "updateTime",
                    "condition": "BETWEEN",
                    "value": "me",
                    "dictionary": null,
                    "enabled": true
                }
            ]
        }
    ],
    "enabled": true
}

const simplestExpression = {
    "uuid" : "root",
    "type" : "operator",
    "op" : "AND",
    "children": [],
    "enabled" : true
};

const testDataSource = {
    "fields": [
        { "type": "ID", "name": "id", "queryable": true, "conditions": ["EQUALS", "IN", "IN_DICTIONARY"] },
        { "type": "FIELD", "name": "colour", "queryable": true, "conditions": ["CONTAINS", "EQUALS", "IN", "IN_DICTIONARY"] },
        { "type": "NUMERIC_FIELD", "name": "numberOfDoors", "queryable": true, "conditions": ["EQUALS", "BETWEEN", "GREATER_THAN", "GREATER_THAN_OR_EQUAL_TO", "LESS_THAN", "LESS_THAN_OR_EQUAL_TO", "IN", "IN_DICTIONARY"] },
        { "type": "FIELD", "name": "createUser", "queryable": true, "conditions": ["EQUALS", "CONTAINS", "IN", "IN_DICTIONARY"] },
        { "type": "DATE_FIELD", "name": "createTime", "queryable": true, "conditions": ["BETWEEN", "EQUALS", "GREATER_THAN", "GREATER_THAN_OR_EQUAL_TO", "LESS_THAN", "LESS_THAN_OR_EQUAL_TO"] },
        { "type": "FIELD", "name": "updateUser", "queryable": true, "conditions": ["EQUALS", "CONTAINS", "IN", "IN_DICTIONARY"] },
        { "type": "DATE_FIELD", "name": "updateTime", "queryable": true, "conditions": ["BETWEEN", "EQUALS", "GREATER_THAN", "GREATER_THAN_OR_EQUAL_TO", "LESS_THAN", "LESS_THAN_OR_EQUAL_TO"] }
    ]
}
const emptyDataSource = { "fields": [] }
const testAndOperator = {
    "type": "operator",
    "op": "AND",
    "children": []
}
const testOrOperator = {
    "type": "operator",
    "op": "AND",
    "children": []
}
const testNotOperator = {
    "type": "operator",
    "op": "AND",
    "children": []
}
export {
    testExpression,
    simplestExpression,
    testDataSource,
    emptyDataSource,
    testAndOperator,
    testOrOperator,
    testNotOperator
}