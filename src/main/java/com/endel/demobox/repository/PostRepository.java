package com.endel.demobox.repository;

import com.endel.demobox.model.Post;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PostRepository extends JpaRepository<Post, Long> {
    Optional<Post> findByName(String name);
    List<Post> findByPublished(Boolean published);
}
