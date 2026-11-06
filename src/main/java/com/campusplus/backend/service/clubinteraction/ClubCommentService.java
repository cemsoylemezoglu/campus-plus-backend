package com.campusplus.backend.service.clubinteraction;
import com.campusplus.backend.dto.comment.*;
import com.campusplus.backend.dto.clubinteraction.*;
import com.campusplus.backend.dto.post.PostActionRequest;
import com.campusplus.backend.entity.*;
import com.campusplus.backend.exception.ResourceConflictException;
import com.campusplus.backend.service.club.ClubWorkspace;
import com.campusplus.backend.service.notification.ClubCommentNotificationWriter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
@Service @Validated @Transactional @PreAuthorize("isAuthenticated()")
public class ClubCommentService {
    private final ClubCommentWorkspace w;
    private final ClubCommentNotificationWriter notifications;
    public ClubCommentService(ClubCommentWorkspace w,ClubCommentNotificationWriter notifications){this.w=w;this.notifications=notifications;}
    public OwnClubCommentResponse create(ClubSourceRef ref,@Positive Long rootId,@Valid CreateCommentRequest request) {
        var ctx=w.sources.participant(ref);ClubComment root=rootId==null?null:w.lock(rootId,ref);
        if(root!=null)w.root(root);
        ClubComment c=w.comments.saveAndFlush(new ClubComment(ctx.source().event(),ctx.source().announcement(),ctx.actor(),root,request.body(),w.core.clock.instant()));
        notifications.created(c,ctx.actor());return OwnClubCommentResponse.from(c);
    }
    public OwnClubCommentResponse update(ClubSourceRef ref,@Positive Long id,@Valid UpdateCommentRequest request) {
        var preview=w.comment(id);w.source(preview,ref);w.own(preview,w.core.actor());
        var ctx=w.sources.participant(ref);var c=w.lock(id,ref);w.own(c,ctx.actor());w.visible(c);
        ClubWorkspace.version(c.getVersion(),request.version());c.edit(request.body(),w.core.clock.instant());
        return OwnClubCommentResponse.from(w.comments.saveAndFlush(c));
    }
    public OwnClubCommentResponse remove(@Positive Long id,@Valid PostActionRequest request) {
        ClubComment preview=w.comment(id);w.own(preview,w.core.actor());
        var ctx=w.sources.cleanup(preview.source());var c=w.lock(id,ctx.source().ref());w.own(c,ctx.actor());
        ClubWorkspace.version(c.getVersion(),request.version());
        if(c.getStatus()==CommentStatus.REMOVED)throw new ResourceConflictException("Yorum zaten kaldırılmış.");
        c.remove(w.core.clock.instant());return OwnClubCommentResponse.from(w.comments.saveAndFlush(c));
    }
}
