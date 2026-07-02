package com.acetelecom.repairdesk.feature.notification.controller;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.acetelecom.repairdesk.feature.notification.dto.NotificationResponse;
import com.acetelecom.repairdesk.feature.notification.service.NotificationService;
import com.acetelecom.repairdesk.security.CurrentUser;

@RestController
@RequestMapping("/me/notifications")
public class NotificationController {

    private final NotificationService service;
    private final CurrentUser currentUser;

    public NotificationController(NotificationService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<NotificationResponse> inbox() {
        return service.inbox(currentUser.get().id());
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount() {
        return Map.of("count", service.unreadCount(currentUser.get().id()));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@PathVariable UUID id) {
        service.markRead(id, currentUser.get().id());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/read-all")
    public ResponseEntity<Void> markAllRead() {
        service.markAllRead(currentUser.get().id());
        return ResponseEntity.noContent().build();
    }
}
