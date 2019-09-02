/*
 * (C) Copyright 2018 Nuxeo (http://nuxeo.com/) and others.
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
 *
 * Contributors:
 *     Funsho David
 */

package org.nuxeo.ecm.platform.comment;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.nuxeo.ecm.core.api.event.DocumentEventTypes.DOCUMENT_UPDATED;
import static org.nuxeo.ecm.core.api.security.ACL.LOCAL_ACL;
import static org.nuxeo.ecm.platform.comment.AbstractTestCommentManager.newConfig;
import static org.nuxeo.ecm.platform.comment.api.CommentConstants.MIGRATION_ID;
import static org.nuxeo.ecm.platform.comment.api.CommentConstants.MIGRATION_STATE_PROPERTY;
import static org.nuxeo.ecm.platform.comment.api.CommentConstants.MIGRATION_STATE_RELATION;
import static org.nuxeo.ecm.platform.comment.api.CommentConstants.MIGRATION_STATE_SECURED;
import static org.nuxeo.ecm.platform.comment.api.CommentConstants.MIGRATION_STEP_PROPERTY_TO_SECURED;
import static org.nuxeo.ecm.platform.comment.api.CommentConstants.MIGRATION_STEP_RELATION_TO_PROPERTY;
import static org.nuxeo.ecm.platform.comment.workflow.utils.CommentsConstants.COMMENT_AUTHOR;
import static org.nuxeo.ecm.platform.comment.workflow.utils.CommentsConstants.COMMENT_DOC_TYPE;
import static org.nuxeo.ecm.platform.comment.workflow.utils.CommentsConstants.COMMENT_PARENT_ID;
import static org.nuxeo.ecm.platform.ec.notification.NotificationConstants.DISABLE_NOTIFICATION_SERVICE;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.awaitility.Duration;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.core.api.PathRef;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.test.CapturingEventListener;
import org.nuxeo.ecm.core.query.sql.NXQL;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.ecm.platform.comment.api.Comment;
import org.nuxeo.ecm.platform.comment.api.CommentManager;
import org.nuxeo.ecm.platform.comment.impl.CommentManagerImpl;
import org.nuxeo.ecm.platform.comment.impl.CommentsMigrator;
import org.nuxeo.ecm.platform.comment.impl.PropertyCommentManager;
import org.nuxeo.ecm.platform.comment.service.CommentServiceConfig;
import org.nuxeo.ecm.platform.notification.api.NotificationManager;
import org.nuxeo.ecm.platform.relations.api.Graph;
import org.nuxeo.ecm.platform.relations.api.RelationManager;
import org.nuxeo.ecm.platform.relations.api.Resource;
import org.nuxeo.ecm.platform.relations.api.Statement;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.migration.MigrationService;
import org.nuxeo.runtime.migration.MigrationService.MigrationContext;
import org.nuxeo.runtime.migration.MigrationService.Migrator;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.TransactionalFeature;

/**
 * @since 10.3
 */
@RunWith(FeaturesRunner.class)
@Features(PlatformFeature.class)
@RepositoryConfig(cleanup = Granularity.METHOD)
@Deploy("org.nuxeo.ecm.platform.comment")
@Deploy("org.nuxeo.ecm.platform.notification.api")
@Deploy("org.nuxeo.ecm.platform.notification.core")
@Deploy("org.nuxeo.ecm.relations.api")
@Deploy("org.nuxeo.ecm.relations")
@Deploy("org.nuxeo.ecm.relations.jena")
@Deploy("org.nuxeo.ecm.platform.comment.tests:OSGI-INF/comment-jena-contrib.xml")
public class TestCommentsMigrator {

    protected static final int NB_COMMENTS_BY_FILE = 50;

    @Inject
    protected CoreSession session;

    @Inject
    protected TransactionalFeature transactionalFeature;

    @Inject
    protected MigrationService migrationService;

    @Inject
    protected NotificationManager notificationManager;

    protected DocumentModel firstFileToComment;

    protected DocumentModel secondFileToComment;

    protected DocumentModel proxyFileToComment;

    @Before
    public void setUp() {
        DocumentModel domain = session.createDocumentModel("/", "test-domain", "Domain");
        session.createDocument(domain);

        DocumentModel anotherDomain = session.createDocumentModel("/", "another-domain", "Domain");
        session.createDocument(anotherDomain);

        // Create files which will be commented
        firstFileToComment = session.createDocumentModel(domain.getPathAsString(), "file1", "File");
        firstFileToComment = session.createDocument(firstFileToComment);

        secondFileToComment = session.createDocumentModel(domain.getPathAsString(), "file2", "File");
        secondFileToComment = session.createDocument(secondFileToComment);

        proxyFileToComment = session.createProxy(secondFileToComment.getRef(), anotherDomain.getRef());

        // Create comments as relations on these files
        createCommentsAsRelations();
    }

    @Test
    public void testMigration() {
        // Create the migration context
        Migrator migrator = new CommentsMigrator();

        // First step of migrate: from 'Relation' to 'Property'
        migrateFromRelationToProperty(migrator);

        // Simulate the existence of unsecured comments
        mockUnsecuredPropertyComments();

        // Second step of migrate: from 'Property' to 'Secured'
        migrateFromPropertyToSecured(migrator);
    }

