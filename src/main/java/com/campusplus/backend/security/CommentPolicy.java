package com.campusplus.backend.security;
import com.campusplus.backend.entity.*;
import com.campusplus.backend.exception.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
@Component
public class CommentPolicy {
    private final ClubPolicy clubs;
    public CommentPolicy(ClubPolicy clubs){this.clubs=clubs;}
    public void own(PostComment c,User actor){if(!c.getAuthor().getId().equals(actor.getId()))missing();}
    public void visible(PostComment c){if(!c.visibleContent())missing();}
    public void root(PostComment c){visible(c);if(c.getRoot()!=null)throw new ResourceConflictException("Yanıt yalnız kök yoruma eklenebilir.");}
    public void resourceManager(Post p,User actor,ClubMembership membership) {
        boolean own=p.getPublisher()==PostPublisher.STUDENT&&p.getCreatedBy().getId().equals(actor.getId())&&actor.getRoles().contains(Role.STUDENT);
        boolean local=p.getClub()!=null&&membership!=null&&membership.getClub().getId().equals(p.getClub().getId())&&clubs.manager(actor,membership);
        if(!own&&!local)throw new AccessDeniedException("Yorum yönetimi için yetkiniz bulunmuyor.");
    }
    public void missing(){throw new ResourceNotFoundException("Yorum bulunamadı.");}
}
