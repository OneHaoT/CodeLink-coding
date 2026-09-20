package com.codeknest.module.account.user.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新个人资料 DTO
 */
@Data
public class UpdateProfileDTO {

    @Size(max = 255)
    private String bio;

    @Size(max = 512)
    private String website;

    @Size(max = 128)
    private String location;

    @Size(max = 128)
    private String company;

    @Size(max = 128)
    private String github;

    @Size(max = 32)
    private String username;

    @Size(max = 512)
    private String avatar;
}