    @Test
    @Deploy("org.nuxeo.ecm.platform.comment.tests:OSGI-INF/relation-comment-manager-override.xml")
    @SuppressWarnings("deprecation")
    public void testMigrationThroughService() {
        CommentManager commentManager;

        commentManager = Framework.getService(CommentManager.class);
        assertTrue(commentManager.getClass().getName(), commentManager instanceof CommentManagerImpl);

        MigrationService.MigrationStatus status = migrationService.getStatus(MIGRATION_ID);
        assertNotNull(status);
        assertFalse(status.isRunning());
        assertEquals(MIGRATION_STATE_RELATION, status.getState());

        // Launch the step relation to property
        runMigration(() -> {
            migrationService.runStep(MIGRATION_ID, MIGRATION_STEP_RELATION_TO_PROPERTY);

            // Wait a bit for the migration to start and poll until migration done
            Duration duration = new Duration(1, SECONDS);
            await().pollDelay(duration)
                   .pollInterval(duration)
                   .until(() -> !migrationService.getStatus(MIGRATION_ID).isRunning());
        });

        commentManager = Framework.getService(CommentManager.class);
        assertTrue(commentManager.getClass().getName(), commentManager instanceof PropertyCommentManager);

        status = migrationService.getStatus(MIGRATION_ID);
        assertNotNull(status);
        assertFalse(status.isRunning());
        assertEquals(MIGRATION_STATE_PROPERTY, status.getState());
    }

