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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.nuxeo.ecm.platform.comment.workflow.utils.CommentsConstants.COMMENT_DOC_TYPE;
import static org.nuxeo.ecm.platform.comment.workflow.utils.CommentsConstants.COMMENT_PARENT_ID;

import org.junit.Test;
import org.junit.runners.model.FrameworkMethod;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.query.sql.NXQL;
import org.nuxeo.ecm.platform.comment.api.Comment;
import org.nuxeo.ecm.platform.comment.api.CommentManager;
import org.nuxeo.ecm.platform.comment.api.exceptions.CommentNotFoundException;
import org.nuxeo.ecm.platform.comment.impl.BridgeCommentManager;
import org.nuxeo.ecm.platform.comment.impl.CommentManagerImpl;
import org.nuxeo.ecm.platform.comment.impl.PropertyCommentManager;
import org.nuxeo.ecm.platform.comment.impl.TreeCommentManager;
import org.nuxeo.ecm.platform.comment.service.CommentService;
import org.nuxeo.ecm.platform.comment.service.CommentServiceConfig;
import org.nuxeo.ecm.platform.relations.api.Graph;
import org.nuxeo.ecm.platform.relations.api.RelationManager;
import org.nuxeo.ecm.platform.relations.api.Resource;
import org.nuxeo.ecm.platform.relations.api.impl.ResourceImpl;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.RunnerFeature;

/**
 * @since 10.3
 */
@Features(TestBridgeCommentManager.BridgeCommentManagerFeature.class)
@Deploy("org.nuxeo.ecm.platform.query.api")
@Deploy("org.nuxeo.ecm.relations.api")
@Deploy("org.nuxeo.ecm.relations")
@Deploy("org.nuxeo.ecm.relations.jena")
@Deploy("org.nuxeo.ecm.platform.comment.tests:OSGI-INF/comment-jena-contrib.xml")
public class TestBridgeCommentManager extends AbstractTestCommentManager {

    public static class BridgeCommentManagerFeature implements RunnerFeature {

        @Override
        public void beforeMethodRun(FeaturesRunner runner, FrameworkMethod method, Object test) {
            CommentManager first = new CommentManagerImpl(newConfig());
            CommentManager second = new PropertyCommentManager();
            CommentManager third = new TreeCommentManager();
            ((TestBridgeCommentManager) test).commentManager = new BridgeCommentManager(first, second, third);
        }
    }

    @Test
    public void shouldDetectCommentAsRelation() {
        // Use the comment as relation
        CommentManager anotherCommentManager = new CommentManagerImpl(newConfig());
        DocumentModel commentDocModel = createComment(anotherCommentManager);
        Comment comment = anotherCommentManager.getComment(session, commentDocModel.getId());
        assertNotNull(comment);

        // Get the document being commented
        DocumentModel docModelCommented = session.query(String.format("Select * From Document where %s = '%s'", NXQL.ECM_NAME, "anyFile")).get(0);

        BridgeCommentManager bridgeCommentManager = (BridgeCommentManager) commentManager;
        assertTrue(bridgeCommentManager.isCommentAsRelation(commentDocModel));
        assertTrue(bridgeCommentManager.isCommentAsRelation(docModelCommented, commentDocModel));
    }

    @Test
    public void shouldDetectCommentAsProperty() {
        // Use the comment as property
        CommentManager anotherCommentManager = new PropertyCommentManager();
        DocumentModel commentDocModel = createComment(anotherCommentManager);
        Comment comment = anotherCommentManager.getComment(session, commentDocModel.getId());
        assertNotNull(comment);

        // Get the document being commented
        DocumentModel docModelCommented = session.query(String.format("Select * From Document where %s = '%s'", NXQL.ECM_NAME, "anyFile")).get(0);

        BridgeCommentManager bridgeCommentManager = (BridgeCommentManager) commentManager;
        assertTrue(bridgeCommentManager.isCommentAsProperty(session, commentDocModel));
        assertFalse(bridgeCommentManager.isCommentAsRelation(commentDocModel));
        assertFalse(bridgeCommentManager.isCommentAsRelation(docModelCommented, commentDocModel));
    }

