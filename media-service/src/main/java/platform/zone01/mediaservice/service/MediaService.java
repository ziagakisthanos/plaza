package platform.zone01.mediaservice.service;


import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import platform.zone01.mediaservice.dto.MediaResponseDTO;
import platform.zone01.mediaservice.entity.Media;
import platform.zone01.mediaservice.exception.InvalidImageException;
import platform.zone01.mediaservice.exception.MaxUploadSizeExceededException;
import platform.zone01.mediaservice.exception.MediaStorageException;
import platform.zone01.mediaservice.minio.MinioProperties;
import platform.zone01.mediaservice.repository.MediaRepository;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.util.Set;
import java.util.UUID;

@Service
public class MediaService {

    private final MinioClient minioClient;
    private final MinioProperties props;
    private final MediaRepository mediaRepository;

    private static final Set<String> ALLOWED_TYPES =
            Set.of("image/jpeg", "image/png");

    private static final long MAX_SIZE = 2 * 1024 * 1024; // 2 MB

    public MediaService(MinioClient minioClient, MinioProperties props,
                        MediaRepository mediaRepository) {
        this.minioClient = minioClient;
        this.props = props;
        this.mediaRepository = mediaRepository;
    }

    public MediaResponseDTO uploadImage(MultipartFile file, String productId, String userId) {
        String contentType = validateImage(file);

        String extension = contentType.equals("image/png") ? ".png" : ".jpg";
        String objectKey = UUID.randomUUID() + extension;

        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(props.bucket())
                            .object(objectKey)
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType(contentType)
                            .build()
            );
        } catch (Exception e) {
            throw new MediaStorageException("Failed to store image");
        }

        Media media = new Media();
        media.setImagePath(objectKey);
        media.setContentType(contentType);
        media.setProductId(productId);
        media.setUserId(userId);
        Media saved = mediaRepository.save(media);

        return toDTO(saved);
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

        if (!ALLOWED_TYPES.contains(detectedType)) {
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
                "media/images/" + media.getId()
        );
    }
}
