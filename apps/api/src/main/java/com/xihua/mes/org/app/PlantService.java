package com.xihua.mes.org.app;

import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.xihua.mes.auth.PlantDirectory;
import com.xihua.mes.common.error.ApiException;
import com.xihua.mes.common.error.Errors;
import com.xihua.mes.common.web.Locks;
import com.xihua.mes.common.web.PageQuery;
import com.xihua.mes.common.web.PageResult;
import com.xihua.mes.org.api.PlantDtos.PlantCreate;
import com.xihua.mes.org.api.PlantDtos.PlantUpdate;
import com.xihua.mes.org.api.PlantDtos.PlantView;
import com.xihua.mes.org.domain.Plant;
import com.xihua.mes.org.infra.PlantMapper;

/** Plants: global master data, not filtered by the current plant. */
@Service
public class PlantService implements PlantDirectory {

    private static final Map<String, String> SORT = Map.of(
            "code", "code", "name", "name", "createdAt", "created_at", "updatedAt", "updated_at");

    private final PlantMapper mapper;

    public PlantService(PlantMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public boolean isActive(UUID plantId) {
        return mapper.selectCount(new QueryWrapper<Plant>().eq("id", plantId).eq("enabled", true)) > 0;
    }

    public PageResult<PlantView> list(PageQuery page, String q, Boolean enabled) {
        QueryWrapper<Plant> qw = new QueryWrapper<>();
        PageQuery.fuzzy(qw, q, "code", "name");
        if (enabled != null) {
            qw.eq("enabled", enabled);
        }
        long total = mapper.selectCount(qw);
        page.applyOrderAndLimit(qw, SORT, "code");
        return new PageResult<>(mapper.selectList(qw).stream().map(PlantView::of).toList(),
                page.page(), page.size(), total);
    }

    public PlantView get(UUID id) {
        return PlantView.of(load(id));
    }

    @Transactional
    public PlantView create(PlantCreate req) {
        Errors errors = new Errors();
        String code = errors.text("code", req.code(), true, 32);
        String name = errors.text("name", req.name(), true, 128);
        errors.text("timezone", req.timezone(), false, 64);
        errors.text("remark", req.remark(), false, 500);
        errors.throwIfAny();
        if (mapper.selectCount(new QueryWrapper<Plant>().eq("code", code)) > 0) {
            throw ApiException.duplicateCode(code);
        }
        Plant p = new Plant();
        p.setId(UUID.randomUUID());
        p.setCode(code);
        p.setName(name);
        p.setTimezone(req.timezone() == null || req.timezone().isBlank() ? "Asia/Shanghai" : req.timezone());
        p.setEnabled(req.enabled() == null || req.enabled());
        p.setRemark(req.remark());
        p.setLockVersion(0);
        p.setDeleted(false);
        mapper.insert(p);
        return PlantView.of(load(p.getId()));
    }

    @Transactional
    public PlantView update(UUID id, PlantUpdate req) {
        Plant p = load(id);
        Locks.check(req.lockVersion(), p.getLockVersion());
        Errors errors = new Errors();
        p.setName(errors.text("name", req.name(), true, 128));
        errors.text("timezone", req.timezone(), true, 64);
        errors.text("remark", req.remark(), false, 500);
        errors.throwIfAny();
        p.setTimezone(req.timezone());
        p.setEnabled(req.enabled() == null || req.enabled());
        p.setRemark(req.remark());
        Locks.requireUpdated(mapper.updateById(p));
        return PlantView.of(load(id));
    }

    @Transactional
    public void delete(UUID id, Integer lockVersion) {
        Plant p = load(id);
        Locks.check(lockVersion, p.getLockVersion());
        if (mapper.hasPlantData(id)) {
            throw ApiException.inUse("工厂下已有数据，只能停用");
        }
        Locks.requireUpdated(mapper.update(null, new UpdateWrapper<Plant>()
                .set("deleted", true).eq("id", id).eq("lock_version", lockVersion)));
    }

    private Plant load(UUID id) {
        Plant p = mapper.selectById(id);
        if (p == null) {
            throw ApiException.notFound("工厂");
        }
        return p;
    }
}
