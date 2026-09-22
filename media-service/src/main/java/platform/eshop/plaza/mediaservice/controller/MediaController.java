package platform.eshop.plaza.mediaservice.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import platform.eshop.plaza.mediaservice.dto.MediaFileDTO;
import platform.eshop.plaza.mediaservice.dto.MediaResponseDTO;
import platform.eshop.plaza.mediaservice.service.MediaService;

import java.time.Duration;
import java.util.List;

@RestController
@RequestMapping("/media")
public class MediaController {

    private final MediaService mediaService;

    public MediaController(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    @GetMapping("/images/{id}")
    public ResponseEntity<byte []> getImage(@PathVariable String id) {
        MediaFileDTO file = mediaService.getImage(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic())
                .body(file.bytes());
    }

    @GetMapping("/product/{productId}")
    public ResponseEntity<List<MediaResponseDTO>> getImageForProduct(@PathVariable String productId) {
        return ResponseEntity.ok(mediaService.getImageForProduct(productId));
    }

    @PostMapping("/images")
    public ResponseEntity<MediaResponseDTO> uploadImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam("productId") String productId,
            @AuthenticationPrincipal String userId) {

        MediaResponseDTO response = mediaService.uploadImage(file, productId, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/images/{id}")
    public ResponseEntity<Void> deleteImage(
            @PathVariable String id,
            @AuthenticationPrincipal String userId) {
        mediaService.deleteImage(id, userId);
        return ResponseEntity.noContent().build();
    }
}
