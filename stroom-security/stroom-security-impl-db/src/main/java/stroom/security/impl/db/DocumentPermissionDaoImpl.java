package stroom.security.impl.db;

import stroom.db.util.JooqUtil;
import stroom.docref.DocRef;
import stroom.explorer.shared.ExplorerConstants;
import stroom.security.impl.DocTypeIdDao;
import stroom.security.impl.DocumentPermissionDao;
import stroom.security.impl.UserDocumentPermissions;
import stroom.security.impl.db.jooq.tables.PermissionDoc;
import stroom.security.impl.db.jooq.tables.PermissionDocCreate;
import stroom.security.impl.db.jooq.tables.StroomUser;
import stroom.security.impl.db.jooq.tables.StroomUserGroup;
import stroom.security.shared.DocumentPermission;
import stroom.security.shared.DocumentUserPermissions;
import stroom.security.shared.FetchDocumentUserPermissionsRequest;
import stroom.util.NullSafe;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserRef;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import org.jooq.CommonTableExpression;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Name;
import org.jooq.OrderField;
import org.jooq.Record;
import org.jooq.Select;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.types.UByte;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static stroom.security.impl.db.jooq.tables.PermissionDoc.PERMISSION_DOC;
import static stroom.security.impl.db.jooq.tables.PermissionDocCreate.PERMISSION_DOC_CREATE;
import static stroom.security.impl.db.jooq.tables.StroomUser.STROOM_USER;
import static stroom.security.impl.db.jooq.tables.StroomUserGroup.STROOM_USER_GROUP;

public class DocumentPermissionDaoImpl implements DocumentPermissionDao {

    private static final BitSet EMPTY = new BitSet(0);
    private static final PermissionDoc PERMISSION_DOC_SOURCE = new PermissionDoc("pd_source");
    private static final PermissionDocCreate PERMISSION_DOC_CREATE_SOURCE = new PermissionDocCreate("pdc_source");

    private final SecurityDbConnProvider securityDbConnProvider;
    private final DocTypeIdDao docTypeIdDao;
    private final Provider<UserDaoImpl> userDaoProvider;

    @Inject
    public DocumentPermissionDaoImpl(final SecurityDbConnProvider securityDbConnProvider,
                                     final DocTypeIdDao docTypeIdDao,
                                     final Provider<UserDaoImpl> userDaoProvider) {
        this.securityDbConnProvider = securityDbConnProvider;
        this.docTypeIdDao = docTypeIdDao;
        this.userDaoProvider = userDaoProvider;
    }

    @Override
    public UserDocumentPermissions getPermissionsForUser(final String userUuid) {
        Objects.requireNonNull(userUuid, "Null user UUID");

        final Map<String, Byte> permissionMap = new ConcurrentHashMap<>();
        JooqUtil.contextResult(securityDbConnProvider, context -> context
                        .select(PERMISSION_DOC.DOC_UUID, PERMISSION_DOC.PERMISSION_ID)
                        .from(PERMISSION_DOC)
                        .join(STROOM_USER).on(STROOM_USER.UUID.eq(PERMISSION_DOC.USER_UUID))
                        .where(PERMISSION_DOC.USER_UUID.eq(userUuid))
                        .and(STROOM_USER.ENABLED.eq(true))
                        .fetch())
                .forEach(r -> {
                    final String docUuid = r.get(PERMISSION_DOC.DOC_UUID);
                    final byte permission = r.get(PERMISSION_DOC.PERMISSION_ID).byteValue();
                    permissionMap.put(docUuid, permission);
                });
        return new UserDocumentPermissions(permissionMap);
    }

    @Override
    public DocumentPermission getDocumentUserPermission(final String documentUuid, final String userUuid) {
        Objects.requireNonNull(documentUuid, "Null document UUID");
        Objects.requireNonNull(userUuid, "Null user UUID");

        return JooqUtil.contextResult(securityDbConnProvider, context -> context
                        .select(PERMISSION_DOC.PERMISSION_ID)
                        .from(PERMISSION_DOC)
                        .join(STROOM_USER).on(STROOM_USER.UUID.eq(PERMISSION_DOC.USER_UUID))
                        .where(PERMISSION_DOC.DOC_UUID.eq(documentUuid))
                        .and(PERMISSION_DOC.USER_UUID.eq(userUuid))
                        .and(STROOM_USER.ENABLED.eq(true))
                        .fetchOptional(PERMISSION_DOC.PERMISSION_ID))
                .map(r -> DocumentPermission.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(r.byteValue()))
                .orElse(null);
    }

