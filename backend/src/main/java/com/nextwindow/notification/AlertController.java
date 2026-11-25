package com.nextwindow.notification;

import com.nextwindow.auth.AuthUser;
import com.nextwindow.common.PageResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {
    private final AlertQueryService service;

    public AlertController(AlertQueryService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<AlertDtos.AlertResponse> list(@AuthenticationPrincipal AuthUser user,
                                                      @RequestParam(defaultValue = "false") boolean unread,
                                                      @RequestParam(required = false) Long watchId,
                                                      @RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "20") int size) {
        return service.list(user.id(), unread, watchId, page, size);
    }

    @GetMapping("/unread")
    public PageResponse<AlertDtos.AlertResponse> unread(@AuthenticationPrincipal AuthUser user,
                                                        @RequestParam(defaultValue = "0") int page,
                                                        @RequestParam(defaultValue = "20") int size) {
        return service.list(user.id(), true, null, page, size);
    }

    @GetMapping("/unread-count")
    public AlertDtos.UnreadCount unreadCount(@AuthenticationPrincipal AuthUser user) {
        return new AlertDtos.UnreadCount(service.unread(user.id()));
    }

    @PostMapping("/{id}/read")
    public AlertDtos.AlertResponse markRead(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return service.setRead(user.id(), id, true);
    }

    @PostMapping("/{id}/unread")
    public AlertDtos.AlertResponse markUnread(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return service.setRead(user.id(), id, false);
    }

    @PostMapping("/read-all")
    public Map<String, Integer> markAllRead(@AuthenticationPrincipal AuthUser user) {
        return Map.of("updated", service.markAllRead(user.id()));
    }
}
