package com.gsmv.media.mapper;

import com.gsmv.media.model.MediaFile;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MediaFileMapper {

    @Insert("""
            INSERT INTO media_file (
              business_type, business_id, original_filename, stored_filename, content_type,
              size_bytes, storage_path, sha256, uploaded_by
            ) VALUES (
              #{businessType}, #{businessId}, #{originalFilename}, #{storedFilename}, #{contentType},
              #{sizeBytes}, #{storagePath}, #{sha256}, #{uploadedBy}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(MediaFile mediaFile);

    @Select("""
            SELECT *
            FROM media_file
            WHERE business_type = #{businessType}
              AND business_id = #{businessId}
            ORDER BY uploaded_at DESC
            """)
    List<MediaFile> findByBusiness(@Param("businessType") String businessType, @Param("businessId") Long businessId);

    @Select("SELECT * FROM media_file WHERE id = #{id}")
    MediaFile findById(Long id);

    @Delete("""
            DELETE FROM media_file
            WHERE business_type = #{businessType}
              AND business_id = #{businessId}
            """)
    void deleteByBusiness(@Param("businessType") String businessType, @Param("businessId") Long businessId);
}
