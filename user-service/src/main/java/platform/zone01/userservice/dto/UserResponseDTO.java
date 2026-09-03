package platform.zone01.userservice.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import platform.zone01.userservice.enums.Role;

@AllArgsConstructor
@Getter
public class UserResponseDTO {
    private String id;
    private String name;
    private String email;
    private Role role;
    private String avatar;
}
