package platform.eshop.plaza.mediaservice.service;


import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import platform.eshop.plaza.mediaservice.dto.MediaFileDTO;
import platform.eshop.plaza.mediaservice.dto.MediaResponseDTO;
import platform.eshop.plaza.mediaservice.entity.Media;
import platform.eshop.plaza.mediaservice.exception.*;
import platform.eshop.plaza.mediaservice.minio.MinioProperties;
import platform.eshop.plaza.mediaservice.repository.MediaRepository;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class MediaService {

    private final MinioClient minioClient;
    private final MinioProperties props;
    private final MediaRepository mediaRepository;

    private static final Set<String> ALLOWED_TYPES =
            Set.of("image/jpeg", "image/png");

    private static final long MAX_SIZE = 2L * 1024 * 1024;

    public MediaService(MinioClient minioClient, MinioProperties props,
                        MediaRepository mediaRepository) {
        this.minioClient = minioClient;
        this.props = props;
        this.mediaRepository = mediaRepository;
    }

    public MediaFileDTO getImage(String id) {
        Media media = mediaRepository.findById(id)
                .orElseThrow(() -> new MediaNotFoundException("Media not found: " + id));
        try {
            InputStream stream = minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(props.bucket())
                            .object(media.getImagePath())
                            .build());
            byte[] bytes = stream.readAllBytes();
            return new MediaFileDTO(bytes, media.getContentType());
        } catch (Exception e) {
            throw new MediaStorageException("Failed to retrieve image");
        }
    }

    public List<MediaResponseDTO> getImageForProduct(String productId) {
        return mediaRepository.findByProductId(productId).stream()
                .map(this::toDTO)
                .toList();
    }

    public MediaResponseDTO uploadImage(MultipartFile file, String productId, String userId) {
        String contentType = validateImage(file);

        List<Media> existing = mediaRepository.findByProductId(productId);

        String extension = contentType.equals("image/png") ? ".png" : ".jpg";
        String objectKey = UUID.randomUUID() + extension;
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(props.bucket())
                    .object(objectKey)
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .contentType(contentType)
                    .build());
        } catch (Exception e) {
            throw new MediaStorageException("Failed to store image");
        }

        Media media = new Media();
        media.setImagePath(objectKey);
        media.setContentType(contentType);
        media.setProductId(productId);
        media.setUserId(userId);
        Media saved = mediaRepository.save(media);

        deleteMediaRecords(existing);
        return toDTO(saved);
    }

    public void deleteImage(String id, String userId) {
        Media media = mediaRepository.findById(id)
                .orElseThrow(() -> new MediaNotFoundException("Media not found: " + id));

        if(!media.getUserId().equals(userId)) {
            throw new NotMediaOwnerException("You can only delete your own media");
        }

        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(props.bucket())
                            .object(media.getImagePath())
                            .build());
        } catch (Exception e) {
            throw new MediaStorageException("Failed to delete image from storage");
        }

        mediaRepository.delete(media);
    }

    private void deleteMediaRecords(List<Media> mediaList) {
        for (Media media : mediaList) {
            try {
                minioClient.removeObject(RemoveObjectArgs.builder()
                        .bucket(props.bucket())
                        .object(media.getImagePath())
                        .build());
            } catch (Exception e) {
                log.error("Failed to remove {} from MinIO", media.getImagePath(), e);
            }
        }
        mediaRepository.deleteAll(mediaList);
    }

    public void deleteImagesEventTrigger(String productId) {
        List<Media> mediaList = mediaRepository.findByProductId(productId);
        deleteMediaRecords(mediaList);
        log.info("Cleaned up {} images for deleted product {}", mediaList.size(), productId);
    }

    private String validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidImageException("No file provided");
        }

        if (file.getSize() > MAX_SIZE) {
            throw new MaxUploadSizeExceededException("File exceeds 2 MB limit");
        }

        // sniff the real content type from the bytes, not the clients claim
        String detectedType;
        try {
            detectedType = detectContentType(file);
        } catch (IOException e) {
            throw new InvalidImageException("Could not read file");
        }

        if (detectedType == null || !ALLOWED_TYPES.contains(detectedType)) {
            throw new InvalidImageException("Only JPEG and PNG images are allowed");
        }
        return detectedType;
    }

    private String detectContentType(MultipartFile file) throws IOException {
        try (InputStream is = new BufferedInputStream(file.getInputStream())) {
            return URLConnection.guessContentTypeFromStream(is);
        }
    }

    private MediaResponseDTO toDTO(Media media) {
        return new MediaResponseDTO(
                media.getId(),
                media.getProductId(),
                "/media/images/" + media.getId()
        );
    }
}
