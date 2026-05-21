package com.gsmv.ai.rag.mapper;

import com.gsmv.ai.rag.model.RagSource;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RagSourceMapper {

    @Select("SELECT * FROM rag_source ORDER BY code")
    List<RagSource> findAll();

    @Select("SELECT * FROM rag_source WHERE code = #{code}")
    RagSource findByCode(String code);
}