    @Override
    public void setDocumentUserPermission(final String documentUuid,
                                          final String userUuid,
                                          final DocumentPermission permission) {
        Objects.requireNonNull(documentUuid, "Null document UUID");
        Objects.requireNonNull(userUuid, "Null user UUID");
        Objects.requireNonNull(permission, "Null permission");

        final UByte permissionId = UByte.valueOf(permission.getPrimitiveValue());
        JooqUtil.context(securityDbConnProvider, context -> context
                .insertInto(PERMISSION_DOC)
                .columns(PERMISSION_DOC.DOC_UUID,
                        PERMISSION_DOC.USER_UUID,
                        PERMISSION_DOC.PERMISSION_ID)
                .values(documentUuid, userUuid, permissionId)
                .onDuplicateKeyUpdate()
                .set(PERMISSION_DOC.PERMISSION_ID, permissionId)
                .execute());
    }

    @Override
    public void removeDocumentUserPermission(final String documentUuid, final String userUuid) {
        Objects.requireNonNull(documentUuid, "Null document UUID");
        Objects.requireNonNull(userUuid, "Null user UUID");

        JooqUtil.context(securityDbConnProvider, context -> context
                .deleteFrom(PERMISSION_DOC)
                .where(PERMISSION_DOC.DOC_UUID.eq(documentUuid))
                .and(PERMISSION_DOC.USER_UUID.eq(userUuid))
                .execute());
    }

    @Override
    public void removeAllDocumentPermissions(final String documentUuid) {
        Objects.requireNonNull(documentUuid, "Null document UUID");

        JooqUtil.context(securityDbConnProvider, context -> context
                .deleteFrom(PERMISSION_DOC)
                .where(PERMISSION_DOC.DOC_UUID.eq(documentUuid))
                .execute());
    }

    @Override
    public BitSet getDocumentUserCreatePermissionsBitSet(final String documentUuid, final String userUuid) {
        final List<Integer> docTypeIdList = getDocumentUserCreateDocTypeIds(documentUuid, userUuid);

        if (docTypeIdList.isEmpty()) {
            return EMPTY;
        }

        final int max = docTypeIdList.stream().mapToInt(v -> v).max().orElseThrow();
        // Note that the bit set is created after we fetch the permissions just to be sure that the max id is correct.
        final BitSet bitSet = new BitSet(max);
        docTypeIdList.forEach(i -> bitSet.set(i, true));
        return bitSet;
    }

    @Override
    public Set<String> getDocumentUserCreatePermissions(final String documentUuid, final String userUuid) {
        final List<Integer> docTypeIdList = getDocumentUserCreateDocTypeIds(documentUuid, userUuid);
        if (docTypeIdList.isEmpty()) {
            return Collections.emptySet();
        }
        return docTypeIdList
                .stream()
                .map(docTypeIdDao::get)
                .collect(Collectors.toSet());
    }

    private List<Integer> getDocumentUserCreateDocTypeIds(final String documentUuid, final String userUuid) {
        Objects.requireNonNull(documentUuid, "Null document UUID");
        Objects.requireNonNull(userUuid, "Null user UUID");

        return JooqUtil.contextResult(securityDbConnProvider, context -> context
                        .select(PERMISSION_DOC_CREATE.DOC_TYPE_ID)
                        .from(PERMISSION_DOC_CREATE)
                        .join(STROOM_USER).on(STROOM_USER.UUID.eq(PERMISSION_DOC_CREATE.USER_UUID))
                        .where(PERMISSION_DOC_CREATE.DOC_UUID.eq(documentUuid))
                        .and(PERMISSION_DOC_CREATE.USER_UUID.eq(userUuid))
                        .and(STROOM_USER.ENABLED.eq(true))
                        .fetch())
                .map(r -> r.get(PERMISSION_DOC_CREATE.DOC_TYPE_ID).intValue());
    }

    @Override
    public void addDocumentUserCreatePermission(final String documentUuid,
                                                final String userUuid,
                                                final String documentType) {
        Objects.requireNonNull(documentUuid, "Null document UUID");
        Objects.requireNonNull(userUuid, "Null user UUID");
        Objects.requireNonNull(documentType, "Null document type");
        final UByte docTypeId = UByte.valueOf(docTypeIdDao.getOrCreateId(documentType));
        JooqUtil.context(securityDbConnProvider, context ->
                addDocumentUserCreatePermission(context, documentUuid, userUuid, docTypeId));
    }

