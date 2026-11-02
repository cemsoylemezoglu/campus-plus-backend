package com.campusplus.backend.service.comment;
import com.campusplus.backend.entity.*;
import com.campusplus.backend.exception.*;
import com.campusplus.backend.repository.PostCommentRepository;
import com.campusplus.backend.security.CommentPolicy;
import com.campusplus.backend.service.post.PostWorkspace;
import jakarta.persistence.*;
import org.springframework.stereotype.Component;
@Component
public class CommentWorkspace {
    public final PostWorkspace posts;
    public final PostCommentRepository comments;
    public final CommentPolicy policy;
    private final EntityManager em;
    public CommentWorkspace(PostWorkspace posts,PostCommentRepository comments,CommentPolicy policy,EntityManager em){this.posts=posts;this.comments=comments;this.policy=policy;this.em=em;}
    public PostComment comment(Long id){return comments.findById(id).orElseThrow(()->new ResourceNotFoundException("Yorum bulunamadı."));}
    public void visiblePost(Post post,User actor) {
        if(!posts.policy.visible(post,actor,posts.core.profiles.findById(actor.getId()).orElse(null),
            post.getClub()==null?null:posts.core.local(post.getClub(),actor.getId())))posts.policy.missing();
    }
    public Post readable(Long id){Post post=posts.post(id);visiblePost(post,posts.core.actor());return post;}
    public PostWorkspace.Locked participant(Long id) {
        readable(id);var ctx=posts.lockInteraction(id);visiblePost(ctx.post(),ctx.actor());posts.policy.requireStudent(ctx.actor());return ctx;
    }
    public void resourceManager(Post p,User actor){visiblePost(p,actor);policy.resourceManager(p,actor,p.getClub()==null?null:posts.core.local(p.getClub(),actor.getId()));}
    public PostComment lock(Long id,Long postId) {
        PostComment preview=comment(id);if(!preview.getPost().getId().equals(postId))policy.missing();
        PostComment root=preview.getRoot();
        if(root!=null)locked(root.getId(),postId);
        return locked(id,postId);
    }
    private PostComment locked(Long id,Long postId) {
        PostComment c=comments.lock(id,postId).orElseThrow(()->new ResourceNotFoundException("Yorum bulunamadı."));
        em.refresh(c,LockModeType.PESSIMISTIC_WRITE);return c;
    }
}
