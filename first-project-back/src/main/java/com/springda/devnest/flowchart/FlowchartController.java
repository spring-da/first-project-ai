package com.springda.devnest.flowchart;

import com.springda.devnest.common.SaveRateLimiter;
import com.springda.devnest.config.WorkspaceOwner;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/flowcharts")
public class FlowchartController {
    private final FlowchartService flowcharts;
    private final SaveRateLimiter limiter;

    public FlowchartController(FlowchartService flowcharts, SaveRateLimiter limiter) {
        this.flowcharts = flowcharts;
        this.limiter = limiter;
    }

    @GetMapping
    public List<FlowchartDtos.Summary> list(
            @WorkspaceOwner String owner, @RequestParam(defaultValue = "") String q) {
        return flowcharts.list(owner, q);
    }

    @GetMapping("/trash")
    public List<FlowchartDtos.Summary> trash(@WorkspaceOwner String owner) {
        return flowcharts.trash(owner);
    }

    @GetMapping("/{id}")
    public FlowchartDtos.Response get(@WorkspaceOwner String owner, @PathVariable String id) {
        return flowcharts.get(owner, id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FlowchartDtos.Response create(
            @WorkspaceOwner String owner, @Valid @RequestBody FlowchartDtos.CreateRequest request) {
        limiter.check(owner, "flowcharts");
        return flowcharts.create(owner, request);
    }

    @PutMapping("/{id}")
    public FlowchartDtos.Response update(
            @WorkspaceOwner String owner,
            @PathVariable String id,
            @Valid @RequestBody FlowchartDtos.UpdateRequest request) {
        limiter.check(owner, "flowcharts");
        return flowcharts.update(owner, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@WorkspaceOwner String owner, @PathVariable String id) {
        flowcharts.delete(owner, id);
    }

    @PostMapping("/{id}/restore")
    public FlowchartDtos.Response restore(@WorkspaceOwner String owner, @PathVariable String id) {
        return flowcharts.restore(owner, id);
    }

    @DeleteMapping("/{id}/permanent")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void purge(@WorkspaceOwner String owner, @PathVariable String id) {
        flowcharts.purge(owner, id);
    }

    @GetMapping("/{id}/revisions")
    public List<FlowchartDtos.RevisionSummary> history(
            @WorkspaceOwner String owner,
            @PathVariable String id,
            @RequestParam(defaultValue = "0") int page) {
        return flowcharts.history(owner, id, page);
    }

    @GetMapping("/{id}/revisions/{revisionId}")
    public FlowchartDtos.RevisionResponse revision(
            @WorkspaceOwner String owner,
            @PathVariable String id,
            @PathVariable String revisionId) {
        return flowcharts.revision(owner, id, revisionId);
    }

    @PostMapping("/{id}/revisions/{revisionId}/restore")
    public FlowchartDtos.Response restoreRevision(
            @WorkspaceOwner String owner,
            @PathVariable String id,
            @PathVariable String revisionId,
            @Valid @RequestBody FlowchartDtos.RestoreRequest request) {
        limiter.check(owner, "flowcharts");
        return flowcharts.restoreRevision(owner, id, revisionId, request);
    }
}