    private void addDocumentUserCreatePermission(final DSLContext context,
                                                 final String documentUuid,
                                                 final String userUuid,
                                                 final UByte docTypeId) {
        context
                .insertInto(PERMISSION_DOC_CREATE)
                .columns(PERMISSION_DOC_CREATE.DOC_UUID,
                        PERMISSION_DOC_CREATE.USER_UUID,
                        PERMISSION_DOC_CREATE.DOC_TYPE_ID)
                .values(documentUuid, userUuid, docTypeId)
                .onDuplicateKeyUpdate()
                .set(PERMISSION_DOC_CREATE.DOC_TYPE_ID, docTypeId)
                .execute();
    }

    @Override
    public void removeDocumentUserCreatePermission(final String documentUuid,
                                                   final String userUuid,
                                                   final String documentType) {
        Objects.requireNonNull(documentUuid, "Null document UUID");
        Objects.requireNonNull(userUuid, "Null user UUID");
        Objects.requireNonNull(documentType, "Null document type");

        final UByte docTypeId = UByte.valueOf(docTypeIdDao.getOrCreateId(documentType));
        JooqUtil.context(securityDbConnProvider, context -> context
                .deleteFrom(PERMISSION_DOC_CREATE)
                .where(PERMISSION_DOC_CREATE.DOC_UUID.eq(documentUuid))
                .and(PERMISSION_DOC_CREATE.USER_UUID.eq(userUuid))
                .and(PERMISSION_DOC_CREATE.DOC_TYPE_ID.eq(docTypeId))
                .execute());
    }

    @Override
    public void setDocumentUserCreatePermissions(final String documentUuid,
                                                 final String userUuid,
                                                 final Set<String> documentTypes) {
        Objects.requireNonNull(documentUuid, "Null document UUID");
        Objects.requireNonNull(userUuid, "Null user UUID");

        final Set<UByte> docTypeIds = documentTypes
                .stream()
                .map(documentType -> UByte.valueOf(docTypeIdDao.getOrCreateId(documentType)))
                .collect(Collectors.toSet());
        JooqUtil.transaction(securityDbConnProvider, context -> {
            // Delete all permissions.
            removeAllDocumentUserCreatePermissions(context, documentUuid, userUuid);
            // Add new permissions.
            for (final UByte docTypeId : docTypeIds) {
                addDocumentUserCreatePermission(context, documentUuid, userUuid, docTypeId);
            }
        });
    }

    @Override
    public void removeAllDocumentUserCreatePermissions(final String documentUuid, final String userUuid) {
        Objects.requireNonNull(documentUuid, "Null document UUID");
        Objects.requireNonNull(userUuid, "Null user UUID");

        JooqUtil.context(securityDbConnProvider, context ->
                removeAllDocumentUserCreatePermissions(context, documentUuid, userUuid));
    }

    private void removeAllDocumentUserCreatePermissions(final DSLContext context,
                                                        final String documentUuid,
                                                        final String userUuid) {
        context
                .deleteFrom(PERMISSION_DOC_CREATE)
                .where(PERMISSION_DOC_CREATE.DOC_UUID.eq(documentUuid))
                .and(PERMISSION_DOC_CREATE.USER_UUID.eq(userUuid))
                .execute();
    }

    @Override
    public void removeAllDocumentCreatePermissions(final String documentUuid) {
        Objects.requireNonNull(documentUuid, "Null document UUID");

        JooqUtil.context(securityDbConnProvider, context -> context
                .deleteFrom(PERMISSION_DOC_CREATE)
                .where(PERMISSION_DOC_CREATE.DOC_UUID.eq(documentUuid))
                .execute());
    }

    @Override
    public void addDocumentPermissions(final String sourceDocUuid, final String destDocUuid) {
        Objects.requireNonNull(sourceDocUuid, "Null source document UUID");
        Objects.requireNonNull(destDocUuid, "Null destination document UUID");

        // Add permissions.
        JooqUtil.context(securityDbConnProvider, context ->
                addDocumentPermissions(context, sourceDocUuid, destDocUuid));
    }

    @Override
    public void setDocumentPermissions(final String sourceDocUuid, final String destDocUuid) {
        Objects.requireNonNull(sourceDocUuid, "Null source document UUID");
        Objects.requireNonNull(destDocUuid, "Null destination document UUID");

        JooqUtil.transaction(securityDbConnProvider, context -> {
            // Clear existing permssions.
            clearDocumentPermissions(context, destDocUuid);
            // Add new permissions.
            addDocumentPermissions(context, sourceDocUuid, destDocUuid);
        });
    }

