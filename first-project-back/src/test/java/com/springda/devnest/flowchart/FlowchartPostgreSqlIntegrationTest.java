package com.springda.devnest.flowchart;

import static org.assertj.core.api.Assertions.*;

import com.springda.devnest.common.ConflictException;
import com.springda.devnest.knowledge.*;
import com.springda.devnest.snippet.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.*;

import java.util.*;
import java.util.concurrent.*;

/** The inherited HTTP contract plus actual PostgreSQL locking, FK and atomic rollback checks. */
@EnabledIfEnvironmentVariable(named = "TEST_POSTGRES_URL", matches = ".+")
class FlowchartPostgreSqlIntegrationTest extends FlowchartIntegrationTest {
    @Autowired KnowledgeDomainService domains;
    @Autowired KnowledgeItemService knowledge;
    @Autowired SnippetService snippets;
    @Autowired SnippetRepository snippetRepository;

    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url", () -> System.getenv("TEST_POSTGRES_URL"));
        p.add("spring.datasource.username", () -> System.getenv("TEST_POSTGRES_USERNAME"));
        p.add("spring.datasource.password", () -> System.getenv("TEST_POSTGRES_PASSWORD"));
        p.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        p.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        p.add("spring.flyway.enabled", () -> "true");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentCreationReturnsSameDocumentAndOnlyOneConcurrentVersionWins() throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            var key = UUID.randomUUID();
            var request =
                    new FlowchartDtos.CreateRequest(
                            "Concurrent", null, false, json.readTree(EMPTY), key);
            var start = new CountDownLatch(1);
            Callable<FlowchartDtos.Response> create =
                    () -> {
                        start.await();
                        return flowcharts.create(owner, request);
                    };
            var a = executor.submit(create);
            var b = executor.submit(create);
            start.countDown();
            var first = a.get(15, TimeUnit.SECONDS);
            var second = b.get(15, TimeUnit.SECONDS);
            assertThat(first.id()).isEqualTo(second.id());
            var saveStart = new CountDownLatch(1);
            Callable<Boolean> save =
                    () -> {
                        saveStart.await();
                        try {
                            flowcharts.update(
                                    owner,
                                    first.id(),
                                    new FlowchartDtos.UpdateRequest(
                                            UUID.randomUUID().toString(),
                                            null,
                                            false,
                                            first.diagram(),
                                            first.version(),
                                            FlowchartDtos.SaveMode.AUTO));
                            return true;
                        } catch (ConflictException expected) {
                            return false;
                        }
                    };
            var x = executor.submit(save);
            var y = executor.submit(save);
            saveStart.countDown();
            assertThat(List.of(x.get(15, TimeUnit.SECONDS), y.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
            assertThat(flowcharts.get(owner, first.id()).version()).isEqualTo(1);
        } finally {
            users.deleteById(owner);
            users.deleteById(other);
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void mixedBulkConflictRollsBackEarlierSnippetAndFlowchartMoves() {
        try {
            var folder =
                    domains.create(
                            owner, new KnowledgeDomainDtos.SaveRequest("Destination", "", 0));
            var snippet =
                    snippets.create(
                            owner,
                            new SnippetDtos.SaveRequest("Snippet", "SQL", "select 1", false, null));
            var a =
                    flowcharts.create(
                            owner,
                            new FlowchartDtos.CreateRequest(
                                    "A", null, false, json.readTree(EMPTY), UUID.randomUUID()));
            var b =
                    flowcharts.create(
                            owner,
                            new FlowchartDtos.CreateRequest(
                                    "B", null, false, json.readTree(EMPTY), UUID.randomUUID()));
            var sorted = new ArrayList<>(List.of(a, b));
            sorted.sort(Comparator.comparing(FlowchartDtos.Response::id));
            var request =
                    new KnowledgeItemDtos.BulkDomainRequest(
                            List.of(
                                    new KnowledgeItemDtos.BulkDomainItem(
                                            KnowledgeItemType.SNIPPET, snippet.id(), null),
                                    new KnowledgeItemDtos.BulkDomainItem(
                                            KnowledgeItemType.FLOWCHART, sorted.get(0).id(), 0L),
                                    new KnowledgeItemDtos.BulkDomainItem(
                                            KnowledgeItemType.FLOWCHART, sorted.get(1).id(), 99L)),
                            folder.id());
            assertThatThrownBy(() -> knowledge.moveToDomain(owner, request))
                    .isInstanceOf(ConflictException.class);
            assertThat(
                            snippetRepository
                                    .findByIdAndOwnerId(snippet.id(), owner)
                                    .orElseThrow()
                                    .getDomainId())
                    .isNull();
            assertThat(flowcharts.get(owner, a.id()).domainId()).isNull();
            assertThat(flowcharts.get(owner, b.id()).domainId()).isNull();
            assertThat(flowcharts.history(owner, sorted.get(0).id(), 0)).hasSize(1);
            knowledge.moveToDomain(
                    owner,
                    new KnowledgeItemDtos.BulkDomainRequest(
                            List.of(
                                    new KnowledgeItemDtos.BulkDomainItem(
                                            KnowledgeItemType.FLOWCHART, a.id(), 0L)),
                            folder.id()));
            assertThat(flowcharts.get(owner, a.id()).domainId()).isEqualTo(folder.id());
            domains.delete(owner, folder.id());
            assertThat(flowcharts.get(owner, a.id()).domainId()).isNull();
            assertThat(flowcharts.get(owner, a.id()).version()).isEqualTo(2);
            var detached = flowcharts.get(owner, a.id());
            flowcharts.update(
                    owner,
                    a.id(),
                    new FlowchartDtos.UpdateRequest(
                            detached.title(),
                            null,
                            false,
                            detached.diagram(),
                            detached.version(),
                            FlowchartDtos.SaveMode.MANUAL));
            assertThat(flowcharts.history(owner, a.id(), 0)).hasSize(3);
        } finally {
            users.deleteById(owner);
            users.deleteById(other);
        }
    }
}
