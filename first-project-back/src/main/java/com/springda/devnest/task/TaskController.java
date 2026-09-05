package com.springda.devnest.task;

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
@RequestMapping("/api/v1/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping
    List<TaskDtos.Response> list(@WorkspaceOwner String ownerId) {
        return taskService.list(ownerId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    TaskDtos.Response create(@WorkspaceOwner String ownerId, @Valid @RequestBody TaskDtos.CreateRequest request) {
        return taskService.create(ownerId, request);
    }

    @PutMapping("/{id}")
    TaskDtos.Response update(
            @WorkspaceOwner String ownerId,
            @PathVariable String id,
            @Valid @RequestBody TaskDtos.UpdateRequest request
    ) {
        return taskService.update(ownerId, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@WorkspaceOwner String ownerId, @PathVariable String id) {
        taskService.delete(ownerId, id);
    }
}
