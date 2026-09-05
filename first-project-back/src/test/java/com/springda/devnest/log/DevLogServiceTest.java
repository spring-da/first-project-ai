package com.springda.devnest.log;

import com.springda.devnest.common.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.springda.devnest.knowledge.KnowledgeDomainService;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DevLogServiceTest {

    @Mock
    private DevLogRepository repository;

    @Mock
    private KnowledgeDomainService domains;

    @InjectMocks
    private DevLogService service;

    @Test
    void createsFallbackTitleAndNormalizesTags() {
        when(repository.save(any(DevLogEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var request = new DevLogDtos.SaveRequest(
                "",
                "解决 Flutter 列表性能问题\n记录排查过程",
                LogCategory.PROBLEM,
                List.of(" Flutter ", "#性能", "Flutter"),
                false,
                null);

        var response = service.create("user-1", request);

        assertThat(response.title()).isEqualTo("解决 Flutter 列表性能问题");
        assertThat(response.tags()).containsExactly("Flutter", "性能");
        assertThat(response.category()).isEqualTo(LogCategory.PROBLEM);
    }

    @Test
    void movesOnlyAnOwnerScopedLog() {
        var log = new DevLogEntity("owner-1", "domain-1", "排查记录", "正文", LogCategory.PROBLEM, List.of(), false);
        when(repository.findByIdAndOwnerId("log-1", "owner-1")).thenReturn(Optional.of(log));
        when(domains.validateSelection("owner-1", "domain-2")).thenReturn("domain-2");

        service.moveToDomain("owner-1", "log-1", "domain-2");

        assertThat(log.getDomainId()).isEqualTo("domain-2");
        verify(repository).save(log);
    }

    @Test
    void hidesAnotherOwnersLog() {
        when(repository.findByIdAndOwnerId("log-1", "owner-2")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.moveToDomain("owner-2", "log-1", "domain-2"))
                .isInstanceOf(NotFoundException.class);

        verify(domains, never()).validateSelection("owner-2", "domain-2");
        verify(repository, never()).save(any());
    }
}
