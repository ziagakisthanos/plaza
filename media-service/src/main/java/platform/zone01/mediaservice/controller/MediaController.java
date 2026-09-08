package platform.zone01.mediaservice.controller;

import org.apache.coyote.Response;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import platform.zone01.mediaservice.dto.MediaResponseDTO;
import platform.zone01.mediaservice.service.MediaService;

@RestController
@RequestMapping("/media")
public class MediaController {

    private final MediaService mediaService;

    public MediaController(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    @PostMapping("/images")
    public ResponseEntity<MediaResponseDTO> uploadImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam("productId") String productId,
            @AuthenticationPrincipal String userId) {

        MediaResponseDTO response = mediaService.uploadImage(file, productId, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

}
