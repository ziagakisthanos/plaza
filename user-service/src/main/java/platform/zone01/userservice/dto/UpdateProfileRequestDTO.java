package platform.zone01.userservice.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Getter
public class UpdateProfileRequestDTO {
    @NotBlank(message = "Name is required.")
    private String name;

    private String avatar;
}
