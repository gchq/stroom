import { guid } from '../../../lib/treeUtils';

const DOC_REF_TYPES = {
    FOLDER : 'Folder',
    DICTIONARY: 'dictionary',
    HOBBIT: 'hobbit',
    HUMAN: 'human',
    DWARF: 'dwarf',
    ELF: 'elf',
    ORC: 'orc',
    WIZARD: 'wizard',
    MONSTER: 'monster'
}

const frodo = {
    uuid: guid(),
    name: 'Frodo Baggins',
    type: DOC_REF_TYPES.HOBBIT
}

const sam = {
    uuid: guid(),
    name: 'Samwise Gamgee',
    type: DOC_REF_TYPES.HOBBIT
}

const pippin = {
    uuid: guid(),
    name: 'Pippin',
    type: DOC_REF_TYPES.HOBBIT
}

const merrin = {
    uuid: guid(),
    name: 'Merrin',
    type: DOC_REF_TYPES.HOBBIT
}

const aragorn = {
    uuid: guid(),
    name: 'Aragorn',
    type: DOC_REF_TYPES.HUMAN
}

const boromir = {
    uuid: guid(),
    name: 'Boromir',
    type: DOC_REF_TYPES.HUMAN
}

const gandalf = {
    uuid: guid(),
    name: 'Gandalf',
    type: DOC_REF_TYPES.WIZARD
}

const gimli = {
    uuid: guid(),
    name: 'Gimli',
    type: DOC_REF_TYPES.DWARF
}

const legolas = {
    uuid: guid(),
    name: 'Legolas',
    type: DOC_REF_TYPES.ELF
}

const giftsFromGaladriel = {
    uuid: guid(),
    name: 'Gifts from Galadriel',
    type: DOC_REF_TYPES.DICTIONARY
}

const fellowship = {
    uuid: guid(),
    name: 'Fellowship of the Ring',
    type: DOC_REF_TYPES.FOLDER,
    children: [
        frodo, sam, pippin, merrin, gimli, aragorn, boromir, legolas, gandalf, giftsFromGaladriel
    ]
}

const theoden = {
    uuid: guid(),
    name: 'Theoden King',
    type: DOC_REF_TYPES.HUMAN,
}

const eomer = {
    uuid: guid(),
    name: 'Eomer',
    type: DOC_REF_TYPES.HUMAN,
}

const eowyn = {
    uuid: guid(),
    name: 'Eowyn',
    type: DOC_REF_TYPES.HUMAN,
}

const menOfRohan = {
    uuid: guid(),
    name: 'Riders of Rohan',
    type: DOC_REF_TYPES.FOLDER,
    children: [
        theoden,
        eomer,
        eowyn
    ]
}

const goodGuys = {
    uuid: guid(),
    name: 'Free People of Middle Earth',
    type: DOC_REF_TYPES.FOLDER,
    children: [
        fellowship,
        menOfRohan
    ]
}

const sauron = {
    uuid: guid(),
    name: 'Sauron',
    type: DOC_REF_TYPES.WIZARD
}

const saruman = {
    uuid: guid(),
    name: 'Saruman',
    type: DOC_REF_TYPES.WIZARD
}

const gorbag = {
    uuid: guid(),
    name: 'Gorbag',
    type: DOC_REF_TYPES.ORC
}

const grishnak = {
    uuid: guid(),
    name: 'Grishnak',
    type: DOC_REF_TYPES.ORC
}

const saurons_army = {
    uuid: guid(),
    name: 'Saurons Army',
    type: DOC_REF_TYPES.FOLDER,
    children: [
        sauron, saruman, gorbag, grishnak
    ]
}

const shelob = {
    uuid: guid(),
    name: 'Shelob',
    type: DOC_REF_TYPES.MONSTER
}

const golem = {
    uuid: guid(),
    name: 'Golem',
    type: DOC_REF_TYPES.HOBBIT
}

const smaug = {
    uuid: guid(),
    name: 'Smaug',
    type: DOC_REF_TYPES.MONSTER
}

const agents_of_chaos = {
    uuid: guid(),
    name: 'Agents of Chaos',
    type: DOC_REF_TYPES.FOLDER,
    children: [
        shelob,
        golem,
        smaug
    ]
}

const evilTrinkets = {
    uuid: guid(),
    name: 'Evil Trinkets',
    type: DOC_REF_TYPES.DICTIONARY
}

const badGuys = {
    uuid: guid(),
    name: 'Servants of the Dark Tower',
    type: DOC_REF_TYPES.FOLDER,
    children: [
        saurons_army,
        agents_of_chaos,
        evilTrinkets
    ]
}

const favouriteAdjectives = {
    uuid: guid(),
    name: 'Favourite Adjectives',
    type: DOC_REF_TYPES.DICTIONARY
}

const testTree = {
    uuid: guid(),
    name: 'LOTR',
    type: DOC_REF_TYPES.FOLDER,
    children: [
        goodGuys,
        badGuys,
        favouriteAdjectives
    ]
};

export {
    DOC_REF_TYPES,
    sam, 
    gimli,
    fellowship,
    testTree
};