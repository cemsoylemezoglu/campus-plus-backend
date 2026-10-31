package com.campusplus.backend.event;

import com.campusplus.backend.dto.event.*;
import jakarta.validation.*;
import java.time.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

class EventValidationTest {
    static ValidatorFactory factory;
    static Validator validator;
    final JsonMapper json=JsonMapper.builder().build();
    static final OffsetDateTime OPEN=OffsetDateTime.parse("2030-01-01T12:00:00Z");
    @BeforeAll static void start(){factory=Validation.buildDefaultValidatorFactory();validator=factory.getValidator();}
    @AfterAll static void close(){factory.close();}
    private CreateEventRequest input(Integer capacity){return new CreateEventRequest("Title","Description","Venue",null,null,capacity,
        OPEN,OPEN.plusHours(1),OPEN.plusHours(2),OPEN.plusHours(3),"Europe/Istanbul");}
    @ParameterizedTest @NullSource @ValueSource(ints={1,100000})
    void unlimitedAndSupportedCapacityLimitsAreValid(Integer capacity){assertTrue(validator.validate(input(capacity)).isEmpty());}
    @ParameterizedTest @ValueSource(ints={-1,0,100001})
    void capacityOutsideSupportedLimitsIsRejected(Integer capacity){assertFalse(validator.validate(input(capacity)).isEmpty());}
    @Test void defaultAudienceAndWhitespaceAreCanonical(){
        var request=new CreateEventRequest(" Title "," Description "," Venue "," ",null,null,OPEN,OPEN.plusHours(1),OPEN.plusHours(2),OPEN.plusHours(3)," UTC ");
        assertEquals("Title",request.title());assertNull(request.coverUrl());assertEquals("UTC",request.timeZone());
        assertEquals(com.campusplus.backend.entity.EventAudience.UNIVERSITY_STUDENTS,request.audience());
    }
    @Test void equalClosingAndStartIsValidButReversedOrEqualOpeningIsNot(){
        assertTrue(EventInput.schedule(OPEN,OPEN.plusHours(1),OPEN.plusHours(1),OPEN.plusHours(2),"UTC"));
        assertFalse(EventInput.schedule(OPEN,OPEN,OPEN.plusHours(1),OPEN.plusHours(2),"UTC"));
        assertFalse(EventInput.schedule(OPEN,OPEN.plusHours(2),OPEN.plusHours(1),OPEN.plusHours(3),"UTC"));
    }
    @Test void utcAndOffsetTransportRepresentTheSameInstant(){
        assertEquals(OffsetDateTime.parse("2030-01-01T15:00:00+03:00").toInstant(),OPEN.toInstant());
        var request=json.readValue("{\"version\":0,\"registrationOpensAt\":\"2030-01-01T15:00:00+03:00\","
            +"\"registrationClosesAt\":\"2030-01-01T13:00:00Z\",\"startsAt\":\"2030-01-01T14:00:00Z\","
            +"\"endsAt\":\"2030-01-01T15:00:00Z\",\"timeZone\":\"Europe/Istanbul\"}",RescheduleEventRequest.class);
        assertTrue(validator.validate(request).isEmpty());assertEquals(OPEN.toInstant(),request.registrationOpensAt().toInstant());
    }
    @Test void dstOverlapOffsetsIdentifyDistinctInstants(){
        var early=OffsetDateTime.parse("2030-10-27T02:30:00+02:00");
        var late=OffsetDateTime.parse("2030-10-27T02:30:00+01:00");
        assertEquals(Duration.ofHours(1),Duration.between(early.toInstant(),late.toInstant()));
        assertFalse(EventInput.schedule(late,early,late.plusHours(1),late.plusHours(2),"Europe/Paris"));
    }
    @Test void localTimestampWithoutOffsetIsNotSilentlyInterpreted(){
        assertThrows(Exception.class,() -> json.readValue("{\"version\":0,\"startsAt\":\"2030-01-01T12:00:00\"}",RescheduleEventRequest.class));
    }
    @ParameterizedTest @ValueSource(strings={"","Unknown/City","+03:00"})
    void invalidDisplayZoneIsRejected(String zone){assertFalse(EventInput.schedule(OPEN,OPEN.plusHours(1),OPEN.plusHours(2),OPEN.plusHours(3),zone));}
    @Test void inputBoundsAreNotSilentlyTruncated(){
        var request=new CreateEventRequest("x".repeat(201),"y".repeat(10001),"z".repeat(501),null,null,null,
            OPEN,OPEN.plusHours(1),OPEN.plusHours(2),OPEN.plusHours(3),"UTC");
        assertEquals(3,validator.validate(request).size());
    }
    @Test void emptyOrConflictingPatchesAndMissingVersionsAreRejected(){
        assertFalse(validator.validate(new UpdateEventRequest(null,null,null,null,null,null,null,0L)).isEmpty());
        assertFalse(validator.validate(new UpdateEventRequest("Title",null,null,null,null,1,true,0L)).isEmpty());
        assertFalse(validator.validate(new UpdateEventRequest("Title",null,null,null,null,null,false,0L)).isEmpty());
        assertFalse(validator.validate(new UpdateEventRequest("Title",null,null,null,null,null,null,null)).isEmpty());
        assertTrue(validator.validate(new UpdateEventRequest(null,null,null,null,null,null,true,0L)).isEmpty());
    }
}
