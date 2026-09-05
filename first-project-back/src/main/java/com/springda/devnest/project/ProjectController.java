package com.springda.devnest.project;

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
@RequestMapping("/api/v1/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping
    List<ProjectDtos.Response> list(@WorkspaceOwner String ownerId) {
        return projectService.list(ownerId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ProjectDtos.Response create(@WorkspaceOwner String ownerId, @Valid @RequestBody ProjectDtos.SaveRequest request) {
        return projectService.create(ownerId, request);
    }

    @PutMapping("/{id}")
    ProjectDtos.Response update(
            @WorkspaceOwner String ownerId,
            @PathVariable String id,
            @Valid @RequestBody ProjectDtos.SaveRequest request
    ) {
        return projectService.update(ownerId, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@WorkspaceOwner String ownerId, @PathVariable String id) {
        projectService.delete(ownerId, id);
    }
}
