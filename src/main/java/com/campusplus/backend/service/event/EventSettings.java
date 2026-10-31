package com.campusplus.backend.service.event;
import java.time.Duration;
import org.springframework.stereotype.Component;
@Component
public class EventSettings { public Duration checkInLead(){return Duration.ofMinutes(30);} }
