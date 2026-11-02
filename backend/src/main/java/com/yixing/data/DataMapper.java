package com.yixing.data;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.*;

@Mapper
// ${table} 是直接替换表名，因此只能由 Store 传入已检查的表名。
// #{id} 等写法是参数绑定，数据值不会作为 SQL 代码执行。
public interface DataMapper {
    @Select("SELECT * FROM ${table} ORDER BY id DESC")
    List<Map<String, Object>> list(@Param("table") String table);
    @Select("SELECT * FROM ${table} WHERE id = #{id}")
    Map<String, Object> get(@Param("table") String table, @Param("id") long id);
    @Select("SELECT * FROM ${table} WHERE id = #{id} FOR UPDATE")
    Map<String, Object> lock(@Param("table") String table, @Param("id") long id);
    @InsertProvider(type = Statements.class, method = "insert")
    // 新增成功后取回自增编号，供订单、流水等关联记录使用。
    @Options(useGeneratedKeys = true, keyProperty = "data.id")
    int insert(@Param("table") String table, @Param("data") Map<String, Object> data);
    @UpdateProvider(type = Statements.class, method = "update")
    int update(@Param("table") String table, @Param("id") long id, @Param("data") Map<String, Object> data);
}
