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
 *     Nuno Cunha <ncunha@nuxeo.com>
 */

package org.nuxeo.ecm.platform.comment.impl;

import static org.nuxeo.ecm.platform.comment.workflow.utils.CommentsConstants.COMMENT_PARENT_ID;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.PartialList;
import org.nuxeo.ecm.platform.comment.api.Comment;
import org.nuxeo.ecm.platform.comment.api.CommentManager;
import org.nuxeo.ecm.platform.comment.api.exceptions.CommentNotFoundException;
import org.nuxeo.ecm.platform.comment.api.exceptions.CommentSecurityException;
import org.nuxeo.ecm.platform.comment.service.CommentService;
import org.nuxeo.ecm.platform.comment.service.CommentServiceConfig;
import org.nuxeo.ecm.platform.relations.api.Graph;
import org.nuxeo.ecm.platform.relations.api.RelationManager;
import org.nuxeo.ecm.platform.relations.api.Resource;
import org.nuxeo.ecm.platform.relations.api.impl.ResourceImpl;
import org.nuxeo.ecm.platform.relations.api.impl.StatementImpl;
import org.nuxeo.runtime.api.Framework;

/**
 * @since 10.3
 */
public class BridgeCommentManager extends AbstractCommentManager {

    protected final CommentManager first;

    protected final CommentManager second;

    /**
     * @since 11.1
     */
    protected final CommentManager third;

    public BridgeCommentManager(CommentManager first, CommentManager second) {
        this(first, second, null);
    }

    /**
     * @since 11.1
     */
    public BridgeCommentManager(CommentManager first, CommentManager second, CommentManager third) {
        this.first = first;
        this.second = second;
        this.third = third;
    }

    @Override
    public List<DocumentModel> getComments(DocumentModel docModel) {
        List<DocumentModel> documentModels = new ArrayList<>();
        documentModels.addAll(first.getComments(docModel));
        documentModels.addAll(second.getComments(docModel));
        documentModels.addAll(third.getComments(docModel));

        return documentModels.stream().distinct().collect(Collectors.toList());
    }

    @Override
    public List<DocumentModel> getComments(CoreSession session, DocumentModel docModel)
            throws CommentSecurityException {
        List<DocumentModel> documentModels = new ArrayList<>();
        documentModels.addAll(first.getComments(session, docModel));
        documentModels.addAll(second.getComments(session, docModel));
        documentModels.addAll(third.getComments(session, docModel));

        return documentModels.stream().distinct().collect(Collectors.toList());
    }

    @Override
    public DocumentModel createComment(DocumentModel docModel, String comment) {
        return third.createComment(docModel, comment);
    }

    @Override
    public DocumentModel createComment(DocumentModel docModel, String comment, String author) {
        return third.createComment(docModel, comment, author);
    }

    @Override
    public DocumentModel createComment(DocumentModel docModel, DocumentModel comment) throws CommentSecurityException {
        return third.createComment(docModel, comment);
    }

    @Override
    public DocumentModel createComment(DocumentModel docModel, DocumentModel parent, DocumentModel child) {
        return third.createComment(docModel, parent, child);
    }

    @Override
    public void deleteComment(DocumentModel docModel, DocumentModel comment) {
        if (isCommentAsProperty(docModel.getCoreSession(), comment)) {
            second.deleteComment(docModel, comment);
        } else if (isCommentAsRelation(docModel, comment)) {
            first.deleteComment(docModel, comment);
        } else {
            third.deleteComment(docModel, comment);
        }
    }

    @Override
    public List<DocumentModel> getDocumentsForComment(DocumentModel comment) {
        List<DocumentModel> documentModels = new ArrayList<>();
        documentModels.addAll(first.getDocumentsForComment(comment));
        documentModels.addAll(second.getDocumentsForComment(comment));
        documentModels.addAll(third.getDocumentsForComment(comment));

        return documentModels.stream().distinct().collect(Collectors.toList());
    }

    @Override
    public DocumentModel getThreadForComment(DocumentModel comment) throws CommentSecurityException {
        if (isCommentAsProperty(comment.getCoreSession(), comment)) {
            return second.getThreadForComment(comment);
        } else if (isCommentAsRelation(comment)) {
            return first.getThreadForComment(comment);
        }
        return third.getThreadForComment(comment);
    }

    @Override
    public DocumentModel createLocatedComment(DocumentModel docModel, DocumentModel comment, String path)
            throws CommentSecurityException {
        return third.createLocatedComment(docModel, comment, path);
    }

    @Override
    public Comment createComment(CoreSession session, Comment comment)
            throws CommentNotFoundException, CommentSecurityException {
        return third.createComment(session, comment);
    }

    @Override
    public Comment getComment(CoreSession session, String commentId)
            throws CommentNotFoundException, CommentSecurityException {
        return third.getComment(session, commentId);
    }

    @Override
    public List<Comment> getComments(CoreSession session, String documentId) {
        List<Comment> comments = new ArrayList<>();
        comments.addAll(first.getComments(session, documentId));
        comments.addAll(second.getComments(session, documentId));
        comments.addAll(third.getComments(session, documentId));

        return comments.stream().distinct().collect(Collectors.toList());
    }

    @Override
    public PartialList<Comment> getComments(CoreSession session, String documentId, Long pageSize,
            Long currentPageIndex, boolean sortAscending) throws CommentSecurityException {
        List<Comment> comments = new ArrayList<>();
        comments.addAll(first.getComments(session, documentId, pageSize, currentPageIndex, sortAscending));
        comments.addAll(second.getComments(session, documentId, pageSize, currentPageIndex, sortAscending));
        comments.addAll(third.getComments(session, documentId, pageSize, currentPageIndex, sortAscending));

        List<Comment> distinctComments = comments.stream().distinct().collect(Collectors.toList());
        return new PartialList<>(distinctComments, distinctComments.size());
    }

