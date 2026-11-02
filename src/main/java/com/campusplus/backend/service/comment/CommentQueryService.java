package com.campusplus.backend.service.comment;
import com.campusplus.backend.dto.PageResponse;
import com.campusplus.backend.dto.comment.*;
import com.campusplus.backend.entity.*;
import com.campusplus.backend.exception.ResourceNotFoundException;
import com.campusplus.backend.repository.*;
import jakarta.validation.constraints.*;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.data.domain.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
@Service @Validated @Transactional(readOnly=true) @PreAuthorize("isAuthenticated()")
public class CommentQueryService {
    private final CommentWorkspace w;
    private final PostLikeRepository likes;
    private final PostCommentLikeRepository commentLikes;
    private final CommentModerationRepository audit;
    public CommentQueryService(CommentWorkspace w,PostLikeRepository likes,PostCommentLikeRepository commentLikes,CommentModerationRepository audit) {
        this.w=w;this.likes=likes;this.commentLikes=commentLikes;this.audit=audit;
    }
    public CommentPage list(@Positive Long postId,@Positive Long rootId,@Size(max=100) String cursor,@Min(1) @Max(100) int size) {
        Post p=w.readable(postId);long actor=w.posts.core.current.userId();var before=CommentCursor.decode(cursor,postId,rootId==null?0:rootId);
        if(rootId!=null){var root=w.comment(rootId);if(!root.getPost().getId().equals(postId)||root.getRoot()!=null||root.hidden())w.policy.missing();}
        var page=PageRequest.of(0,size+1);List<CommentRow> rows;
        if(rootId==null)rows=before==null?w.comments.rootsFirst(postId,actor,p.getUniversity().getId(),page)
            :w.comments.rootsAfter(postId,actor,p.getUniversity().getId(),before.time(),before.id(),page);
        else rows=before==null?w.comments.repliesFirst(postId,rootId,actor,p.getUniversity().getId(),page)
            :w.comments.repliesAfter(postId,rootId,actor,p.getUniversity().getId(),before.time(),before.id(),page);
        boolean more=rows.size()>size;rows=rows.subList(0,Math.min(size,rows.size()));String next=null;
        if(more){var last=rows.getLast();next=new CommentCursor(last.createdAt(),last.id(),postId,rootId==null?0:rootId).encode();}
        return new CommentPage(responses(rows,actor),next,more);
    }
    public CommentResponse get(@Positive Long postId,@Positive Long id) {
        Post p=w.readable(postId);long actor=w.posts.core.current.userId();var row=w.comments.visible(postId,id,actor,p.getUniversity().getId())
            .orElseThrow(()->new ResourceNotFoundException("Yorum bulunamadı."));return responses(List.of(row),actor).getFirst();
    }
    private List<CommentResponse> responses(List<CommentRow> rows,long actor) {
        if(rows.isEmpty())return List.of();var ids=rows.stream().map(CommentRow::id).toList();
        var replies=countMap(w.comments.replyCounts(ids));var counts=countMap(commentLikes.counts(ids));var liked=new HashSet<>(commentLikes.liked(ids,actor));
        return rows.stream().map(c->c.response(replies.getOrDefault(c.id(),0L),counts.getOrDefault(c.id(),0L),liked.contains(c.id()))).toList();
    }
    private Map<Long,Long> countMap(List<SourceCount> values){return values.stream().collect(Collectors.toMap(SourceCount::id,SourceCount::count));}
    public InteractionSummary interactions(@Positive Long id) {
        w.readable(id);long actor=w.posts.core.current.userId();return new InteractionSummary(id,w.comments.visibleCount(id),likes.countByPostIdAndActiveTrue(id),likes.existsByPostIdAndUserIdAndActiveTrue(id,actor));
    }
    public PageResponse<OwnCommentResponse> own(@Min(0) int page,@Min(1) @Max(100) int size) {
        return PageResponse.from(w.comments.own(w.posts.core.current.userId(),page(page,size)).map(OwnCommentResponse::from));
    }
    public OwnCommentResponse ownDetail(@Positive Long id){var c=w.comment(id);w.policy.own(c,w.posts.core.actor());return OwnCommentResponse.from(c);}
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<AdminCommentResponse> managed(@Positive Long postId,CommentStatus status,@Min(0) int page,@Min(1) @Max(100) int size) {
        w.posts.core.policy.requireAdmin(w.posts.core.actor());return PageResponse.from(w.comments.managed(postId,status,page(page,size)).map(AdminCommentResponse::from));
    }
    @PreAuthorize("hasRole('ADMIN')")
    public AdminCommentResponse adminDetail(@Positive Long id) {
        w.posts.core.policy.requireAdmin(w.posts.core.actor());return AdminCommentResponse.from(w.comments.managedDetail(id)
            .orElseThrow(()->new ResourceNotFoundException("Yorum bulunamadı.")));
    }
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<CommentModerationResponse> adminHistory(@Positive Long id,@Min(0) int page,@Min(1) @Max(100) int size) {
        w.posts.core.policy.requireAdmin(w.posts.core.actor());w.comment(id);return PageResponse.from(audit.history(id,null,page(page,size)));
    }
    public PageResponse<CommentModerationResponse> resourceHistory(@Positive Long postId,@Positive Long id,@Min(0) int page,@Min(1) @Max(100) int size) {
        w.resourceManager(w.posts.post(postId),w.posts.core.actor());var c=w.comment(id);if(!c.getPost().getId().equals(postId))w.policy.missing();
        return PageResponse.from(audit.history(id,CommentModerationScope.RESOURCE,page(page,size)));
    }
    private Pageable page(int page,int size){return PageRequest.of(page,size,Sort.by("id").descending());}
}
