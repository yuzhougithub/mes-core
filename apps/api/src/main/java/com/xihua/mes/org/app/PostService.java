package com.xihua.mes.org.app;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.xihua.mes.common.error.ApiException;
import com.xihua.mes.common.error.ErrorCodes;
import com.xihua.mes.common.error.Errors;
import com.xihua.mes.common.web.Locks;
import com.xihua.mes.common.web.PageQuery;
import com.xihua.mes.common.web.PageResult;
import com.xihua.mes.org.api.PostDtos.PostView;
import com.xihua.mes.org.api.PostDtos.PostWrite;
import com.xihua.mes.org.domain.Post;
import com.xihua.mes.org.infra.PostMapper;

@Service
public class PostService {

    private static final Map<String, String> SORT = Map.of(
            "code", "code", "name", "name", "sortNo", "sort_no", "createdAt", "created_at");

    private final PostMapper posts;

    public PostService(PostMapper posts) {
        this.posts = posts;
    }

    public PageResult<PostView> list(PageQuery page, String q) {
        QueryWrapper<Post> qw = new QueryWrapper<>();
        PageQuery.fuzzy(qw, q, "code", "name");
        long total = posts.selectCount(qw);
        page.applyOrderAndLimit(qw, SORT, "sortNo");
        return new PageResult<>(posts.selectList(qw).stream().map(PostView::of).toList(),
                page.page(), page.size(), total);
    }

    @Transactional
    public PostView create(PostWrite req) {
        requireBody(req);
        Errors errors = new Errors();
        String code = trim(errors.text("code", req.code(), true, 32));
        String name = trim(errors.text("name", req.name(), true, 64));
        errors.throwIfAny();
        Post row = new Post();
        row.setId(UUID.randomUUID());
        row.setCode(code);
        row.setName(name);
        row.setSortNo(req.sortNo() == null ? 0 : req.sortNo());
        row.setEnabled(req.enabled() == null || req.enabled());
        row.setLockVersion(0);
        row.setDeleted(false);
        try {
            posts.insert(row);
        } catch (DuplicateKeyException ex) {
            throw ApiException.duplicateCode(code);
        }
        return PostView.of(require(row.getId()));
    }

    @Transactional
    public PostView update(UUID id, PostWrite req) {
        requireBody(req);
        Post row = require(id);
        Locks.check(req.lockVersion(), row.getLockVersion());
        Errors errors = new Errors();
        String name = trim(errors.text("name", req.name(), true, 64));
        errors.throwIfAny();
        row.setName(name);
        row.setSortNo(req.sortNo() == null ? row.getSortNo() : req.sortNo());
        row.setEnabled(req.enabled() == null ? Boolean.TRUE.equals(row.getEnabled()) : req.enabled());
        Locks.requireUpdated(posts.updateById(row));
        return PostView.of(require(id));
    }

    @Transactional
    public void delete(UUID id, Integer lockVersion) {
        Post row = require(id);
        Locks.check(lockVersion, row.getLockVersion());
        if (posts.personCount(id) > 0) {
            throw ApiException.inUse("岗位已被人员引用");
        }
        Locks.requireUpdated(posts.update(null, new UpdateWrapper<Post>()
                .set("deleted", true).eq("id", id).eq("lock_version", lockVersion)));
    }

    private Post require(UUID id) {
        Post row = id == null ? null : posts.selectById(id);
        if (row == null) {
            throw ApiException.notFound("岗位");
        }
        return row;
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    private static void requireBody(Object body) {
        if (body == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCodes.BAD_REQUEST, "请求体不是合法 JSON 或字段类型不符",
                    List.of());
        }
    }
}
