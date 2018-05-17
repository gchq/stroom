import { 
    guid, 
    findMatch,
    canMove
} from './treeUtils';

import expect from 'expect.js';

const oneOne = {
    uuid: '1-1',
    type: 'file',
    name: 'myFirstFirst'
};

const oneTwo = {
    uuid: '1-2',
    type: 'file',
    name: 'myFirstSecond'
};

const oneThreeOne = {
    uuid: '1-3-1',
    type: 'file',
    name: 'myFirstThirdFirst'
};

const oneThreeTwo = {
    uuid: '1-3-2',
    type: 'file',
    name: 'myFirstThirdSecond'
};

const oneThree = {
    uuid: '1-3',
    type: 'folder',
    name: 'myFirstThird',
    children: [
        oneThreeOne,
        oneThreeTwo,
    ]
};

const oneFourOne = {
    uuid: '1-4-1',
    type: 'file',
    name: 'myFirstFourthFirst'
};

const oneFourTwo = {
    uuid: '1-4-2',
    type: 'file',
    name: 'myFirstFourthSecond'
};

const oneFour = {
    uuid: '1-4',
    type: 'folder',
    name: 'myFirstFourth',
    children: [
        oneFourOne,
        oneFourTwo,
    ]
};

const oneFiveOneOne = {
    uuid: '1-5-1-1',
    type: 'file',
    name: 'myFirstFifthFirstFirst',
}

const oneFiveOne = {
    uuid: '1-5-1',
    type: 'folder',
    name: 'myFirstFifthFirst',
    children: [
        oneFiveOneOne
    ]
}

const oneFive = {
    uuid: '1-5',
    type: 'folder',
    name: 'myFirstFifth',
    children: [
        oneFiveOne
    ]
}

const testTree = {
    uuid: '1',
    type: 'folder',
    name: 'root',
    children: [
        oneOne,
        oneTwo,
        oneThree,
        oneFour,
        oneFive
    ]
}

describe('Tree Utils', () => {
    describe('#guid()', () => {
        it('should create distinct values 1000 times', () => {
            const numberValues = 1000;
            const set1 = new Set([]);

            for (let x=0; x<numberValues; x++) {
                set1.add(guid());
            }

            expect(set1.size).to.be(numberValues);
        })
    })
    describe('#findMatch()', () => {
        it('should find a match when is root', () => {
            let found = findMatch(testTree, testTree);
            expect(found).to.be(true);
        });
        it('should find a match when present within children', () => {
            let found = findMatch(testTree, oneTwo);
            expect(found).to.be(true);
        });
        it('should find a match when present within grand-children', () => {
            let found = findMatch(testTree, oneThreeOne);
            expect(found).to.be(true);
        });
        it('should not find a match when missing', () => {
            let found = findMatch(testTree, {uuid:'fifty'});
            expect(found).to.be(false);
        })
    });
    describe('#canMove()', () => {
        it('should allow moving files to other directories', () => {
            let allowed = canMove(oneFourTwo, oneThree);
            expect(allowed).to.be(true);
        });
        it('should prevent moving file into folder its already in', () => {
            let allowed = canMove(oneFourTwo, oneFour);
            expect(allowed).to.be(false);
        });
        it('should allow moving folder to other directories not inside self', () => {
            let allowed = canMove(oneFour, oneThree);
            expect(allowed).to.be(true);
        });
        it('should prevent moving folder into one of its own children', () => {
            let allowed = canMove(oneFive, oneFiveOne)
            expect(allowed).to.be(false);
        });
        it('should prevent moving folder into one of its own grand-children', () => {
            let allowed = canMove(testTree, oneFiveOne)
            expect(allowed).to.be(false);
        });
        it('should prevent moving folder into itself', () => {
            let allowed = canMove(oneFive, oneFive)
            expect(allowed).to.be(false);
        });
    })
});