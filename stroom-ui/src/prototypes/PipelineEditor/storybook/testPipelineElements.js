import { ElementCategories } from './ElementCategories';

export const pipelineElements = {
    elementTypes: [
        {
            type: 'sometype',
            category: ElementCategories.INTERNAL,
            roles: ['role1', 'role2'],
            icon: 'someIcon'
        }
    ],
    propertyTypes: {
        'sometype' : {
            'someprop' : {
                elementType: 'refers back round to the elementType class above...',
                name: 'asdf',
                type: 'asdf',
                description: 'asdf',
                defaultValue: 'asdf',
                pipelineReference : false,
                docRefTypes: ['t1', 't2']
            }
        }
    }
}