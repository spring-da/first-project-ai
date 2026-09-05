package com.springda.devnest.project;

import com.springda.devnest.common.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;

@Service
public class ProjectService {

    private final ProjectRepository projects;

    public ProjectService(ProjectRepository projects) {
        this.projects = projects;
    }

    @Transactional(readOnly = true)
    public List<ProjectDtos.Response> list(String ownerId) {
        return projects.findAllByOwnerIdOrderByUpdatedAtDesc(ownerId)
                .stream().map(ProjectDtos.Response::from).toList();
    }

    @Transactional
    public ProjectDtos.Response create(String ownerId, ProjectDtos.SaveRequest request) {
        var project = new ProjectEntity(
                ownerId, request.name().trim(), normalized(request.description()), normalizeTags(request.techStack()),
                request.status(), request.progress(), request.nextAction().trim());
        return ProjectDtos.Response.from(projects.save(project));
    }

    @Transactional
    public ProjectDtos.Response update(String ownerId, String id, ProjectDtos.SaveRequest request) {
        var project = find(ownerId, id);
        project.update(
                request.name().trim(), normalized(request.description()), normalizeTags(request.techStack()),
                request.status(), request.progress(), request.nextAction().trim());
        return ProjectDtos.Response.from(projects.save(project));
    }

    @Transactional
    public void delete(String ownerId, String id) {
        projects.delete(find(ownerId, id));
    }

    private ProjectEntity find(String ownerId, String id) {
        return projects.findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() -> new NotFoundException("项目", id));
    }

    private List<String> normalizeTags(List<String> values) {
        var result = new LinkedHashSet<String>();
        values.stream().map(String::trim).filter(value -> !value.isEmpty()).forEach(result::add);
        return List.copyOf(result);
    }

    private String normalized(String value) {
        return value == null ? "" : value.trim();
    }
}
