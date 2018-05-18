/**
 * This function takes the denormalised pipeline definition and creates
 * a tree like structure. The root element is the 'from' of the first link.
 * This tree structure can then be used to lay the elements out in the graphical display.
 * 
 * @param {object} pipeline 
 * @return {object} Tree like structure
 */
export function getPipelineAsTree(pipeline) {
    let elements = {};

    // Put all the elements into an object, keyed on id
    pipeline.elements.add.element.forEach(e => {
        elements[e.id] = {
            id : e.id,
            type : e.type,
            to: []
        };
    })

    // Create the tree using links
    pipeline.links.add.link.forEach(l => {
        elements[l.from].to.push(elements[l.to]);
    });

    // Figure out the root
    let rootId = pipeline.links.add.link[0].from;

    return elements[rootId];
}