    private void clearDocumentPermissions(final DSLContext context,
                                          final String docUuid) {
        // Clear permissions.
        context
                .deleteFrom(PERMISSION_DOC)
                .where(PERMISSION_DOC.DOC_UUID.eq(docUuid))
                .execute();
    }

    private void addDocumentPermissions(final DSLContext context,
                                        final String sourceDocUuid,
                                        final String destDocUuid) {
        // Add permissions.
        context
                .insertInto(PERMISSION_DOC)
                .columns(PERMISSION_DOC.DOC_UUID,
                        PERMISSION_DOC.USER_UUID,
                        PERMISSION_DOC.PERMISSION_ID)
                .select(context
                        .select(DSL.val(destDocUuid),
                                PERMISSION_DOC_SOURCE.USER_UUID,
                                PERMISSION_DOC_SOURCE.PERMISSION_ID)
                        .from(PERMISSION_DOC_SOURCE)
                        .where(PERMISSION_DOC_SOURCE.DOC_UUID.eq(sourceDocUuid)))
                .onDuplicateKeyUpdate()
                .set(PERMISSION_DOC.PERMISSION_ID, PERMISSION_DOC.PERMISSION_ID)
                .execute();
    }

    @Override
    public void addDocumentCreatePermissions(String sourceDocUuid, String destDocUuid) {
        Objects.requireNonNull(sourceDocUuid, "Null source document UUID");
        Objects.requireNonNull(destDocUuid, "Null destination document UUID");

        // Add create permissions.
        JooqUtil.context(securityDbConnProvider, context ->
                addDocumentCreatePermissions(context, sourceDocUuid, destDocUuid));
    }

    @Override
    public void setDocumentCreatePermissions(final String sourceDocUuid, final String destDocUuid) {
        Objects.requireNonNull(sourceDocUuid, "Null source document UUID");
        Objects.requireNonNull(destDocUuid, "Null destination document UUID");

        JooqUtil.transaction(securityDbConnProvider, context -> {
            // Clear existing permssions.
            clearDocumentCreatePermissions(context, destDocUuid);
            // Add new permissions.
            addDocumentCreatePermissions(context, sourceDocUuid, destDocUuid);
        });
    }

    private void addDocumentCreatePermissions(final DSLContext context,
                                              final String sourceDocUuid,
                                              final String destDocUuid) {
        context
                .insertInto(PERMISSION_DOC_CREATE)
                .columns(PERMISSION_DOC_CREATE.DOC_UUID,
                        PERMISSION_DOC_CREATE.USER_UUID,
                        PERMISSION_DOC_CREATE.DOC_TYPE_ID)
                .select(context
                        .select(DSL.val(destDocUuid),
                                PERMISSION_DOC_CREATE_SOURCE.USER_UUID,
                                PERMISSION_DOC_CREATE_SOURCE.DOC_TYPE_ID)
                        .from(PERMISSION_DOC_CREATE_SOURCE)
                        .where(PERMISSION_DOC_CREATE_SOURCE.DOC_UUID.eq(sourceDocUuid)))
                .onDuplicateKeyUpdate()
                .set(PERMISSION_DOC_CREATE.DOC_TYPE_ID, PERMISSION_DOC_CREATE.DOC_TYPE_ID)
                .execute();
    }

    private void clearDocumentCreatePermissions(final DSLContext context,
                                                final String docUuid) {
        // Clear create permissions.
        context
                .deleteFrom(PERMISSION_DOC_CREATE)
                .where(PERMISSION_DOC_CREATE.DOC_UUID.eq(docUuid))
                .execute();
    }

    /**
     * Get permissions for users on documents or folders plus any permissions that they inherit from their group
     * membership.
     *
     * @param request The Fetch request.
     * @return A result page of user permissions.
     */
    @Override
    public ResultPage<DocumentUserPermissions> fetchDocumentUserPermissions(
            final FetchDocumentUserPermissionsRequest request) {
        Objects.requireNonNull(request, "Null request");
        final DocRef docRef = request.getDocRef();
        Objects.requireNonNull(docRef, "Null doc ref");
        if (ExplorerConstants.isFolderOrSystem(docRef)) {
            return fetchDeepFolderUserPermissions(request);
        } else {
            return fetchDeepDocumentUserPermissions(request);
        }
    }

