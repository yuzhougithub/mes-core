package com.xihua.mes.org.api;

import java.net.URI;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.xihua.mes.auth.RequiresPerm;
import com.xihua.mes.common.web.PageQuery;
import com.xihua.mes.common.web.PageResult;
import com.xihua.mes.org.api.PostDtos.PostView;
import com.xihua.mes.org.api.PostDtos.PostWrite;
import com.xihua.mes.org.app.PostService;

@RestController
@RequestMapping("/api/v1/posts")
public class PostController {

    private final PostService posts;

    public PostController(PostService posts) {
        this.posts = posts;
    }

    @GetMapping
    @RequiresPerm("org:post:list")
    public PageResult<PostView> list(HttpServletRequest request, @RequestParam(required = false) String q) {
        return posts.list(PageQuery.from(request), q);
    }

    @PostMapping
    @RequiresPerm(value = "org:post:create", global = true)
    public ResponseEntity<PostView> create(@RequestBody(required = false) PostWrite req) {
        PostView created = posts.create(req);
        return ResponseEntity.created(URI.create("/api/v1/posts/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @RequiresPerm(value = "org:post:update", global = true)
    public PostView update(@PathVariable UUID id, @RequestBody(required = false) PostWrite req) {
        return posts.update(id, req);
    }

    @DeleteMapping("/{id}")
    @RequiresPerm(value = "org:post:delete", global = true)
    public ResponseEntity<Void> delete(@PathVariable UUID id, @RequestParam(required = false) Integer lockVersion) {
        posts.delete(id, lockVersion);
        return ResponseEntity.noContent().build();
    }
}
