package com.campusplus.backend.dto.event;
import java.time.*;
public final class EventInput {
    private EventInput() {}
    public static String strip(String value){return value==null?null:value.strip();}
    public static String optional(String value){return value==null||value.isBlank()?null:value.strip();}
    public static boolean schedule(OffsetDateTime opens,OffsetDateTime closes,OffsetDateTime starts,OffsetDateTime ends,String zone) {
        if(opens==null||closes==null||starts==null||ends==null||zone==null) return true;
        return ZoneId.getAvailableZoneIds().contains(zone) && opens.toInstant().isBefore(closes.toInstant())
            && !closes.toInstant().isAfter(starts.toInstant()) && starts.toInstant().isBefore(ends.toInstant());
    }
}
