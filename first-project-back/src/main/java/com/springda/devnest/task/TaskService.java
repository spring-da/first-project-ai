package com.springda.devnest.task;

import com.springda.devnest.common.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TaskService {

    private final TaskRepository tasks;

    public TaskService(TaskRepository tasks) {
        this.tasks = tasks;
    }

    @Transactional(readOnly = true)
    public List<TaskDtos.Response> list(String ownerId) {
        return tasks.findAllByOwnerIdOrderBySortOrderAscCreatedAtAsc(ownerId)
                .stream().map(TaskDtos.Response::from).toList();
    }

    @Transactional
    public TaskDtos.Response create(String ownerId, TaskDtos.CreateRequest request) {
        return TaskDtos.Response.from(tasks.save(
                new TaskEntity(
                        ownerId,
                        request.title().trim(),
                        request.sortOrder(),
                        request.scheduledDate(),
                        request.dueAt(),
                        request.priority() == null ? TaskPriority.NORMAL : request.priority())));
    }

    @Transactional
    public TaskDtos.Response update(String ownerId, String id, TaskDtos.UpdateRequest request) {
        var task = find(ownerId, id);
        task.update(
                request.title().trim(),
                request.done(),
                request.sortOrder(),
                request.scheduledDate(),
                request.dueAt(),
                request.priority(),
                request.archived());
        return TaskDtos.Response.from(tasks.save(task));
    }

    @Transactional
    public void delete(String ownerId, String id) {
        tasks.delete(find(ownerId, id));
    }

    private TaskEntity find(String ownerId, String id) {
        return tasks.findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() -> new NotFoundException("任务", id));
    }
}
