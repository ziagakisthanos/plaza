package platform.eshop.plaza.userservice.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import platform.eshop.plaza.commonsecurity.jwt.JwtService;
import platform.eshop.plaza.userservice.config.SecurityConfig;
import platform.eshop.plaza.userservice.exception.EmailAlreadyExistsException;
import platform.eshop.plaza.userservice.service.UserService;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {AuthController.class, UserController.class})
@Import({SecurityConfig.class, JwtService.class})
@TestPropertySource(properties = {
        "jwt.secret=test-secret-test-secret-test-secret-1234",
        "jwt.expiration=3600000"
})
class UserControllerErrorHandlingTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private UserService userService;

    private String client() {
        return "Bearer " + jwtService.generateToken("client-1", "CLIENT");
    }

    @Test
    void unknownUrl_isNotFound_withTheStandardBody() throws Exception {
        mockMvc.perform(get("/users/nothing-here").header("Authorization", client()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Resource not found"))
                .andExpect(jsonPath("$.path").value("/users/nothing-here"));
    }

    @Test
    void malformedJson_isBadRequest() throws Exception {
        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("{bad"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed request body"));
    }

    @Test
    void wrongContentType_isUnsupportedMediaType() throws Exception {
        mockMvc.perform(post("/auth/login").contentType(MediaType.TEXT_PLAIN).content("hello"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.message", containsString("text/plain")));
    }

    @Test
    void wrongMethodOnALoginUrl_isMethodNotAllowed() throws Exception {
        mockMvc.perform(get("/auth/login"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", "POST"))
                .andExpect(jsonPath("$.message").value("Method 'GET' is not supported for this URL"));
    }

    @Test
    void wrongMethodOnTheProfile_isMethodNotAllowed() throws Exception {
        mockMvc.perform(delete("/users/me").header("Authorization", client()))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().exists("Allow"));
    }

    @Test
    void anEmptyRegistration_listsWhatIsMissing() throws Exception {
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    @Test
    void anAlreadyUsedEmail_isAConflict() throws Exception {
        when(userService.register(any())).thenThrow(new EmailAlreadyExistsException("carl@x.io"));

        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Carl\",\"email\":\"carl@x.io\",\"password\":\"secret12\",\"role\":\"CLIENT\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("carl@x.io")));
    }

    @Test
    void anUnexpectedFailure_isAPlainServerError_thatHidesTheDetails() throws Exception {
        when(userService.register(any())).thenThrow(new IllegalStateException("mongo password is hunter2"));

        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Carl\",\"email\":\"carl@x.io\",\"password\":\"secret12\",\"role\":\"CLIENT\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Unexpected error occurred"));
    }
}
