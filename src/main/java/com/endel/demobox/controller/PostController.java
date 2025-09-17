package com.endel.demobox.controller;

import com.endel.demobox.model.Post;
import com.endel.demobox.service.PostService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/posts")
public class PostController {
    final PostService postService;

    public PostController(PostService postService){
        this.postService = postService;
    }

    @GetMapping("/all")
    public ResponseEntity<List<Post>> getAllPosts(){
        List<Post> posts =  postService.findAll();
        return ResponseEntity.ok(posts);
    }

    @PostMapping("/save")
    public ResponseEntity<Post> savePost(@RequestBody Post post){
        return new ResponseEntity<>(postService.save(post), HttpStatus.CREATED);
    }
    @GetMapping("/{idPost}")
    public ResponseEntity<Post> getByIdPost(@PathVariable Long idPost){
        Optional<Post> postOptional = postService.getById(idPost);
        return postOptional.map(ResponseEntity::ok).orElseGet(() -> (ResponseEntity<Post>) ResponseEntity.notFound());

    }
}
