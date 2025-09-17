package com.endel.demobox.service;

import com.endel.demobox.model.Post;
import com.endel.demobox.repository.PostRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class PostService {
    final PostRepository postRepository;

    public PostService(PostRepository postRepository){
        this.postRepository = postRepository;
    }

    public Post save(Post newPost){
        return this.postRepository.save(newPost);
    }

    public List<Post> findAll(){
        return this.postRepository.findAll();
    }

    public Optional<Post> getById(Long id){
        return postRepository.findById(id);
    }
}
