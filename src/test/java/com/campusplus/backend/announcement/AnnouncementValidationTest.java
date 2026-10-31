package com.campusplus.backend.announcement;

import com.campusplus.backend.dto.announcement.*;
import com.campusplus.backend.entity.AnnouncementAudience;
import com.campusplus.backend.entity.AnnouncementStatus;
import java.time.Instant;
import jakarta.validation.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class AnnouncementValidationTest {
    static ValidatorFactory factory;
    static Validator validator;
    @BeforeAll static void start(){factory=Validation.buildDefaultValidatorFactory();validator=factory.getValidator();}
    @AfterAll static void stop(){factory.close();}
    boolean valid(Object request){return validator.validate(request).isEmpty();}
    @Test void exactTextLimitsAreAcceptedAndDefaultAudienceIsUniversity() {
        var r=new CreateAnnouncementRequest("x".repeat(200),"x".repeat(10000),null,null);
        assertTrue(valid(r));assertEquals(AnnouncementAudience.UNIVERSITY_STUDENTS,r.audience());
    }
    @Test void overlongTitleAndBodyAreRejected() {
        assertFalse(valid(new CreateAnnouncementRequest("x".repeat(201),"Body",null,null)));
        assertFalse(valid(new CreateAnnouncementRequest("Title","x".repeat(10001),null,null)));
    }
    @ParameterizedTest @ValueSource(strings={"", " ", "\t\n", "\u2003"})
    void whitespaceOnlyTitleOrBodyIsRejected(String text) {
        assertFalse(valid(new CreateAnnouncementRequest(text,"Body",null,null)));
        assertFalse(valid(new CreateAnnouncementRequest("Title",text,null,null)));
    }
    @Test void eventAndVersionIdentifiersAreValidated() {
        assertFalse(valid(new CreateAnnouncementRequest("Title","Body",null,0L)));
        assertFalse(valid(new UpdateAnnouncementRequest(null,"Title",null,null,null,null)));
        assertFalse(valid(new AnnouncementActionRequest(-1L)));
        assertFalse(valid(new AnnouncementActionRequest(null)));
    }
    @Test void clearEventHasExplicitPatchSemantics() {
        assertTrue(valid(new UpdateAnnouncementRequest(0L,null,null,null,null,true)));
        assertFalse(valid(new UpdateAnnouncementRequest(0L,null,null,null,null,false)));
        assertFalse(valid(new UpdateAnnouncementRequest(0L,null,null,null,1L,true)));
        assertTrue(valid(new UpdateAnnouncementRequest(0L,null,null,AnnouncementAudience.CLUB_MEMBERS,null,null)));
    }
    @Test void optionalPatchFieldsCannotHideInvalidContentOrOversizedBodies() {
        assertFalse(valid(new UpdateAnnouncementRequest(0L," ",null,null,null,null)));
        assertFalse(valid(new UpdateAnnouncementRequest(0L,null,"x".repeat(10001),null,null,null)));
        assertFalse(valid(new UpdateAnnouncementRequest(0L,null,null,null,-1L,null)));
    }
    @Test void withdrawalReasonIsRequiredBoundedAndNormalized() {
        assertFalse(valid(new WithdrawAnnouncementRequest(0L," ")));
        assertFalse(valid(new WithdrawAnnouncementRequest(0L,"x".repeat(1001))));
        var r=new WithdrawAnnouncementRequest(0L,"  Correction  ");assertTrue(valid(r));assertEquals("Correction",r.reason());
    }
    @Test void diagnosticStringsDoNotExposePrivateContent() {
        var club=new AnnouncementClubSummary(1L,"Private Club",2L,"University");
        var summary=new AnnouncementSummary(3L,club,"Private Title",AnnouncementAudience.CLUB_MEMBERS,null,Instant.EPOCH,0L);
        Object[] records={new CreateAnnouncementRequest("Private Title","Private Body",null,null),
            new UpdateAnnouncementRequest(0L,null,"Private Body",null,null,null),new WithdrawAnnouncementRequest(0L,"Private Reason"),
            club,summary,new AnnouncementResponse(summary,"Private Body",null),
            new ManagedAnnouncementResponse(summary,AnnouncementStatus.WITHDRAWN,null,"Private Body",null,Instant.EPOCH,Instant.EPOCH,"Private Reason")};
        for(Object record:records)assertFalse(record.toString().contains("Private"),record.getClass().getSimpleName());
    }
}
