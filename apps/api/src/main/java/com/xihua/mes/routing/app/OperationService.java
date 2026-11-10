package com.xihua.mes.routing.app;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.xihua.mes.auth.PermissionService;
import com.xihua.mes.common.error.ApiException;
import com.xihua.mes.common.error.ErrorCodes;
import com.xihua.mes.common.error.Errors;
import com.xihua.mes.common.web.PageQuery;
import com.xihua.mes.common.web.PageResult;
import com.xihua.mes.common.web.Scope;
import com.xihua.mes.config.app.ConfigQuery;
import com.xihua.mes.routing.api.OperationDtos.OperationCreate;
import com.xihua.mes.routing.api.OperationDtos.OperationView;
import com.xihua.mes.routing.domain.Operation;
import com.xihua.mes.routing.infra.OperationMapper;

/** Operations (shared-type). Slice 1: create / get / list only; PUT/DELETE and plant-ext come later. */
@Service
public class OperationService implements OperationQuery {

    private static final Map<String, String> SORT = Map.of("code", "code", "name", "name");

    private final OperationMapper mapper;
    private final ConfigQuery config;
    private final PermissionService permissions;

    public OperationService(OperationMapper mapper, ConfigQuery config, PermissionService permissions) {
        this.mapper = mapper;
        this.config = config;
        this.permissions = permissions;
    }

    public PageResult<OperationView> list(PageQuery page, String q) {
        QueryWrapper<Operation> qw = new QueryWrapper<>();
        PageQuery.fuzzy(qw, q, "code", "name");
        long total = mapper.selectCount(qw);
        page.applyOrderAndLimit(qw, SORT, "code");
        return new PageResult<>(mapper.selectList(qw).stream().map(OperationView::of).toList(), page.page(),
                page.size(), total);
    }

    public OperationView get(UUID id) {
        Operation o = mapper.selectById(id);
        if (o == null) {
            throw ApiException.notFound("工序");
        }
        return OperationView.of(o);
    }

    @Override
    public Optional<OperationRef> findVisible(UUID operationId) {
        if (operationId == null) {
            return Optional.empty();
        }
        Operation o = mapper.selectById(operationId);
        return o == null ? Optional.empty()
                : Optional.of(new OperationRef(o.getId(), o.getPlantId(), Boolean.TRUE.equals(o.getEnabled())));
    }

    @Transactional
    public OperationView create(OperationCreate req) {
        Scope scope = Scope.parseRequired(req.scope());
        permissions.requireSharedMaster(scope.plantIdForCreate());
        Errors errors = new Errors();
        String code = errors.text("code", req.code(), true, 32);
        String name = errors.text("name", req.name(), true, 128);
        errors.text("remark", req.remark(), false, 500);
        if (req.operationType() != null && !config.isDictValue("operation_type", req.operationType())) {
            errors.add("operationType", ErrorCodes.DICT_VALUE_INVALID, "不是有效的工序类型：" + req.operationType());
        }
        if (req.stdTimeSec() != null && req.stdTimeSec().compareTo(BigDecimal.ZERO) < 0) {
            errors.add("stdTimeSec", ErrorCodes.OUT_OF_RANGE, "标准工时不能为负");
        }
        errors.throwIfAny();
        if (mapper.codeTaken(code)) {
            throw ApiException.duplicateCode(code);
        }
        Operation o = new Operation();
        o.setId(UUID.randomUUID());
        o.setPlantId(scope.plantIdForCreate());
        o.setCode(code);
        o.setName(name);
        o.setOperationType(req.operationType());
        o.setStdTimeSec(req.stdTimeSec());
        o.setEnabled(req.enabled() == null || req.enabled());
        o.setRemark(req.remark());
        o.setLockVersion(0);
        o.setDeleted(false);
        mapper.insert(o);
        return get(o.getId());
    }
}