    /**
     * Get permissions for users on documents plus any permissions that they inherit from their group membership.
     *
     * @param request The Fetch request.
     * @return A result page of user permissions.
     */
    private ResultPage<DocumentUserPermissions> fetchDeepDocumentUserPermissions(
            final FetchDocumentUserPermissionsRequest request) {
        final UserDaoImpl userDao = userDaoProvider.get();
        final DocRef docRef = request.getDocRef();

        final int offset = JooqUtil.getOffset(request.getPageRequest());
        final int limit = JooqUtil.getLimit(request.getPageRequest(), true);

        final Collection<OrderField<?>> orderFields = userDao.createOrderFields(request);

        final List<Condition> conditions = new ArrayList<>();
        conditions.add(userDao.getUserCondition(request.getExpression()));
        if (request.getUserRef() != null) {
            conditions.add(STROOM_USER.UUID.eq(request.getUserRef().getUuid()));
        } else {
            conditions.add(STROOM_USER.ENABLED.eq(true));
        }

        final StroomUser su = STROOM_USER.as("su");
        final StroomUserGroup sug = STROOM_USER_GROUP.as("sug");
        final PermissionDoc pd = PERMISSION_DOC.as("pd");
        final PermissionDoc pdParent = PERMISSION_DOC.as("pd_parent");

        final Name cte = DSL.name("cte");
        final Field<String> cteUserUuid = DSL.field(cte.append("user_uuid"), String.class);
        final Field<String> cteGroupUuid = DSL.field(cte.append("group_uuid"), String.class);
        final Field<String> ctePerms = DSL.field(cte.append("perms"), String.class);
        final Field<String> cteInheritedPerms = DSL.field(cte.append("inherited_perms"), String.class);

        final List<DocumentUserPermissions> list = JooqUtil.contextResult(securityDbConnProvider, context -> {

            // Create a select to group permissions and parent permissions for the doc.
            final Select<?> select = context
                    .select(
                            su.UUID.as("user_uuid"),
                            sug.GROUP_UUID,
                            DSL.groupConcatDistinct(pd.PERMISSION_ID).as("perms"),
                            DSL.groupConcatDistinct(pdParent.PERMISSION_ID).as("parent_perms"))
                    .from(su)
                    .leftOuterJoin(sug)
                    .on(sug.USER_UUID.eq(su.UUID))
                    .leftOuterJoin(pd)
                    .on(pd.USER_UUID.eq(su.UUID).and(pd.DOC_UUID.eq(docRef.getUuid())))
                    .leftOuterJoin(pdParent)
                    .on(pdParent.USER_UUID.eq(sug.GROUP_UUID).and(pdParent.DOC_UUID.eq(docRef.getUuid())))
                    .groupBy(su.UUID, sug.GROUP_UUID);

            final Table<?> v = select.asTable("v");
            final Field<String> vUserUuid = v.field("user_uuid", String.class);
            final Field<String> vGroupUuid = v.field("group_uuid", String.class);
            final Field<String> vPerms = v.field("perms", String.class);
            final Field<String> vParentPerms = v.field("parent_perms", String.class);
            assert vUserUuid != null;
            assert vGroupUuid != null;
            assert vPerms != null;
            assert vParentPerms != null;

            // Create a view to recursively aggregate parent permissions for users and groups so we can see all
            // inherited permissions.
            // Create common table expression to apply `with recursive`.
            final CommonTableExpression<?> commonTableExpression = cte
                    .as(context
                            .select(
                                    vUserUuid,
                                    vGroupUuid,
                                    vPerms,
                                    vParentPerms.as("inherited_perms"))
                            .from(v)
                            .unionAll(
                                    context.select(
                                                    vUserUuid,
                                                    vGroupUuid,
                                                    vPerms,
                                                    DSL.if_(cteInheritedPerms.isNull(), vParentPerms,
                                                            DSL.if_(vParentPerms.isNull(),
                                                                    cteInheritedPerms,
                                                                    DSL.concat(
                                                                            DSL.concat(cteInheritedPerms,
                                                                                    ","),
                                                                            vParentPerms))))
                                            .from(DSL.table(cte))
                                            .join(v).on(vGroupUuid.eq(cteUserUuid))));

            // Apply `with recursive`
            final Table<?> recursive = context
                    .withRecursive(commonTableExpression)
                    .select(
                            cteUserUuid,
                            cteGroupUuid,
                            DSL.groupConcatDistinct(ctePerms).as("perms"),
                            DSL.groupConcatDistinct(cteInheritedPerms).as("inherited_perms"))
                    .from(commonTableExpression)
                    .groupBy(cteUserUuid, cteGroupUuid)
                    .asTable();

            final Field<String> recUserUuid = recursive.field("user_uuid", String.class);
            final Field<String> recGroupUuid = recursive.field("group_uuid", String.class);
            final Field<String> recPerms = recursive.field("perms", String.class);
            final Field<String> recInheritedPerms = recursive.field("inherited_perms", String.class);
            assert recUserUuid != null;
            assert recGroupUuid != null;
            assert recPerms != null;
            assert recInheritedPerms != null;

            // Add additional conditions if we want to just show effective or explicit permissions.
            switch (request.getShowLevel()) {
                case SHOW_EFFECTIVE -> conditions.add(recPerms.isNotNull()
                        .or(recInheritedPerms.isNotNull()));
                case SHOW_EXPLICIT -> conditions.add(recPerms.isNotNull());
            }

            // Join recursive select to user.
            return context
                    .select(STROOM_USER.UUID,
                            STROOM_USER.NAME,
                            STROOM_USER.DISPLAY_NAME,
                            STROOM_USER.FULL_NAME,
                            STROOM_USER.IS_GROUP,
                            recPerms,
                            recInheritedPerms)
                    .from(STROOM_USER)
                    .join(recursive).on(recUserUuid.eq(STROOM_USER.UUID))
                    .where(conditions)
                    .orderBy(orderFields)
                    .offset(offset)
                    .limit(limit)
                    .fetch();

        }).map(r -> {
            final UserRef userRef = recordToUserRef(r);
            final String perms = r.get(ctePerms);
            final String inheritedPerms = r.get(cteInheritedPerms);
            final DocumentPermission permission = getHighestDocPermission(perms);
            final DocumentPermission inherited = getHighestDocPermission(inheritedPerms);
            return new DocumentUserPermissions(
                    userRef,
                    permission,
                    inherited,
                    Collections.emptySet(),
                    Collections.emptySet());
        });

        return ResultPage.createCriterialBasedList(list, request);
    }


