package com.mozip.server.policy.repository;

import com.mozip.server.policy.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {
}
