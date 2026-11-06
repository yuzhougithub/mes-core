package com.xihua.mes.common.persistence;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Jackson 3 {@link JsonNode} ↔ PostgreSQL {@code jsonb}. */
@MappedTypes(JsonNode.class)
public class JsonbTypeHandler extends BaseTypeHandler<JsonNode> {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, JsonNode parameter, JdbcType jdbcType)
            throws SQLException {
        if (parameter.isNull() || parameter.isMissingNode()) {
            ps.setNull(i, java.sql.Types.OTHER);
            return;
        }
        // Types.OTHER → sent untyped; PostgreSQL casts the text to the jsonb column type.
        ps.setObject(i, MAPPER.writeValueAsString(parameter), java.sql.Types.OTHER);
    }

    @Override
    public JsonNode getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return parse(rs.getString(columnName));
    }

    @Override
    public JsonNode getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return parse(rs.getString(columnIndex));
    }

    @Override
    public JsonNode getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return parse(cs.getString(columnIndex));
    }

    private static JsonNode parse(String text) {
        return text == null ? null : MAPPER.readTree(text);
    }
}
