package com.campusplus.backend.service.comment;
import com.campusplus.backend.exception.InvalidRequestException;
import java.nio.ByteBuffer;
import java.time.*;
import java.util.Base64;
public record CommentCursor(Instant time,long id,long postId,long rootId) {
    public String encode(){return Base64.getUrlEncoder().withoutPadding().encodeToString(ByteBuffer.allocate(37).put((byte)1)
        .putLong(time.getEpochSecond()).putInt(time.getNano()).putLong(id).putLong(postId).putLong(rootId).array());}
    public static CommentCursor decode(String value,long postId,long rootId) {
        if(value==null)return null;
        try {
            if(value.length()!=50||!value.matches("[A-Za-z0-9_-]+"))throw new IllegalArgumentException();
            byte[] bytes=Base64.getUrlDecoder().decode(value);if(bytes.length!=37)throw new IllegalArgumentException();
            ByteBuffer b=ByteBuffer.wrap(bytes);if(b.get()!=1)throw new IllegalArgumentException();
            long seconds=b.getLong();int nanos=b.getInt();long id=b.getLong(),post=b.getLong(),root=b.getLong();
            if(nanos<0||nanos>999999999||id<=0||post<=0||root<0||post!=postId||root!=rootId)throw new IllegalArgumentException();
            var c=new CommentCursor(Instant.ofEpochSecond(seconds,nanos),id,post,root);
            if(c.time.isBefore(Instant.parse("0001-01-01T00:00:00Z"))||c.time.isAfter(Instant.parse("9999-12-31T23:59:59.999999Z"))||!c.encode().equals(value))throw new IllegalArgumentException();
            return c;
        }catch(IllegalArgumentException|DateTimeException e){throw new InvalidRequestException("Yorum cursor değeri geçersiz.");}
    }
}
