package com.lucapodesta.agentoffice.infrastructure.persistence;

import com.lucapodesta.agentoffice.application.port.out.ReviewRepository;
import com.lucapodesta.agentoffice.domain.review.Review;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class ReviewPersistenceAdapter implements ReviewRepository {

    private final ReviewJpaRepository jpa;

    ReviewPersistenceAdapter(ReviewJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Review save(Review review) {
        return jpa.save(ReviewEntity.from(review)).toDomain();
    }

    @Override
    public List<Review> findByTaskId(UUID taskId) {
        return jpa.findByTaskIdOrderByCreatedAtAsc(taskId).stream().map(ReviewEntity::toDomain).toList();
    }
}
