package com.springda.devnest.sharing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.assertThat;

/** Runs the same HTTP/security scenarios against an isolated Flyway-managed PostgreSQL database. */
@EnabledIfEnvironmentVariable(named = "TEST_POSTGRES_URL", matches = ".+")
class SharingPostgreSqlIntegrationTest extends SharingIntegrationTest {
    @Autowired SharingService sharing;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    @Autowired com.springda.devnest.snippet.SnippetRepository snippetRepository;
    @Autowired com.springda.devnest.flowchart.FlowchartRepository logRepository;
    @DynamicPropertySource static void postgres(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> System.getenv("TEST_POSTGRES_URL"));
        properties.add("spring.datasource.username", () -> System.getenv("TEST_POSTGRES_USERNAME"));
        properties.add("spring.datasource.password", () -> System.getenv("TEST_POSTGRES_PASSWORD"));
        properties.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        properties.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        properties.add("spring.flyway.enabled", () -> "true");
    }
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentPublicationReturnsOneExistingResultAndOneNewResult() throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            var request = new SharingDtos.PublishRequest(List.of(new SharingDtos.Reference(ResourceType.MARKDOWN, document),
                    new SharingDtos.Reference(ResourceType.SNIPPET, snippet), new SharingDtos.Reference(ResourceType.FLOWCHART, log)));
            Callable<SharingDtos.PublishResult> publish = () -> { start.await(); return sharing.publish(owner, request); };
            var first = executor.submit(publish); var second = executor.submit(publish); start.countDown();
            var a = first.get(20, TimeUnit.SECONDS); var b = second.get(20, TimeUnit.SECONDS);
            assertThat(a.publishedCount() + b.publishedCount()).isEqualTo(3);
            assertThat(a.existingCount() + b.existingCount()).isEqualTo(3);
            assertThat(sharing.pool(reader, 0, 20, "", null, false).total()).isEqualTo(3);
        } finally {
            users.deleteById(owner); users.deleteById(reader);
        }
    }
    @Test void deletingOwnerCascadesPoolBundlesAndItems() throws Exception {
        sharing.publish(owner, new SharingDtos.PublishRequest(List.of(new SharingDtos.Reference(ResourceType.MARKDOWN, document))));
        var link = sharing.createLink(owner, new SharingDtos.CreateLinkRequest("Cascade", List.of(new SharingDtos.Reference(ResourceType.MARKDOWN, document)), java.time.Instant.now().plusSeconds(86400)));
        jdbc.update("DELETE FROM users WHERE id = ?", owner);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sharing_pool_entries WHERE owner_id = ?", Long.class, owner)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM share_bundles WHERE owner_id = ?", Long.class, owner)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM share_bundle_items WHERE bundle_id = ?", Long.class, link.id())).isZero();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void inFlightContentEditsCannotUndoTrashOrReviveAnEarlierShare() throws Exception {
        var loaded = new CountDownLatch(1); var finishEdit = new CountDownLatch(1);
        var executor = Executors.newSingleThreadExecutor();
        try {
            var link = sharing.createLink(owner, new SharingDtos.CreateLinkRequest("Concurrent", List.of(
                    new SharingDtos.Reference(ResourceType.SNIPPET, snippet), new SharingDtos.Reference(ResourceType.FLOWCHART, log)), java.time.Instant.now().plusSeconds(86400)));
            var edit = executor.submit(() -> new org.springframework.transaction.support.TransactionTemplate(transactions).execute(status -> {
                var staleSnippet = snippetRepository.findByIdAndOwnerId(snippet, owner).orElseThrow();
                var staleLog = logRepository.findByIdAndOwnerId(log, owner).orElseThrow();
                loaded.countDown();
                try { if (!finishEdit.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("edit release timed out"); }
                catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException(exception); }
                staleSnippet.update(null, "Concurrent edit", "Java", "return 99;", false);
                staleLog.update("Concurrent flowchart edit", null, false, new com.springda.devnest.flowchart.FlowchartValidator.Validated(staleLog.getDiagram(), staleLog.getSearchText(), staleLog.getNodeCount(), staleLog.getEdgeCount()));
                snippetRepository.flush(); return null;
            }));
            assertThat(loaded.await(10, TimeUnit.SECONDS)).isTrue();
            snippets.delete(owner, snippet); logs.delete(owner, log); finishEdit.countDown();
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> edit.get(15, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class).hasCauseInstanceOf(org.springframework.dao.OptimisticLockingFailureException.class);
            assertThat(snippets.trash(owner)).hasSize(1);
            assertThat(logs.trash(owner)).hasSize(1);
            snippets.restore(owner, snippet); logs.restore(owner, log);
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> sharing.publicBundle(link.token()))
                    .isInstanceOf(com.springda.devnest.common.NotFoundException.class);
        } finally {
            finishEdit.countDown(); executor.shutdownNow(); executor.awaitTermination(15, TimeUnit.SECONDS);
            users.deleteById(owner); users.deleteById(reader);
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void staleTrashCannotOverwriteANewerSharingGeneration() throws Exception {
        var loaded = new CountDownLatch(1); var finishTrash = new CountDownLatch(1);
        var executor = Executors.newSingleThreadExecutor();
        try {
            var delayed = executor.submit(() -> new org.springframework.transaction.support.TransactionTemplate(transactions).execute(status -> {
                var staleSnippet = snippetRepository.findByIdAndOwnerId(snippet, owner).orElseThrow();
                var staleLog = logRepository.findByIdAndOwnerId(log, owner).orElseThrow();
                loaded.countDown();
                try { if (!finishTrash.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("trash release timed out"); }
                catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException(exception); }
                staleSnippet.moveToTrash(); staleLog.moveToTrash(); snippetRepository.flush(); return null;
            }));
            assertThat(loaded.await(10, TimeUnit.SECONDS)).isTrue();
            snippets.delete(owner, snippet); logs.delete(owner, log);
            snippets.restore(owner, snippet); logs.restore(owner, log);
            var link = sharing.createLink(owner, new SharingDtos.CreateLinkRequest("After first restore", List.of(
                    new SharingDtos.Reference(ResourceType.SNIPPET, snippet), new SharingDtos.Reference(ResourceType.FLOWCHART, log)), java.time.Instant.now().plusSeconds(86400)));
            finishTrash.countDown();
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> delayed.get(15, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class).hasCauseInstanceOf(org.springframework.dao.OptimisticLockingFailureException.class);
            assertThat(sharing.publicBundle(link.token()).items()).hasSize(2);
            snippets.delete(owner, snippet); logs.delete(owner, log);
            snippets.restore(owner, snippet); logs.restore(owner, log);
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> sharing.publicBundle(link.token()))
                    .isInstanceOf(com.springda.devnest.common.NotFoundException.class);
        } finally {
            finishTrash.countDown(); executor.shutdownNow(); executor.awaitTermination(15, TimeUnit.SECONDS);
            users.deleteById(owner); users.deleteById(reader);
        }
    }
}