    @SuppressWarnings("deprecation")
    protected void runMigration(Runnable migrator) {
        try (CapturingEventListener listener = new CapturingEventListener(DOCUMENT_UPDATED)) {
            migrator.run();
            List<Event> events = listener.getCapturedEvents();

            // Ensure the migration is done silently
            for (Event event : events) {
                assertEquals(TRUE, event.getContext().getProperty(DISABLE_NOTIFICATION_SERVICE));
            }
        }
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testProbe() {

        CommentServiceConfig config = newConfig();
        CommentManager relationCommentManager = new CommentManagerImpl(config);
        CommentManager propertyCommentManager = new PropertyCommentManager();

        DocumentModel domain = session.createDocumentModel("/", "test-domain", "Domain");
        session.createDocument(domain);
        DocumentModel file = session.createDocumentModel("/test-domain", "anotherFile", "File");
        file = session.createDocument(file);
        session.save();

        Migrator migrator = new CommentsMigrator();
        assertEquals(MIGRATION_STATE_RELATION, migrator.probeState());

        // Both a relation-based comment and a property-based comment, detected as not migrated
        DocumentModel otherComment = session.createDocumentModel(null, "comment", COMMENT_DOC_TYPE);
        otherComment.setPropertyValue(COMMENT_PARENT_ID, file.getId());
        propertyCommentManager.createComment(file, otherComment);
        session.save();
        assertEquals(MIGRATION_STATE_RELATION, migrator.probeState());

        // Migrate the created relation based comment to property
        migrateFromRelationToProperty(migrator);

        // Comments detected as 'Property'
        assertEquals(MIGRATION_STATE_PROPERTY, migrator.probeState());

        // Just a relation-based comment, detected as not migrated
        DocumentModel comment = session.createDocumentModel(null, "comment", COMMENT_DOC_TYPE);
        comment.setPropertyValue(COMMENT_AUTHOR, session.getPrincipal().getName());
        comment = relationCommentManager.createComment(file, comment);
        session.save();
        assertEquals(MIGRATION_STATE_RELATION, migrator.probeState());

        // just a property-based comment, detected as migrated
        relationCommentManager.deleteComment(file, comment);
        session.save();

        // Simulate comment deletion event
        RelationManager relationManager = Framework.getService(RelationManager.class);
        Resource commentRes = relationManager.getResource(config.commentNamespace, comment, null);
        Graph graph = relationManager.getGraph(config.graphName, session);
        List<Statement> statementList = graph.getStatements(commentRes, null, null);
        graph.remove(statementList);

        // No more relation comments detected as 'Relation'
        assertEquals(MIGRATION_STATE_PROPERTY, migrator.probeState());

        // Migrate the created property based comment to secured
        migrateFromPropertyToSecured(migrator);

        // No more unsecured property comments
        assertEquals(MIGRATION_STATE_SECURED, migrator.probeState());
    }

    /**
     * @since 11.1
     */
    protected void migrateFromRelationToProperty(Migrator migrator) {
        ProgressMigrationContext migrationContext = new ProgressMigrationContext();
        runMigration(() -> migrator.run(MIGRATION_STEP_RELATION_TO_PROPERTY, migrationContext));

        CommentManager propertyCommentManager = new PropertyCommentManager();
        List<Comment> commentsForFile1 = propertyCommentManager.getComments(session, firstFileToComment.getId());
        List<Comment> commentsForFile2 = propertyCommentManager.getComments(session, secondFileToComment.getId());
        List<Comment> commentsForProxy = propertyCommentManager.getComments(session, proxyFileToComment.getId());

        assertEquals(NB_COMMENTS_BY_FILE * 3,
                commentsForFile1.size() + commentsForFile2.size() + commentsForProxy.size());
        for (Comment comment : commentsForFile1) {
            assertEquals(firstFileToComment.getId(), comment.getParentId());
        }
        for (Comment comment : commentsForFile2) {
            assertEquals(secondFileToComment.getId(), comment.getParentId());
            assertNotEquals(proxyFileToComment.getId(), comment.getParentId());
        }

        for (Comment comment : commentsForProxy) {
            assertEquals(proxyFileToComment.getId(), comment.getParentId());
            assertNotEquals(secondFileToComment.getId(), comment.getParentId());
        }

        List<String> expectedLines = Arrays.asList( //
                "Initializing: 0/-1", //
                "Migrating comments from Relation to Property: 1/150", //
                "Migrating comments from Relation to Property: 51/150", //
                "Migrating comments from Relation to Property: 101/150", //
                "Migrating comments from Relation to Property: 150/150", //
                "Done Migrating from Relation to Property: 150/150");
        assertEquals(expectedLines, migrationContext.getProgressLines());
    }

    /**
     * @since 11.1
     */
    protected void migrateFromPropertyToSecured(Migrator migrator) {
        ProgressMigrationContext migrationContext = new ProgressMigrationContext();

        runMigration(() -> migrator.run(MIGRATION_STEP_PROPERTY_TO_SECURED, migrationContext));

        List<String> expectedLines = Arrays.asList( //
                "Initializing: 0/-1", //
                "Migrating comments from Property to Secured: 1/150", //
                "Migrating comments from Property to Secured: 51/150", //
                "Migrating comments from Property to Secured: 101/150", //
                "Migrating comments from Property to Secured: 150/150", //
                "Done Migrating from Property to Secured: 150/150");
        assertEquals(expectedLines, migrationContext.getProgressLines());

        checkCommentsForDocument(firstFileToComment.getId());
        checkCommentsForDocument(secondFileToComment.getId());
        DocumentModelList rootCommentFolder = session.query(CommentsMigrator.GET_COMMENTS_FOLDERS_QUERY);
        assertEquals(0, rootCommentFolder.size());
    }

    /**
     * @since 11.1
     */
    protected void checkCommentsForDocument(String docId) {
        DocumentModelList dml = session.query(String.format("SELECT * FROM Document WHERE %s = '%s' AND %s = '%s'",
                NXQL.ECM_PARENTID, docId, NXQL.ECM_PRIMARYTYPE, COMMENT_DOC_TYPE));
        assertEquals(NB_COMMENTS_BY_FILE, dml.size());
        dml.forEach((doc) -> assertNull(doc.getACP().getACL(LOCAL_ACL)));
    }

    /**
     * @since 11.1
     */
    protected void createCommentsAsRelations() {
        CommentManager relationCommentManager = new CommentManagerImpl(newConfig());
        NuxeoPrincipal principal = session.getPrincipal();
        // create some comments as relation
        for (int i = 0; i < NB_COMMENTS_BY_FILE * 2; i++) {
            DocumentModel comment = session.createDocumentModel(null, "comment_" + i, COMMENT_DOC_TYPE);
            DocumentModel createdComment;
            if (i % 2 == 0) {
                createdComment = relationCommentManager.createComment(firstFileToComment, comment);
            } else {
                createdComment = relationCommentManager.createComment(secondFileToComment, comment);
            }
            notificationManager.addSubscription(principal.getName(), "notification" + i, createdComment, FALSE,
                    principal, "notification" + i);
        }

        for (int i = 0; i < 50; i++) {
            DocumentModel comment = session.createDocumentModel(null, "comment_proxy" + i, COMMENT_DOC_TYPE);
            DocumentModel createdComment = relationCommentManager.createComment(proxyFileToComment, comment);

            notificationManager.addSubscription(principal.getName(), "notification" + i, createdComment, FALSE,
                    principal, "notification" + i);
        }

        session.save();
        transactionalFeature.nextTransaction();
    }

    /**
     * @since 11.1
     */
    public static class ProgressMigrationContext implements MigrationContext {
        protected final List<String> progressLines = new ArrayList<>();

        public List<String> getProgressLines() {
            return progressLines;
        }

        @Override
        public void reportProgress(String message, long num, long total) {
            progressLines.add(String.format("%s: %s/%s", message, num, total));
        }

        @Override
        public void requestShutdown() {
        }

        @Override
        public boolean isShutdownRequested() {
            return false;
        }
    }

    /**
     * @since 11.1
     */
    protected void mockUnsecuredPropertyComments() {
        CommentManager propertyCommentManager = new PropertyCommentManager();
        List<Comment> commentsForFile1 = propertyCommentManager.getComments(session, firstFileToComment.getId());

        List<DocumentRef> idRefs = commentsForFile1.stream()
                                                   .map(c -> new IdRef(c.getId()))
                                                   .collect(Collectors.toList());

        DocumentModel commentsRoot = session.getDocument(new PathRef("/test-domain/Comments"));
        session.move(idRefs, commentsRoot.getRef());
    }
}
