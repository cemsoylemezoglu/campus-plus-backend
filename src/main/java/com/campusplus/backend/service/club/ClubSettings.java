package com.campusplus.backend.service.club;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ClubSettings {
    private final Duration reapplyCooldown;
    public ClubSettings(@Value("${campus.clubs.reapply-cooldown-seconds:60}") long seconds) {
        if(seconds<1 || seconds>86400) throw new IllegalArgumentException("Üyelik tekrar başvuru süresi 1..86400 saniye olmalıdır.");
        reapplyCooldown=Duration.ofSeconds(seconds);
    }
    public Duration reapplyCooldown() { return reapplyCooldown; }
}
