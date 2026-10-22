package com.lucapodesta.agentoffice.infrastructure.persistence;

import com.lucapodesta.agentoffice.application.dto.PageQuery;
import com.lucapodesta.agentoffice.application.dto.PageResult;
import com.lucapodesta.agentoffice.application.dto.TaskFilter;
import com.lucapodesta.agentoffice.application.port.out.TaskRepository;
import com.lucapodesta.agentoffice.domain.task.Task;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

@Component
class TaskPersistenceAdapter implements TaskRepository {

    private final TaskJpaRepository jpa;

    TaskPersistenceAdapter(TaskJpaRepository jpa) {
        this.jpa = jpa;
    }

    /**
     * Updates go through the managed entity loaded in the current transaction, so its
     * {@code @Version} detects concurrent writers.
     */
    @Override
    public Task save(Task task) {
        TaskEntity entity = jpa.findById(task.id())
                .map(existing -> {
                    existing.apply(task);
                    return existing;
                })
                .orElseGet(() -> TaskEntity.from(task));
        return jpa.save(entity).toDomain();
    }

    @Override
    public Optional<Task> findById(UUID id) {
        return jpa.findById(id).map(TaskEntity::toDomain);
    }

    @Override
    public PageResult<Task> search(TaskFilter filter, PageQuery query) {
        PageRequest pageRequest = PageRequest.of(query.page(), query.size(), Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<TaskEntity> page = jpa.findAll(matching(filter), pageRequest);
        return new PageResult<>(
                page.getContent().stream().map(TaskEntity::toDomain).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    private static Specification<TaskEntity> matching(TaskFilter filter) {
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filter.status() != null) {
                predicates.add(cb.equal(root.get("status"), filter.status()));
            }
            if (filter.agent() != null) {
                predicates.add(cb.equal(root.get("assignedAgent"), filter.agent()));
            }
            if (filter.priority() != null) {
                predicates.add(cb.equal(root.get("priority"), filter.priority()));
            }
            if (filter.source() != null) {
                predicates.add(cb.equal(root.get("source"), filter.source()));
            }
            if (filter.externalReference() != null) {
                predicates.add(cb.equal(root.get("externalReference"), filter.externalReference()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
