package com.lucapodesta.agentoffice.infrastructure.web;

import com.lucapodesta.agentoffice.application.dto.PageQuery;
import com.lucapodesta.agentoffice.application.dto.PageResult;
import com.lucapodesta.agentoffice.application.dto.TaskFilter;
import com.lucapodesta.agentoffice.application.service.TaskService;
import com.lucapodesta.agentoffice.domain.agent.AgentType;
import com.lucapodesta.agentoffice.domain.task.Task;
import com.lucapodesta.agentoffice.domain.task.TaskPriority;
import com.lucapodesta.agentoffice.domain.task.TaskStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/tasks")
@Tag(name = "Tasks", description = "Create, run, inspect and cancel agent tasks")
@ApiResponse(responseCode = "400", description = "Invalid request",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
class TaskController {

    private final TaskService taskService;

    TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping
    @Operation(summary = "Create a task", description = "The task enters INBOX and is routed by the Manager agent.")
    @ApiResponse(responseCode = "201", description = "Task created")
    ResponseEntity<TaskResponse> create(@Valid @RequestBody CreateTaskRequest request) {
        Task task = taskService.create(request.toCommand());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(task.id()).toUri();
        return ResponseEntity.created(location).body(respond(task));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a task")
    @ApiResponse(responseCode = "200", description = "Task found")
    @ApiResponse(responseCode = "404", description = "TASK_NOT_FOUND",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    TaskResponse get(@PathVariable UUID id) {
        return respond(taskService.get(id));
    }

    @GetMapping
    @Operation(summary = "List tasks", description = "Newest first. All filters are optional and combined with AND.")
    PageResponse<TaskResponse> list(
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(required = false) AgentType agent,
            @RequestParam(required = false) TaskPriority priority,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(PageQuery.MAX_SIZE) int size) {
        PageResult<Task> tasks = taskService.list(new TaskFilter(status, agent, priority), new PageQuery(page, size));
        Map<UUID, String> boardUrls = taskService.boardUrls(tasks.content().stream().map(Task::id).toList());
        return PageResponse.from(tasks.map(task -> TaskResponse.from(task, boardUrls.get(task.id()))));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel a task", description = "Allowed while the task is not DONE, FAILED or CANCELLED.")
    @ApiResponse(responseCode = "200", description = "Task cancelled")
    @ApiResponse(responseCode = "404", description = "TASK_NOT_FOUND",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "INVALID_TASK_STATE or CONCURRENT_MODIFICATION",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    TaskResponse cancel(@PathVariable UUID id) {
        return respond(taskService.cancel(id));
    }

    @PostMapping("/{id}/execute")
    @Operation(summary = "Run or resume a task",
            description = "Starts a task in INBOX or PLANNED, or resumes a BLOCKED one. Agents run in the background.")
    @ApiResponse(responseCode = "202", description = "Execution scheduled")
    @ApiResponse(responseCode = "404", description = "TASK_NOT_FOUND",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "INVALID_TASK_STATE",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    ResponseEntity<TaskResponse> execute(@PathVariable UUID id) {
        return ResponseEntity.accepted().body(respond(taskService.execute(id)));
    }

    @GetMapping("/{id}/executions")
    @Operation(summary = "Agent executions of a task", description = "Every agent invocation, oldest first.")
    @ApiResponse(responseCode = "404", description = "TASK_NOT_FOUND",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    List<ExecutionResponse> executions(@PathVariable UUID id) {
        return taskService.executions(id).stream().map(ExecutionResponse::from).toList();
    }

    @GetMapping("/{id}/reviews")
    @Operation(summary = "Reviews of a task", description = "Reviewer verdicts, oldest first.")
    @ApiResponse(responseCode = "404", description = "TASK_NOT_FOUND",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    List<ReviewResponse> reviews(@PathVariable UUID id) {
        return taskService.reviews(id).stream().map(ReviewResponse::from).toList();
    }

    @GetMapping("/{id}/events")
    @Operation(summary = "Audit trail of a task", description = "Chronological list of everything that happened to the task.")
    @ApiResponse(responseCode = "404", description = "TASK_NOT_FOUND",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    List<TaskEventResponse> events(@PathVariable UUID id) {
        return taskService.history(id).stream().map(TaskEventResponse::from).toList();
    }

    private TaskResponse respond(Task task) {
        return TaskResponse.from(task, taskService.boardUrls(List.of(task.id())).get(task.id()));
    }
}
