package platform.zone01.mediaservice.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import platform.zone01.commonsecurity.jwt.JwtService;
import platform.zone01.mediaservice.config.SecurityConfig;
import platform.zone01.mediaservice.exception.InvalidImageException;
import platform.zone01.mediaservice.exception.MaxUploadSizeExceededException;
import platform.zone01.mediaservice.service.MediaService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MediaController.class)
@Import({SecurityConfig.class, JwtService.class})
@TestPropertySource(properties = {
        "jwt.secret=test-secret-test-secret-test-secret-1234",
        "jwt.expiration=3600000"
})
class MediaControllerErrorHandlingTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private MediaService mediaService;

    private String seller() {
        return "Bearer " + jwtService.generateToken("seller-1", "SELLER");
    }

    private static MockMultipartFile image() {
        return new MockMultipartFile("file", "photo.png", "image/png", new byte[] {1, 2, 3});
    }

    @Test
    void unknownUrl_isNotFound_withTheStandardBody() throws Exception {
        mockMvc.perform(get("/media/nothing/here").header("Authorization", seller()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Resource not found"));
    }

    @Test
    void wrongMethod_isMethodNotAllowed() throws Exception {
        mockMvc.perform(put("/media/images/i1").header("Authorization", seller()))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().exists("Allow"));
    }

    @Test
    void anUploadWithoutAFile_isBadRequest_andNamesWhatIsMissing() throws Exception {
        mockMvc.perform(multipart("/media/images").param("productId", "p1").header("Authorization", seller()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Required part 'file' is missing"));
    }

    @Test
    void anUploadWithoutAProduct_isBadRequest_andNamesWhatIsMissing() throws Exception {
        mockMvc.perform(multipart("/media/images").file(image()).header("Authorization", seller()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Required parameter 'productId' is missing"));
    }

    @Test
    void aFileOverTheServerLimit_isPayloadTooLarge_notAServerError() throws Exception {
        when(mediaService.uploadImage(any(), anyString(), anyString()))
                .thenThrow(new org.springframework.web.multipart.MaxUploadSizeExceededException(2_097_152));

        mockMvc.perform(multipart("/media/images").file(image()).param("productId", "p1").header("Authorization", seller()))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.message").value("The uploaded file is too large"));
    }

    @Test
    void aFileOverTheLimitCheckedByTheService_isPayloadTooLargeToo() throws Exception {
        when(mediaService.uploadImage(any(), anyString(), anyString()))
                .thenThrow(new MaxUploadSizeExceededException("File exceeds 2 MB limit"));

        mockMvc.perform(multipart("/media/images").file(image()).param("productId", "p1").header("Authorization", seller()))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.message").value("File exceeds 2 MB limit"));
    }

    @Test
    void aFileThatIsNotAnImage_isBadRequest_withTheReason() throws Exception {
        when(mediaService.uploadImage(any(), anyString(), anyString()))
                .thenThrow(new InvalidImageException("Only PNG, JPEG, GIF and WebP images are allowed"));

        mockMvc.perform(multipart("/media/images").file(image()).param("productId", "p1").header("Authorization", seller()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Only PNG, JPEG, GIF and WebP images are allowed"));
    }

    @Test
    void anUnexpectedFailure_isAPlainServerError_thatHidesTheDetails() throws Exception {
        when(mediaService.getImageForProduct("p1")).thenThrow(new IllegalStateException("minio key is hunter2"));

        mockMvc.perform(get("/media/product/p1"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Unexpected error occurred"));
    }
}
