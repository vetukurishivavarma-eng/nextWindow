package com.nextwindow.monitoring;

import com.nextwindow.auth.AuthUser;
import com.nextwindow.common.PageResponse;
import com.nextwindow.watch.WatchDtos;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/monitoring")
public class MonitoringController {
    private final MonitoringQueryService service;

    public MonitoringController(MonitoringQueryService service) {
        this.service = service;
    }

    @GetMapping("/health")
    public MonitoringQueryService.Health health(@AuthenticationPrincipal AuthUser user) {
        return service.health(user.id());
    }

    @GetMapping("/checks")
    public PageResponse<WatchDtos.CheckRunResponse> checks(@AuthenticationPrincipal AuthUser user,
                                                           @RequestParam(defaultValue = "all") String filter,
                                                           @RequestParam(defaultValue = "0") int page,
                                                           @RequestParam(defaultValue = "25") int size) {
        return service.checks(user.id(), filter, page, size);
    }
}
