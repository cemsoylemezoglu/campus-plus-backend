package com.campusplus.backend.service.event;

import com.campusplus.backend.dto.PageResponse;
import com.campusplus.backend.dto.event.*;
import com.campusplus.backend.entity.*;
import com.campusplus.backend.exception.InvalidRequestException;
import com.campusplus.backend.service.club.ClubWorkspace;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.data.domain.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service @Validated @Transactional(readOnly=true) @PreAuthorize("isAuthenticated()")
public class EventQueryService {
    private final EventWorkspace w;
    public EventQueryService(EventWorkspace w){this.w=w;}
    public PageResponse<EventSummary> discover(@Positive Long clubId,@Positive Long universityId,Instant from,Instant to,
            @Size(max=200) String query,@Min(0) int page,@Min(1) @Max(100) int size) {
        if(from!=null&&to!=null&&!from.isBefore(to)) throw new InvalidRequestException("Tarih filtresi aralığı geçersiz.");
        User actor=w.core.actor();
        return summaries(w.events.discover(clubId,universityId,from,to,ClubWorkspace.search(query),w.core.policy.admin(actor),actor.getId(),
                PageRequest.of(page,size,Sort.by("startsAt").ascending().and(Sort.by("id").ascending()))));
    }
    public EventResponse get(@Positive Long id) {
        Event e=w.event(id);User actor=w.core.actor();w.policy.requireVisible(e,actor,w.core.local(e.getClub(),actor.getId()));return w.response(e);
    }
    public PageResponse<EventSummary> managed(@Positive Long clubId,EventStatus status,@Min(0) int page,@Min(1) @Max(100) int size) {
        w.preflightManager(clubId);return summaries(w.events.managed(clubId,status,PageRequest.of(page,size,Sort.by("id").descending())));
    }
    public PageResponse<MyEventParticipationResponse> own(EventParticipationStatus status,@Min(0) int page,@Min(1) @Max(100) int size) {
        return PageResponse.from(w.participants.own(w.core.current.userId(),status,PageRequest.of(page,size,Sort.by("id").descending()))
                .map(MyEventParticipationResponse::from));
    }
    public PageResponse<ManagedEventParticipationResponse> participants(@Positive Long clubId,@Positive Long id,EventParticipationStatus status,
            @Min(0) int page,@Min(1) @Max(100) int size) {
        w.preflightManager(clubId);w.eventOfClub(id,clubId);
        return PageResponse.from(w.participants.managed(id,status,PageRequest.of(page,size,Sort.by("id").ascending())));
    }
    private PageResponse<EventSummary> summaries(Page<Event> page) {
        Map<Long,Long> counts=page.isEmpty()?Map.of():w.participants.counts(page.getContent().stream().map(Event::getId).toList(),EventWorkspace.OCCUPIED)
                .stream().collect(Collectors.toMap(x -> x.getEventId(),x -> x.getAmount()));
        Instant now=w.core.clock.instant();return PageResponse.from(page.map(e -> w.summary(e,counts.getOrDefault(e.getId(),0L),now)));
    }
}
