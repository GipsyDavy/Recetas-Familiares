package org.gipsybuho.recetasfamiliares.sync;

import java.time.Instant;

import jakarta.validation.Valid;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/families/{familyId}/sync")
public class SyncController {

    private final SyncService syncService;

    public SyncController(SyncService syncService) {
        this.syncService = syncService;
    }

    @GetMapping("/pull")
    public SyncPullResponse pull(
            @PathVariable String familyId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant since,
            @RequestParam(required = false) Integer limit,
            Authentication authentication
    ) {
        return syncService.pull(familyId, authentication.getName(), since, limit);
    }

    @PostMapping("/push")
    public SyncPullResponse push(
            @PathVariable String familyId,
            @Valid @RequestBody SyncPushRequest request,
            Authentication authentication
    ) {
        return syncService.push(familyId, authentication.getName(), request);
    }
}
