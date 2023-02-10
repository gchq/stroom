/*
 * This file is generated by jOOQ.
 */
package stroom.docstore.fav.impl.db.jooq;


import org.jooq.TableField;
import org.jooq.UniqueKey;
import org.jooq.impl.DSL;
import org.jooq.impl.Internal;

import stroom.docstore.fav.impl.db.jooq.tables.DocFavourite;
import stroom.docstore.fav.impl.db.jooq.tables.records.DocFavouriteRecord;


/**
 * A class modelling foreign key relationships and constraints of tables in
 * stroom.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class Keys {

    // -------------------------------------------------------------------------
    // UNIQUE and PRIMARY KEY definitions
    // -------------------------------------------------------------------------

    public static final UniqueKey<DocFavouriteRecord> KEY_DOC_FAVOURITE_DOC_FAVOURITE_FK_EXPLORER_NODE_TYPE_EXPLORER_NODE_UUID_USER_UUID = Internal.createUniqueKey(DocFavourite.DOC_FAVOURITE, DSL.name("KEY_doc_favourite_doc_favourite_fk_explorer_node_type_explorer_node_uuid_user_uuid"), new TableField[] { DocFavourite.DOC_FAVOURITE.DOC_TYPE, DocFavourite.DOC_FAVOURITE.DOC_UUID, DocFavourite.DOC_FAVOURITE.USER_UUID }, true);
    public static final UniqueKey<DocFavouriteRecord> KEY_DOC_FAVOURITE_PRIMARY = Internal.createUniqueKey(DocFavourite.DOC_FAVOURITE, DSL.name("KEY_doc_favourite_PRIMARY"), new TableField[] { DocFavourite.DOC_FAVOURITE.ID }, true);
}