package com.lucapodesta.agentoffice.application.port.out;

import com.lucapodesta.agentoffice.domain.review.Review;
import java.util.List;
import java.util.UUID;

public interface ReviewRepository {

    Review save(Review review);

    List<Review> findByTaskId(UUID taskId);
}
