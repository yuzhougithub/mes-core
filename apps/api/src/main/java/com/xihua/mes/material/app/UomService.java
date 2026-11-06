package com.xihua.mes.material.app;

import java.util.Map;
import java.util.UUID;

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
import com.xihua.mes.material.api.UomDtos.UomCreate;
import com.xihua.mes.material.api.UomDtos.UomUpdate;
import com.xihua.mes.material.api.UomDtos.UomView;
import com.xihua.mes.material.domain.Uom;
import com.xihua.mes.material.infra.UomMapper;

/** Units of measure: global master data. */
@Service
public class UomService {

    private static final Map<String, String> SORT = Map.of("code", "code", "name", "name");

    private final UomMapper mapper;

    public UomService(UomMapper mapper) {
        this.mapper = mapper;
    }

    public PageResult<UomView> list(PageQuery page, String q, Boolean enabled) {
        QueryWrapper<Uom> qw = new QueryWrapper<>();
        PageQuery.fuzzy(qw, q, "code", "name");
        if (enabled != null) {
            qw.eq("enabled", enabled);
        }
        long total = mapper.selectCount(qw);
        page.applyOrderAndLimit(qw, SORT, "code");
        return new PageResult<>(mapper.selectList(qw).stream().map(UomView::of).toList(), page.page(), page.size(),
                total);
    }

    public UomView get(UUID id) {
        return UomView.of(load(id));
    }

    public boolean exists(UUID id) {
        return id != null && mapper.selectById(id) != null;
    }

    @Transactional
    public UomView create(UomCreate req) {
        Errors errors = new Errors();
        String code = errors.text("code", req.code(), true, 16);
        String name = errors.text("name", req.name(), true, 32);
        int decimals = decimals(req.decimals(), errors);
        errors.throwIfAny();
        if (mapper.selectCount(new QueryWrapper<Uom>().eq("code", code)) > 0) {
            throw ApiException.duplicateCode(code);
        }
        Uom u = new Uom();
        u.setId(UUID.randomUUID());
        u.setCode(code);
        u.setName(name);
        u.setDecimals(decimals);
        u.setEnabled(req.enabled() == null || req.enabled());
        u.setLockVersion(0);
        u.setDeleted(false);
        mapper.insert(u);
        return UomView.of(load(u.getId()));
    }

    @Transactional
    public UomView update(UUID id, UomUpdate req) {
        Uom u = load(id);
        Locks.check(req.lockVersion(), u.getLockVersion());
        Errors errors = new Errors();
        u.setName(errors.text("name", req.name(), true, 32));
        u.setDecimals(decimals(req.decimals(), errors));
        errors.throwIfAny();
        u.setEnabled(req.enabled() == null || req.enabled());
        Locks.requireUpdated(mapper.updateById(u));
        return UomView.of(load(id));
    }

    @Transactional
    public void delete(UUID id, Integer lockVersion) {
        Uom u = load(id);
        Locks.check(lockVersion, u.getLockVersion());
        if (mapper.inUse(id)) {
            throw ApiException.inUse("计量单位已被引用，只能停用");
        }
        Locks.requireUpdated(mapper.update(null, new UpdateWrapper<Uom>()
                .set("deleted", true).eq("id", id).eq("lock_version", lockVersion)));
    }

    private static int decimals(Integer raw, Errors errors) {
        int d = raw == null ? 0 : raw;
        if (d < 0 || d > 6) {
            errors.add("decimals", ErrorCodes.OUT_OF_RANGE, "decimals 须在 0–6");
        }
        return d;
    }

    private Uom load(UUID id) {
        Uom u = mapper.selectById(id);
        if (u == null) {
            throw ApiException.notFound("计量单位");
        }
        return u;
    }
}
