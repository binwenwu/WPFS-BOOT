package com.example.wpfsboot.mapper;

import com.example.wpfsboot.entity.Role;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author 结束乐队
 * @since 2023-07-05
 */
public interface RoleMapper extends BaseMapper<Role> {
    @Select("select id from sys_role where flag = #{flag}")
    Integer selectByFlag(@Param("flag") String flag);
}