    /**
     * Get permissions for users on folders plus any permissions that they inherit from their group membership.
     * Also join to the document create permission table to see what document create permissions users have explicitly
     * or inherit from their group membership.
     *
     * @param request The Fetch request.
     * @return A result page of user permissions.
     */
    private ResultPage<DocumentUserPermissions> fetchDeepFolderUserPermissions(
            final FetchDocumentUserPermissionsRequest request) {
        final UserDaoImpl userDao = userDaoProvider.get();
        final DocRef docRef = request.getDocRef();

        final int offset = JooqUtil.getOffset(request.getPageRequest());
        final int limit = JooqUtil.getLimit(request.getPageRequest(), true);

        final Collection<OrderField<?>> orderFields = userDao.createOrderFields(request);

        final List<Condition> conditions = new ArrayList<>();

        conditions.add(userDao.getUserCondition(request.getExpression()));
        if (request.getUserRef() != null) {
            conditions.add(STROOM_USER.UUID.eq(request.getUserRef().getUuid()));
        } else {
            conditions.add(STROOM_USER.ENABLED.eq(true));
        }

        // If we have a single doc then try to deliver more useful permissions.
        final StroomUser su = STROOM_USER.as("su");
        final StroomUserGroup sug = STROOM_USER_GROUP.as("sug");
        final PermissionDoc pd = PERMISSION_DOC.as("pd");
        final PermissionDoc pdParent = PERMISSION_DOC.as("pd_parent");
        final PermissionDocCreate pdc = PERMISSION_DOC_CREATE.as("pdc");
        final PermissionDocCreate pdcParent = PERMISSION_DOC_CREATE.as("pdc_parent");

        final Name cte = DSL.name("cte");
        final Field<String> cteUserUuid = DSL.field(cte.append("user_uuid"), String.class);
        final Field<String> cteGroupUuid = DSL.field(cte.append("group_uuid"), String.class);
        final Field<String> ctePerms = DSL.field(cte.append("perms"), String.class);
        final Field<String> cteInheritedPerms = DSL.field(cte.append("inherited_perms"), String.class);
        final Field<String> cteCreatePerms = DSL.field(cte.append("create_perms"), String.class);
        final Field<String> cteInheritedCreatePerms = DSL.field(cte.append("inherited_create_perms"),
                String.class);

        final List<DocumentUserPermissions> list = JooqUtil.contextResult(securityDbConnProvider, context -> {

            // Create a select to group permissions and parent permissions for the doc.
            final Select<?> select = context
                    .select(
                            su.UUID.as("user_uuid"),
                            sug.GROUP_UUID,
                            DSL.groupConcatDistinct(pd.PERMISSION_ID).as("perms"),
                            DSL.groupConcatDistinct(pdParent.PERMISSION_ID).as("parent_perms"),
                            DSL.groupConcatDistinct(pdc.DOC_TYPE_ID).as("create_perms"),
                            DSL.groupConcatDistinct(pdcParent.DOC_TYPE_ID).as("parent_create_perms"))
                    .from(su)
                    .leftOuterJoin(sug)
                    .on(sug.USER_UUID.eq(su.UUID))
                    .leftOuterJoin(pd)
                    .on(pd.USER_UUID.eq(su.UUID).and(pd.DOC_UUID.eq(docRef.getUuid())))
                    .leftOuterJoin(pdParent)
                    .on(pdParent.USER_UUID.eq(sug.GROUP_UUID).and(pdParent.DOC_UUID.eq(docRef.getUuid())))
                    .leftOuterJoin(pdc)
                    .on(pdc.USER_UUID.eq(su.UUID).and(pdc.DOC_UUID.eq(docRef.getUuid())))
                    .leftOuterJoin(pdcParent)
                    .on(pdcParent.USER_UUID.eq(sug.GROUP_UUID).and(pdcParent.DOC_UUID.eq(docRef.getUuid())))
                    .groupBy(su.UUID, sug.GROUP_UUID);

            final Table<?> v = select.asTable("v");
            final Field<String> vUserUuid = v.field("user_uuid", String.class);
            final Field<String> vGroupUuid = v.field("group_uuid", String.class);
            final Field<String> vPerms = v.field("perms", String.class);
            final Field<String> vParentPerms = v.field("parent_perms", String.class);
            final Field<String> vCreatePerms = v.field("create_perms", String.class);
            final Field<String> vParentCreatePerms = v.field("parent_create_perms", String.class);
            assert vUserUuid != null;
            assert vGroupUuid != null;
            assert vPerms != null;
            assert vParentPerms != null;
            assert vCreatePerms != null;
            assert vParentCreatePerms != null;

            // Create a view to recursively aggregate parent permissions for users and groups so we can see all
            // inherited permissions.
            // Create common table expression to apply `with recursive`.
            final CommonTableExpression<?> commonTableExpression = cte
                    .as(context
                            .select(
                                    vUserUuid,
                                    vGroupUuid,
                                    vPerms,
                                    vParentPerms.as("inherited_perms"),
                                    vCreatePerms,
                                    vParentCreatePerms.as("inherited_create_perms"))
                            .from(v)
                            .unionAll(
                                    context.select(
                                                    vUserUuid,
                                                    vGroupUuid,
                                                    vPerms,
                                                    DSL.if_(cteInheritedPerms.isNull(),
                                                            vParentPerms,
                                                            DSL.if_(vParentPerms.isNull(),
                                                                    cteInheritedPerms,
                                                                    DSL.concat(
                                                                            DSL.concat(cteInheritedPerms,
                                                                                    ","),
                                                                            vParentPerms))),
                                                    vCreatePerms,
                                                    DSL.if_(cteInheritedCreatePerms.isNull(),
                                                            vParentCreatePerms,
                                                            DSL.if_(vParentCreatePerms.isNull(),
                                                                    cteInheritedCreatePerms,
                                                                    DSL.concat(
                                                                            DSL.concat(cteInheritedCreatePerms,
                                                                                    ","),
                                                                            vParentCreatePerms))))
                                            .from(DSL.table(cte))
                                            .join(v).on(vGroupUuid.eq(cteUserUuid))));

            // Apply `with recursive`
            final Table<?> recursive = context
                    .withRecursive(commonTableExpression)
                    .select(
                            cteUserUuid,
                            cteGroupUuid,
                            DSL.groupConcatDistinct(ctePerms).as("perms"),
                            DSL.groupConcatDistinct(cteInheritedPerms).as("inherited_perms"),
                            DSL.groupConcatDistinct(cteCreatePerms).as("create_perms"),
                            DSL.groupConcatDistinct(cteInheritedCreatePerms).as("inherited_create_perms"))
                    .from(commonTableExpression)
                    .groupBy(cteUserUuid, cteGroupUuid)
                    .asTable();

            final Field<String> recUserUuid = recursive.field("user_uuid", String.class);
            final Field<String> recGroupUuid = recursive.field("group_uuid", String.class);
            final Field<String> recPerms = recursive.field("perms", String.class);
            final Field<String> recInheritedPerms = recursive.field("inherited_perms", String.class);
            final Field<String> recCreatePerms = recursive.field("create_perms", String.class);
            final Field<String> recInheritedCreatePerms = recursive.field("inherited_create_perms",
                    String.class);
            assert recUserUuid != null;
            assert recGroupUuid != null;
            assert recPerms != null;
            assert recInheritedPerms != null;
            assert recCreatePerms != null;
            assert recInheritedCreatePerms != null;

            // Add additional conditions if we want to just show effective or explicit permissions.
            switch (request.getShowLevel()) {
                case SHOW_EFFECTIVE -> conditions.add(recPerms.isNotNull()
                        .or(recInheritedPerms.isNotNull())
                        .or(recCreatePerms.isNotNull())
                        .or(recInheritedCreatePerms.isNotNull()));
                case SHOW_EXPLICIT -> conditions.add(recPerms.isNotNull()
                        .or(recCreatePerms.isNotNull()));
            }

            // Join recursive select to user.
            return context
                    .select(STROOM_USER.UUID,
                            STROOM_USER.NAME,
                            STROOM_USER.DISPLAY_NAME,
                            STROOM_USER.FULL_NAME,
                            STROOM_USER.IS_GROUP,
                            recPerms,
                            recInheritedPerms,
                            recCreatePerms,
                            recInheritedCreatePerms)
                    .from(STROOM_USER)
                    .join(recursive).on(recUserUuid.eq(STROOM_USER.UUID))
                    .where(conditions)
                    .orderBy(orderFields)
                    .offset(offset)
                    .limit(limit)
                    .fetch();

        }).map(r -> {
            final UserRef userRef = recordToUserRef(r);
            final String perms = r.get(ctePerms);
            final String inheritedPerms = r.get(cteInheritedPerms);
            final String createPerms = r.get(cteCreatePerms);
            final String inheritedCreatePerms = r.get(cteInheritedCreatePerms);
            final DocumentPermission permission = getHighestDocPermission(perms);
            final DocumentPermission inherited = getHighestDocPermission(inheritedPerms);
            final Set<String> documentCreatePermissions = getDocCreatePermissionSet(createPerms);
            final Set<String> inheritedDocumentCreatePermissions = getDocCreatePermissionSet(
                    inheritedCreatePerms);
            return new DocumentUserPermissions(
                    userRef,
                    permission,
                    inherited,
                    documentCreatePermissions,
                    inheritedDocumentCreatePermissions);
        });

        return ResultPage.createCriterialBasedList(list, request);
    }


