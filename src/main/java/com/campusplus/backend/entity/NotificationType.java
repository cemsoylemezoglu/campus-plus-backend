package com.campusplus.backend.entity;
import static com.campusplus.backend.entity.NotificationResource.*;
public enum NotificationType {
    ANNOUNCEMENT_PUBLISHED(ANNOUNCEMENT,"Yeni bir kulüp duyurusu yayımlandı."),
    CLUB_APPROVED(CLUB,"Kulübünüz onaylandı."), CLUB_REJECTED(CLUB,"Kulüp başvurunuz değerlendirildi."),
    CLUB_SUSPENDED(CLUB,"Kulübünüz askıya alındı."), CLUB_RESTORED(CLUB,"Kulübünüz yeniden etkinleştirildi."),
    CLUB_ARCHIVED(CLUB,"Kulübünüz arşivlendi."),
    MEMBERSHIP_REQUESTED(CLUB_MEMBERSHIP,"Yeni bir üyelik başvurusu var."),
    MEMBERSHIP_APPROVED(CLUB_MEMBERSHIP,"Kulüp üyeliğiniz onaylandı."),
    MEMBERSHIP_REJECTED(CLUB_MEMBERSHIP,"Üyelik başvurunuz değerlendirildi."),
    MEMBERSHIP_REMOVED(CLUB_MEMBERSHIP,"Kulüp üyeliğiniz sonlandırıldı."),
    MEMBERSHIP_REAPPLICATION_ALLOWED(CLUB_MEMBERSHIP,"Kulübe yeniden başvurabilirsiniz."),
    MEMBERSHIP_SUSPENDED(CLUB_MEMBERSHIP,"Kulüp üyeliğiniz askıya alındı."),
    MEMBERSHIP_RESTORED(CLUB_MEMBERSHIP,"Kulüp üyeliğiniz yeniden etkinleştirildi."),
    MEMBERSHIP_ROLE_CHANGED(CLUB_MEMBERSHIP,"Kulüp içindeki göreviniz değişti."),
    MEMBERSHIP_OWNERSHIP_TRANSFERRED(CLUB_MEMBERSHIP,"Kulüp sahipliği devredildi."),
    EVENT_PUBLISHED(EVENT,"Yeni bir kulüp etkinliği yayımlandı."),
    EVENT_RESCHEDULED(EVENT,"Etkinliğin zamanı güncellendi."), EVENT_CANCELLED(EVENT,"Etkinlik iptal edildi."),
    PARTICIPATION_REGISTERED(EVENT_PARTICIPATION,"Etkinlik kaydınız tamamlandı."),
    PARTICIPATION_CHECKED_IN(EVENT_PARTICIPATION,"Etkinliğe girişiniz doğrulandı."),
    PARTICIPATION_REMOVED(EVENT_PARTICIPATION,"Etkinlik kaydınız sonlandırıldı."),
    PARTICIPATION_REAPPLICATION_ALLOWED(EVENT_PARTICIPATION,"Etkinliğe yeniden kayıt olabilirsiniz."),
    PARTICIPATION_CANCELLED(EVENT_PARTICIPATION,"Etkinlik kaydınız iptal edildi.");
    private final NotificationResource resource;
    private final String message;
    NotificationType(NotificationResource resource,String message){this.resource=resource;this.message=message;}
    public NotificationResource resource(){return resource;}
    public String message(){return message;}
}
