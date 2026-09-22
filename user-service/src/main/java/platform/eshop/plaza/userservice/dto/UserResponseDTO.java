package platform.eshop.plaza.userservice.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import platform.eshop.plaza.userservice.enums.Role;

@AllArgsConstructor
@Getter
public class UserResponseDTO {
    private String id;
    private String name;
    private String email;
    private Role role;
    private String avatar;
}
