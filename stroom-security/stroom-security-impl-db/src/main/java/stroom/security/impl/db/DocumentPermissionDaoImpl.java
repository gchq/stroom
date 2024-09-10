package stroom.security.impl.db;

import stroom.db.util.JooqUtil;
import stroom.docref.DocRef;
import stroom.explorer.shared.ExplorerConstants;
import stroom.security.impl.DocTypeIdDao;
import stroom.security.impl.DocumentPermissionDao;
import stroom.security.impl.UserDocumentPermissions;
import stroom.security.impl.db.jooq.tables.PermissionDoc;
import stroom.security.impl.db.jooq.tables.PermissionDocCreate;
import stroom.security.shared.DocumentPermission;
import stroom.security.shared.DocumentUserPermissions;
import stroom.security.shared.FetchDocumentUserPermissionsRequest;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserRef;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.OrderField;
import org.jooq.Result;
import org.jooq.impl.DSL;
import org.jooq.types.UByte;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static stroom.security.impl.db.jooq.tables.PermissionDoc.PERMISSION_DOC;
import static stroom.security.impl.db.jooq.tables.PermissionDocCreate.PERMISSION_DOC_CREATE;
import static stroom.security.impl.db.jooq.tables.StroomUser.STROOM_USER;

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
        JooqUtil.context(securityDbConnProvider, context -> context
                .insertInto(PERMISSION_DOC_CREATE)
                .columns(PERMISSION_DOC_CREATE.DOC_UUID,
                        PERMISSION_DOC_CREATE.USER_UUID,
                        PERMISSION_DOC_CREATE.DOC_TYPE_ID)
                .values(documentUuid, userUuid, docTypeId)
                .onDuplicateKeyUpdate()
                .set(PERMISSION_DOC_CREATE.DOC_TYPE_ID, docTypeId)
                .execute());
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
    public void removeDocumentUserCreatePermissions(final String documentUuid, final String userUuid) {
        Objects.requireNonNull(documentUuid, "Null document UUID");
        Objects.requireNonNull(userUuid, "Null user UUID");

        JooqUtil.context(securityDbConnProvider, context -> context
                .deleteFrom(PERMISSION_DOC_CREATE)
                .where(PERMISSION_DOC_CREATE.DOC_UUID.eq(documentUuid))
                .and(PERMISSION_DOC_CREATE.USER_UUID.eq(userUuid))
                .execute());
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

    @Override
    public ResultPage<DocumentUserPermissions> fetchDocumentUserPermissions(
            final FetchDocumentUserPermissionsRequest request) {
        Objects.requireNonNull(request, "Null request");
        Objects.requireNonNull(request.getDocRef(), "Null doc ref");
        final UserDaoImpl userDao = userDaoProvider.get();

        final DocRef docRef = request.getDocRef();

        final int offset = JooqUtil.getOffset(request.getPageRequest());
        final int limit = JooqUtil.getLimit(request.getPageRequest(), true);

        final Collection<OrderField<?>> orderFields = userDao.createOrderFields(request);

        final List<Condition> conditions = new ArrayList<>();

        conditions.add(userDao.getUserCondition(request.getExpression()));
        conditions.add(STROOM_USER.ENABLED.eq(true));

        // If we have a single doc then try to deliver more useful permissions.
        final Result<?> result = JooqUtil.contextResult(securityDbConnProvider, context -> {
            if (request.isAllUsers()) {
                return context.select(
                                PERMISSION_DOC.PERMISSION_ID,
                                STROOM_USER.UUID,
                                STROOM_USER.NAME,
                                STROOM_USER.DISPLAY_NAME,
                                STROOM_USER.FULL_NAME,
                                STROOM_USER.IS_GROUP)
                        .from(STROOM_USER)
                        .leftOuterJoin(PERMISSION_DOC)
                        .on(PERMISSION_DOC.USER_UUID.eq(STROOM_USER.UUID)
                                .and(PERMISSION_DOC.DOC_UUID.eq(docRef.getUuid())))
                        .where(conditions)
                        .orderBy(orderFields)
                        .offset(offset)
                        .limit(limit)
                        .fetch();

            } else if (ExplorerConstants.isFolderOrSystem(docRef)) {
                conditions
                        .add(PERMISSION_DOC.DOC_UUID.eq(docRef.getUuid())
                                .or(PERMISSION_DOC_CREATE.DOC_UUID.eq(docRef.getUuid())));
                return context.selectDistinct(
                                PERMISSION_DOC.PERMISSION_ID,
                                STROOM_USER.UUID,
                                STROOM_USER.NAME,
                                STROOM_USER.DISPLAY_NAME,
                                STROOM_USER.FULL_NAME,
                                STROOM_USER.IS_GROUP)
                        .from(STROOM_USER)
                        .leftOuterJoin(PERMISSION_DOC)
                        .on(PERMISSION_DOC.USER_UUID.eq(STROOM_USER.UUID))
                        .leftOuterJoin(PERMISSION_DOC_CREATE)
                        .on(PERMISSION_DOC_CREATE.USER_UUID.eq(STROOM_USER.UUID))
                        .where(conditions)
                        .orderBy(orderFields)
                        .offset(offset)
                        .limit(limit)
                        .fetch();

            } else {
                conditions
                        .add(PERMISSION_DOC.DOC_UUID.eq(docRef.getUuid()));
                return context.select(
                                PERMISSION_DOC.PERMISSION_ID,
                                STROOM_USER.UUID,
                                STROOM_USER.NAME,
                                STROOM_USER.DISPLAY_NAME,
                                STROOM_USER.FULL_NAME,
                                STROOM_USER.IS_GROUP)
                        .from(STROOM_USER)
                        .join(PERMISSION_DOC)
                        .on(PERMISSION_DOC.USER_UUID.eq(STROOM_USER.UUID))
                        .where(conditions)
                        .orderBy(orderFields)
                        .offset(offset)
                        .limit(limit)
                        .fetch();
            }
        });

        final List<DocumentUserPermissions> list = result.map(r -> {
            final UserRef userRef = UserRef
                    .builder()
                    .uuid(r.get(STROOM_USER.UUID))
                    .subjectId(r.get(STROOM_USER.NAME))
                    .displayName(r.get(STROOM_USER.DISPLAY_NAME))
                    .fullName(r.get(STROOM_USER.FULL_NAME))
                    .group(r.get(STROOM_USER.IS_GROUP))
                    .build();
            final UByte value = r.get(PERMISSION_DOC.PERMISSION_ID);
            DocumentPermission documentPermission = null;
            if (value != null) {
                documentPermission = DocumentPermission.PRIMITIVE_VALUE_CONVERTER
                        .fromPrimitiveValue(value.byteValue());
            }

            Set<String> documentCreatePermissions = null;
            if (ExplorerConstants.isFolderOrSystem(docRef)) {
                documentCreatePermissions =
                        getDocumentUserCreatePermissions(docRef.getUuid(), userRef.getUuid());
            }

            return new DocumentUserPermissions(
                    userRef,
                    documentPermission,
                    documentCreatePermissions);
        });

        return ResultPage.createCriterialBasedList(list, request);
    }
}
