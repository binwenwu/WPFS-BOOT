package com.example.wpfsboot.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.wpfsboot.controller.dto.UserDTO;
import com.example.wpfsboot.controller.dto.UserPasswordDTO;
import com.example.wpfsboot.entity.User;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 结束乐队
 * @since 2023-07-05
 */
public interface IUserService extends IService<User> {

    UserDTO login(UserDTO userDTO);

    User register(UserDTO userDTO);

    void updatePassword(UserPasswordDTO userPasswordDTO);

    Page<User> findPage(Page<User> objectPage, String username, String email, String address);

}