    @Test
    public void shouldDetectCommentAsSecuredTree() {
        // Use the comment as Tree structure and it will be secured
        CommentManager anotherCommentManager = new TreeCommentManager();
        DocumentModel commentDocModel = createComment(anotherCommentManager);
        Comment comment = anotherCommentManager.getComment(session, commentDocModel.getId());
        assertNotNull(comment);

        // Get the document being commented
        DocumentModel docModelCommented = session.query(String.format("Select * From Document where %s = '%s'", NXQL.ECM_NAME, "anyFile")).get(0);

        BridgeCommentManager bridgeCommentManager = (BridgeCommentManager) commentManager;
        assertFalse(bridgeCommentManager.isCommentAsProperty(session, commentDocModel));
        assertFalse(bridgeCommentManager.isCommentAsRelation(commentDocModel));
        assertFalse(bridgeCommentManager.isCommentAsRelation(docModelCommented, commentDocModel));
    }

    @Test
    public void testDeleteCommentAsRelation() {
        // Use the comment as relation
        CommentManager anotherCommentManager = new CommentManagerImpl(newConfig());
        DocumentModel commentDocModel = createComment(anotherCommentManager);
        Comment comment = anotherCommentManager.getComment(session, commentDocModel.getId());
        assertNotNull(comment);

        // Ensure that this comment is correctly created as relation
        CommentService commentComponent = (CommentService) Framework.getRuntime().getComponent(CommentService.NAME);
        RelationManager relationManager = Framework.getService(RelationManager.class);
        CommentServiceConfig config = commentComponent.getConfig();
        if (config != null) {
            Resource commentRes = relationManager.getResource(config.commentNamespace, commentDocModel, null);
            assertNotNull(commentRes);
            Graph graph = relationManager.getGraph(config.graphName, commentDocModel.getCoreSession());
            Resource predicateRes = new ResourceImpl(config.predicateNamespace);
            assertTrue(graph.getObjects(commentRes, predicateRes).stream().findAny().isPresent());
        }

        // Delete this relation comment using the Bridge
        // FIXME see the Team CommentManagerImpl#deleteComment(session, commentId) use PARENT_ID which is not consistent
        // this means that an old comment cannot be deleted with the bridge (10.10) to check
        // It should remove from jena graph like the Migration Service

        // commentManager.deleteComment(session, commentDocModel.getId());
        // comment = anotherCommentManager.getComment(session, commentDocModel.getId());
        // assertNull(comment);
        // if (config != null) {
        // assertNull(relationManager.getResource(config.commentNamespace, commentDocModel, null));
        // }
    }

    @Test
    public void testDeleteCommentAsProperty() {
        // Use the comment as property
        CommentManager anotherCommentManager = new PropertyCommentManager();
        DocumentModel commentDocModel = createComment(anotherCommentManager);
        Comment comment = anotherCommentManager.getComment(session, commentDocModel.getId());
        assertNotNull(comment);
        assertNotNull(comment.getParentId());
        assertNotEquals(0, comment.getParentId().length());

        // Delete this property comment using the Bridge

        commentManager.deleteComment(session, commentDocModel.getId());
        try {
            anotherCommentManager.getComment(session, commentDocModel.getId());
            fail();
        } catch (CommentNotFoundException cfe) {
            assertNotNull(cfe);
            assertNotNull(cfe.getMessage());
        }
    }

    protected DocumentModel createComment(CommentManager commentManager) {
        // Create the file to be commented
        DocumentModel domain = session.createDocumentModel("/", "test-domain", "Domain");
        session.createDocument(domain);
        DocumentModel fileToComment = session.createDocumentModel(domain.getPathAsString(), "anyFile", "File");
        fileToComment = session.createDocument(fileToComment);
        transactionalFeature.nextTransaction();

        // Add a comment
        DocumentModel commentDocModel = session.createDocumentModel(null, "Fake comment", COMMENT_DOC_TYPE);
        boolean setParent = commentManager instanceof PropertyCommentManager;
        // Because we don't use the CommentableDocumentAdapter which will set this property, we should fill it here
        if (setParent) {
            commentDocModel.setPropertyValue(COMMENT_PARENT_ID, fileToComment.getId());
        }

        DocumentModel createdComment = commentManager.createComment(fileToComment, commentDocModel);
        return session.getDocument(new IdRef(createdComment.getId()));
    }

    @Override
    public Class<? extends CommentManager> getType() {
        return BridgeCommentManager.class;
    }
}
