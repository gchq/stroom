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

export {
    testDataSource,
    emptyDataSource
}