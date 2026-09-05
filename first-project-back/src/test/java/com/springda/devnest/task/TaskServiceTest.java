package com.springda.devnest.task;

import com.springda.devnest.common.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private TaskRepository repository;

    @InjectMocks
    private TaskService service;

    @Test
    void refusesToUpdateTaskOutsideCurrentUserScope() {
        when(repository.findByIdAndOwnerId("task-1", "user-2")).thenReturn(Optional.empty());
        var request = new TaskDtos.UpdateRequest(
                "不能修改别人的任务", true, 0, null, null, TaskPriority.NORMAL, false);

        assertThatThrownBy(() -> service.update("user-2", "task-1", request))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("task-1");
    }

    @Test
    void recordsCompletionTimeAndClearsItWhenTaskIsReopened() {
        var task = new TaskEntity("user-1", "Ship it", 0, null, null, TaskPriority.HIGH);
        when(repository.findByIdAndOwnerId("task-1", "user-1")).thenReturn(Optional.of(task));
        when(repository.save(any(TaskEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var completed = service.update("user-1", "task-1", new TaskDtos.UpdateRequest(
                "Ship it", true, 0, null, null, TaskPriority.HIGH, false));
        assertThat(completed.completedAt()).isNotNull();

        var reopened = service.update("user-1", "task-1", new TaskDtos.UpdateRequest(
                "Ship it", false, 0, null, null, TaskPriority.HIGH, false));
        assertThat(reopened.completedAt()).isNull();
        verify(repository, times(2)).save(task);
    }

    @Test
    void defaultsNewTaskPriorityToNormalForOlderClients() {
        when(repository.save(any(TaskEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var created = service.create("user-1", new TaskDtos.CreateRequest("Inbox item", 0, null, null, null));

        assertThat(created.priority()).isEqualTo(TaskPriority.NORMAL);
        assertThat(created.scheduledDate()).isNull();
    }
}
