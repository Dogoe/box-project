package com.endel.demobox.controller;

import com.endel.demobox.model.Post;
import com.endel.demobox.service.PostService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
public class PostsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PostService postService;

    @Test
    public void testGetPosts() throws Exception {
        List<Post> posts = new ArrayList<>();
        Post post = new Post("test1", "test desc", "TEST", Boolean.TRUE);
        posts.add(post);

        when(postService.findAll()).thenReturn(posts);
        mockMvc.perform(get("/posts/all"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(jsonPath("$",hasSize(1)));
                //.andExpect(jsonPath("$[0].name", is(post.getName())));
                //.andExpect(jsonPath("$[0].name", is("name")));
    }
}