    private DocumentPermission getHighestDocPermission(final String perms) {
        DocumentPermission permission = null;
        if (!NullSafe.isBlankString(perms)) {
            final String[] parts = perms.split(",");
            for (final String part : parts) {
                final String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    final int permissionId = Integer.parseInt(trimmed);
                    final DocumentPermission documentPermission = DocumentPermission.PRIMITIVE_VALUE_CONVERTER
                            .fromPrimitiveValue((byte) permissionId);
                    if (permission == null || documentPermission.isHigher(permission)) {
                        permission = documentPermission;
                    }
                }
            }
        }
        return permission;
    }

    private Set<String> getDocCreatePermissionSet(final String perms) {
        if (NullSafe.isBlankString(perms)) {
            return Collections.emptySet();
        }

        final String[] parts = perms.split(",");
        final Set<String> types = new HashSet<>(parts.length);
        for (final String part : parts) {
            final String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                final int typeId = Integer.parseInt(trimmed);
                final String type = docTypeIdDao.get(typeId);
                if (type != null) {
                    types.add(type);
                }
            }
        }
        return types;
    }

    private UserRef recordToUserRef(final Record r) {
        return UserRef
                .builder()
                .uuid(r.get(STROOM_USER.UUID))
                .subjectId(r.get(STROOM_USER.NAME))
                .displayName(r.get(STROOM_USER.DISPLAY_NAME))
                .fullName(r.get(STROOM_USER.FULL_NAME))
                .group(r.get(STROOM_USER.IS_GROUP))
                .build();
    }
}
