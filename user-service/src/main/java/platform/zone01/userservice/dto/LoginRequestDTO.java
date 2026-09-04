package platform.zone01.userservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Getter
public class LoginRequestDTO {
    @NotBlank(message = "Please enter a valid email")
    @Email(message = "Email must be valid")
    private String email;

    @NotBlank(message = "Please enter your password")
    private String password;
}