    @Override
    public Comment updateComment(CoreSession session, String commentId, Comment comment)
            throws CommentNotFoundException, CommentSecurityException {
        DocumentRef commentRef = new IdRef(commentId);
        if (!session.exists(commentRef)) {
            throw new CommentNotFoundException("The comment " + commentId + " does not exist");
        }
        if (isCommentAsProperty(session, session.getDocument(commentRef))) {
            return second.updateComment(session, commentId, comment);
        } else if (isCommentAsRelation(session.getDocument(commentRef))) {
            return first.updateComment(session, commentId, comment);
        }
        return third.updateComment(session, commentId, comment);
    }

    @Override
    public void deleteComment(CoreSession session, String commentId)
            throws CommentNotFoundException, CommentSecurityException {
        DocumentRef commentRef = new IdRef(commentId);
        if (!session.exists(commentRef)) {
            throw new CommentNotFoundException("The comment " + commentId + " does not exist");
        }
        if (isCommentAsProperty(session, session.getDocument(commentRef))) {
            second.deleteComment(session, commentId);
        } else if (isCommentAsRelation(session.getDocument(commentRef))) {
            first.deleteComment(session, commentId);
        } else {
            third.deleteComment(session, commentId);
        }
    }

    @Override
    public Comment getExternalComment(CoreSession session, String entityId)
            throws CommentNotFoundException, CommentSecurityException {
        return third.getExternalComment(session, entityId);
    }

    @Override
    public Comment updateExternalComment(CoreSession session, String entityId, Comment comment)
            throws CommentNotFoundException, CommentSecurityException {
        return third.updateExternalComment(session, entityId, comment);
    }

    @Override
    public void deleteExternalComment(CoreSession session, String entityId)
            throws CommentNotFoundException, CommentSecurityException {
        third.deleteExternalComment(session, entityId);
    }

    @Override
    public boolean hasFeature(Feature feature) {
        switch (feature) {
        case COMMENTS_LINKED_WITH_PROPERTY:
            return false;
        default:
            throw new UnsupportedOperationException(feature.name());
        }
    }

    @Override
    public DocumentRef getAncestorRef(CoreSession session, DocumentRef commentIdRef) {
        DocumentModel commentModel = session.getDocument(commentIdRef);
        if (isCommentAsProperty(session, commentModel)) {
            return second.getAncestorRef(session, commentIdRef);
        } else if (isCommentAsRelation(commentModel)) {
            return first.getAncestorRef(session, commentIdRef);
        }
        return third.getAncestorRef(session, commentIdRef);
    }

    @Override
    public String getLocationOfCommentCreation(CoreSession session, DocumentModel documentModel) {
        return third.getLocationOfCommentCreation(session, documentModel);
    }

    /**
     * @since 11.1
     * @return true if the given {@code comment} is stored as a relation, otherwise false
     */
    public boolean isCommentAsRelation(DocumentModel docModel, DocumentModel commentModel) {
        CommentService commentComponent = (CommentService) Framework.getRuntime().getComponent(CommentService.NAME);
        RelationManager relationManager = Framework.getService(RelationManager.class);
        CommentServiceConfig config = commentComponent.getConfig();
        if (config != null) {
            Resource commentRes = relationManager.getResource(config.commentNamespace, commentModel, null);
            Resource documentRes = relationManager.getResource(config.documentNamespace, docModel, null);
            if (commentRes != null && documentRes != null) {
                Resource predicateRes = new ResourceImpl(config.predicateNamespace);
                Graph graph = relationManager.getGraph(config.graphName, docModel.getCoreSession());
                return graph.hasStatement(new StatementImpl(commentRes, predicateRes, documentRes));
            }
        }

        return false;
    }

    /**
     * @since 11.1
     * @return true if the given {@code comment} is stored as a relation, otherwise false
     */
    public boolean isCommentAsRelation(DocumentModel commentModel) {
        CommentService commentComponent = (CommentService) Framework.getRuntime().getComponent(CommentService.NAME);
        RelationManager relationManager = Framework.getService(RelationManager.class);
        CommentServiceConfig config = commentComponent.getConfig();
        if (config != null) {
            Resource commentRes = relationManager.getResource(config.commentNamespace, commentModel, null);
            if (commentRes != null) {
                Graph graph = relationManager.getGraph(config.graphName, commentModel.getCoreSession());
                Resource predicateRes = new ResourceImpl(config.predicateNamespace);
                return graph.getObjects(commentRes, predicateRes).stream().findAny().isPresent();
            }
        }

        return false;
    }

    public boolean isCommentAsProperty(CoreSession session, DocumentModel documentModel) {
        // For Backward compatibility we should keep COMMENT_PARENT_ID and COMMENT_ANCESTOR_IDS filled as its done in
        // PropertyManager.
        // To be able to detect which comment manager we should use, we rely on comment:parentId and ecm:parentId
        // CommentManagerImpl: comments are under HiddenFolder but there COMMENT_PARENT_ID is not filled
        // PropertyManager: comments are under HiddenFolder but her COMMENT_PARENT_ID is filled
        // TreeManager: comments are under CommentRoot or the comment that they reply, COMMENT_PARENT_ID is filled
        // (Backward compatibility to 10.10)
        return documentModel.getPropertyValue(COMMENT_PARENT_ID) != null
                && session.getDocument(documentModel.getParentRef()).getType().equals("HiddenFolder");
    }

}
