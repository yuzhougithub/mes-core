package com.xihua.mes.config.infra;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** Read-only lookups; {@code cfg_dict_item} / {@code cfg_file_object} rows are shared + current plant. */
@Mapper
public interface ConfigLookupMapper {

    @Select("""
            SELECT EXISTS (
              SELECT 1 FROM cfg_dict_item i
              JOIN cfg_dict_type t ON t.id = i.dict_type_id
              WHERE t.code = #{typeCode} AND i.value = #{value}
                AND i.enabled AND NOT i.deleted AND NOT t.deleted)
            """)
    boolean dictValueExists(@Param("typeCode") String typeCode, @Param("value") String value);

    /** Visible defect code (shared or current plant): id + plant_id, null if missing/invisible. */
    @Select("SELECT d.id, d.plant_id FROM cfg_defect_code d WHERE d.id = #{id} AND NOT d.deleted")
    com.xihua.mes.common.web.ScopedRef defectCode(@Param("id") java.util.UUID id);

    @Select("SELECT f.content_type FROM cfg_file_object f WHERE f.object_key = #{objectKey}")
    String fileContentType(@Param("objectKey") String objectKey);
}
