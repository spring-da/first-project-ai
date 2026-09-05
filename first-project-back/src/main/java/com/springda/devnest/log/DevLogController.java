package com.springda.devnest.log;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import com.springda.devnest.config.WorkspaceOwner;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/logs")
public class DevLogController {

    private final DevLogService logService;

    public DevLogController(DevLogService logService) {
        this.logService = logService;
    }

    @GetMapping
    List<DevLogDtos.Response> list(@WorkspaceOwner String ownerId) {
        return logService.list(ownerId);
    }

    @GetMapping("/trash")
    List<DevLogDtos.TrashResponse> trash(@WorkspaceOwner String ownerId) {
        return logService.trash(ownerId);
    }

    @PostMapping("/{id}/restore")
    DevLogDtos.Response restore(@WorkspaceOwner String ownerId, @PathVariable String id) {
        return logService.restore(ownerId, id);
    }

    @DeleteMapping("/{id}/permanent")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void purge(@WorkspaceOwner String ownerId, @PathVariable String id) {
        logService.purge(ownerId, id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    DevLogDtos.Response create(@WorkspaceOwner String ownerId, @Valid @RequestBody DevLogDtos.SaveRequest request) {
        return logService.create(ownerId, request);
    }

    @PutMapping("/{id}")
    DevLogDtos.Response update(
            @WorkspaceOwner String ownerId,
            @PathVariable String id,
            @Valid @RequestBody DevLogDtos.SaveRequest request
    ) {
        return logService.update(ownerId, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@WorkspaceOwner String ownerId, @PathVariable String id) {
        logService.delete(ownerId, id);
    }
}
