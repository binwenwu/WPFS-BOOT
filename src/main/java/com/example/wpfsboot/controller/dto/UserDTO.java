package com.example.wpfsboot.controller.dto;

import com.example.wpfsboot.entity.Menu;
import lombok.Data;

import java.util.List;

/**
 * @author 结束乐队
 * 接受前端登录请求的参数
 */
@Data
public class UserDTO {

    private Integer id;
    private String username;
    private String password;
    private String nickname;
    private String email;
    private String phone;
    private String address;
    private String role;
    private String token;
    private List<Menu> menus;

}
