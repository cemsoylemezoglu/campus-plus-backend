package com.campusplus.backend.comment;
import com.campusplus.backend.dto.comment.*;
import com.campusplus.backend.entity.*;
import com.campusplus.backend.exception.InvalidRequestException;
import com.campusplus.backend.service.comment.CommentCursor;
import com.campusplus.backend.service.notification.CommentNotificationWriter;
import jakarta.validation.Validation;
import java.nio.ByteBuffer;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CommentInputAndWriterTest {
    static final Instant NOW=Instant.parse("2030-01-01T12:00:00Z");
    @ParameterizedTest @ValueSource(longs={1,42,Long.MAX_VALUE}) void cursorPreservesNanosecondsAndBothSources(long id){
        var c=new CommentCursor(Instant.parse("2030-01-01T01:02:03.123456Z"),id,51,100);assertEquals(c,CommentCursor.decode(c.encode(),51,100));
        assertThrows(InvalidRequestException.class,()->CommentCursor.decode(c.encode(),53,100));assertThrows(InvalidRequestException.class,()->CommentCursor.decode(c.encode(),51,0));
    }
    @ParameterizedTest @ValueSource(strings={""," ","x","a/b","=","broken"}) void invalidCursorIs400(String value){assertThrows(InvalidRequestException.class,()->CommentCursor.decode(value,51,0));}
    @Test void corruptFieldsNeverReachJdbc(){
        for(long[] row:new long[][]{{2,0,0,1,51,0},{1,0,-1,1,51,0},{1,0,1000000000,1,51,0},{1,0,0,0,51,0},{1,Long.MAX_VALUE,0,1,51,0},{1,0,0,1,51,-1}}){
            String value=Base64.getUrlEncoder().withoutPadding().encodeToString(ByteBuffer.allocate(37).put((byte)row[0]).putLong(row[1]).putInt((int)row[2]).putLong(row[3]).putLong(row[4]).putLong(row[5]).array());
            assertThrows(InvalidRequestException.class,()->CommentCursor.decode(value,51,0));
        }
        assertNull(CommentCursor.decode(null,51,0));
    }
    @Test void contentBoundsAndLogRedaction(){
        try(var factory=Validation.buildDefaultValidatorFactory()){
            var v=factory.getValidator();assertTrue(v.validate(new CreateCommentRequest("x".repeat(3000))).isEmpty());
            assertFalse(v.validate(new CreateCommentRequest("x".repeat(3001))).isEmpty());assertFalse(v.validate(new CreateCommentRequest(" \t\n")).isEmpty());
            assertFalse(v.validate(new UpdateCommentRequest(null,"text")).isEmpty());
        }
        assertEquals("text",new CreateCommentRequest(" text ").body());
        assertFalse(new CreateCommentRequest("PRIVATE_SENTINEL").toString().contains("PRIVATE_SENTINEL"));
        assertFalse(new UpdateCommentRequest(0L,"PRIVATE_SENTINEL").toString().contains("PRIVATE_SENTINEL"));
    }
    @Test void removedProjectionNeverReturnsRetainedTextOrAuthor(){
        var row=new CommentRow(1L,51L,null,2L,"Private name","Private body",CommentStatus.REMOVED,false,false,NOW,NOW,NOW,1L);
        var response=row.response(2,10,true);assertNull(response.body());assertNull(response.author());assertEquals(0,response.likeCount());assertFalse(response.likedByMe());assertEquals(2,response.replyCount());
    }
    void field(Object object,String name,Object value){ReflectionTestUtils.setField(object,name,value);}
    @Test void writerParametersMatchPlaceholdersAndDedupeStableLikeIdentity(){
        var jdbc=mock(JdbcTemplate.class);var writer=new CommentNotificationWriter(jdbc,Clock.fixed(NOW,ZoneOffset.UTC));
        var actor=new User("Actor","actor@example.test");field(actor,"id",4L);var university=new University("UNI","University","City","TR",null,NOW);field(university,"id",1L);
        var post=new Post(university,actor,null,"Body",PostAudience.UNIVERSITY_STUDENTS,NOW);field(post,"id",51L);
        var comment=new PostComment(post,actor,null,"Comment",NOW);field(comment,"id",100L);writer.created(comment,actor);
        var like=new PostLike(post,actor,NOW);field(like,"id",200L);writer.postLiked(like,actor);
        var commentLike=new PostCommentLike(comment,actor,NOW);field(commentLike,"id",201L);writer.commentLiked(commentLike,actor);
        var calls=mockingDetails(jdbc).getInvocations().stream().filter(i->i.getMethod().getName().equals("update")).toList();assertEquals(3,calls.size());
        for(var call:calls){String sql=(String)call.getRawArguments()[0];Object[] args=(Object[])call.getRawArguments()[1];
            assertEquals(sql.chars().filter(c->c=='?').count(),args.length);assertTrue(sql.endsWith("on conflict(recipient_id,source_key) do nothing"));assertTrue(sql.contains("r.user_id<>?"));}
        Object[] postArgs=(Object[])calls.get(1).getRawArguments()[1];assertEquals(4L,postArgs[0]);assertEquals("POST_LIKED",postArgs[1]);assertEquals("POST_LIKED:200",postArgs[2]);assertNull(postArgs[3]);assertEquals(51L,postArgs[5]);assertEquals(51L,postArgs[6]);assertEquals(51L,postArgs[7]);assertEquals(4L,postArgs[8]);
        Object[] commentArgs=(Object[])calls.get(2).getRawArguments()[1];assertEquals(100L,commentArgs[3]);assertEquals(100L,commentArgs[5]);assertEquals("COMMENT_LIKED:201",commentArgs[2]);
    }
    @Test void writerDoesNotSwallowUnexpectedDatabaseFailures(){
        var jdbc=mock(JdbcTemplate.class);when(jdbc.update(anyString(),any(Object[].class))).thenThrow(new org.springframework.dao.DataIntegrityViolationException("Controlled failure"));
        var writer=new CommentNotificationWriter(jdbc,Clock.fixed(NOW,ZoneOffset.UTC));var actor=new User("Actor","actor@example.test");field(actor,"id",4L);
        var post=new Post(new University("UNI","University","City","TR",null,NOW),actor,null,"Body",PostAudience.UNIVERSITY_STUDENTS,NOW);field(post,"id",51L);
        var like=new PostLike(post,actor,NOW);field(like,"id",200L);
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->writer.postLiked(like,actor));
    }
}
