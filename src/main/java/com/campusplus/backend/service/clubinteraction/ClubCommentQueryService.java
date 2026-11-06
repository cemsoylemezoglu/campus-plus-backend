package com.campusplus.backend.service.clubinteraction;

import com.campusplus.backend.dto.PageResponse;
import com.campusplus.backend.dto.clubinteraction.*;
import com.campusplus.backend.dto.comment.*;
import com.campusplus.backend.entity.*;
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
public class ClubCommentQueryService {
    private final ClubCommentWorkspace w;
    private final ClubContentLikeRepository likes;
    private final ClubCommentLikeRepository commentLikes;
    private final ClubCommentModerationRepository audit;
    public ClubCommentQueryService(ClubCommentWorkspace w,ClubContentLikeRepository likes,ClubCommentLikeRepository commentLikes,ClubCommentModerationRepository audit) {
        this.w=w;this.likes=likes;this.commentLikes=commentLikes;this.audit=audit;
    }
    public ClubCommentPage list(ClubSourceRef ref,@Positive Long rootId,@Size(max=100) String cursor,@Min(1) @Max(100) int size) {
        var ctx=w.sources.readable(ref);long actor=ctx.actor().getId();
        var after=ClubCommentCursor.decode(cursor,ref,rootId==null?0:rootId);
        if(rootId!=null){var root=w.comment(rootId);w.source(root,ref);if(root.getRoot()!=null||root.hidden())throw ClubCommentWorkspace.missing();}
        var page=PageRequest.of(0,size+1);List<ClubCommentRow> rows;
        if(rootId==null) rows=after==null?w.comments.rootsFirst(ref.eventId(),ref.announcementId(),actor,page)
            :w.comments.rootsAfter(ref.eventId(),ref.announcementId(),actor,after.time(),after.id(),page);
        else rows=after==null?w.comments.repliesFirst(ref.eventId(),ref.announcementId(),rootId,actor,page)
            :w.comments.repliesAfter(ref.eventId(),ref.announcementId(),rootId,actor,after.time(),after.id(),page);
        boolean more=rows.size()>size;rows=rows.subList(0,Math.min(size,rows.size()));String next=null;
        if(more){var last=rows.getLast();next=new ClubCommentCursor(last.createdAt(),last.id(),ref,rootId==null?0:rootId).encode();}
        return new ClubCommentPage(responses(rows,actor),next,more);
    }
    public ClubCommentResponse get(ClubSourceRef ref,@Positive Long id) {
        var ctx=w.sources.readable(ref);long actor=ctx.actor().getId();
        var row=w.comments.visible(ref.eventId(),ref.announcementId(),id,actor).orElseThrow(ClubCommentWorkspace::missing);
        return responses(List.of(row),actor).getFirst();
    }
    private List<ClubCommentResponse> responses(List<ClubCommentRow> rows,long actor) {
        if(rows.isEmpty())return List.of();var ids=rows.stream().map(ClubCommentRow::id).toList();
        var replies=countMap(w.comments.replyCounts(ids));var counts=countMap(commentLikes.counts(ids));
        var liked=new HashSet<>(commentLikes.liked(ids,actor));
        return rows.stream().map(row->row.response(replies.getOrDefault(row.id(),0L),counts.getOrDefault(row.id(),0L),liked.contains(row.id()))).toList();
    }
    private Map<Long,Long> countMap(List<SourceCount> rows){return rows.stream().collect(Collectors.toMap(SourceCount::id,SourceCount::count));}
    public ClubInteractionSummary interactions(ClubSourceRef ref) {
        var ctx=w.sources.readable(ref);long actor=ctx.actor().getId();
        boolean liked=likes.own(ref.eventId(),ref.announcementId(),actor).map(ClubContentLike::isActive).orElse(false);
        return new ClubInteractionSummary(ref,w.comments.visibleCount(ref.eventId(),ref.announcementId(),actor),likes.activeCount(ref.eventId(),ref.announcementId()),liked);
    }
    public PageResponse<OwnClubCommentResponse> own(@Min(0) int page,@Min(1) @Max(100) int size) {
        return PageResponse.from(w.comments.own(w.core.current.userId(),page(page,size)).map(OwnClubCommentResponse::from));
    }
    public OwnClubCommentResponse ownDetail(@Positive Long id) {
        var c=w.comment(id);w.own(c,w.core.actor());return OwnClubCommentResponse.from(c);
    }
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<AdminClubCommentResponse> managed(CommentStatus status,@Min(0) int page,@Min(1) @Max(100) int size) {
        w.core.policy.requireAdmin(w.core.actor());return PageResponse.from(w.comments.managed(status,page(page,size)).map(AdminClubCommentResponse::from));
    }
    @PreAuthorize("hasRole('ADMIN')")
    public AdminClubCommentResponse adminDetail(@Positive Long id) {
        w.core.policy.requireAdmin(w.core.actor());return AdminClubCommentResponse.from(w.comments.managedDetail(id).orElseThrow(ClubCommentWorkspace::missing));
    }
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<CommentModerationResponse> adminHistory(@Positive Long id,@Min(0) int page,@Min(1) @Max(100) int size) {
        w.core.policy.requireAdmin(w.core.actor());w.comment(id);return PageResponse.from(audit.history(id,null,page(page,size)));
    }
    public PageResponse<CommentModerationResponse> resourceHistory(ClubSourceRef ref,@Positive Long id,@Min(0) int page,@Min(1) @Max(100) int size) {
        w.sources.readableManager(ref);
        var c=w.comment(id);w.source(c,ref);
        return PageResponse.from(audit.history(id,CommentModerationScope.RESOURCE,page(page,size)));
    }
    private Pageable page(int page,int size){return PageRequest.of(page,size,Sort.by("id").descending());}
}
