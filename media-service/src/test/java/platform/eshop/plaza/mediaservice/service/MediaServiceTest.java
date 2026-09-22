package platform.eshop.plaza.mediaservice.service;

import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import platform.eshop.plaza.mediaservice.dto.MediaFileDTO;
import platform.eshop.plaza.mediaservice.dto.MediaResponseDTO;
import platform.eshop.plaza.mediaservice.entity.Media;
import platform.eshop.plaza.mediaservice.exception.InvalidImageException;
import platform.eshop.plaza.mediaservice.exception.MaxUploadSizeExceededException;
import platform.eshop.plaza.mediaservice.exception.MediaNotFoundException;
import platform.eshop.plaza.mediaservice.exception.MediaStorageException;
import platform.eshop.plaza.mediaservice.exception.NotMediaOwnerException;
import platform.eshop.plaza.mediaservice.minio.MinioProperties;
import platform.eshop.plaza.mediaservice.repository.MediaRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaServiceTest {

    private static final byte[] PNG_HEADER = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};

    @Mock
    private MinioClient minioClient;

    @Mock
    private MediaRepository mediaRepository;

    private MediaService mediaService;

    @BeforeEach
    void setUp() {
        MinioProperties props = new MinioProperties("http://minio", "key", "secret", "images");
        mediaService = new MediaService(minioClient, props, mediaRepository);
    }

    private Media media(String id, String userId) {
        return new Media(id, "path.png", "image/png", "product-1", userId);
    }

    @Test
    void getImage_returnsBytesAndContentType() throws Exception {
        GetObjectResponse stored = org.mockito.Mockito.mock(GetObjectResponse.class);
        when(stored.readAllBytes()).thenReturn(new byte[]{1, 2, 3});
        when(mediaRepository.findById("m-1")).thenReturn(Optional.of(media("m-1", "owner")));
        when(minioClient.getObject(any(GetObjectArgs.class))).thenReturn(stored);

        MediaFileDTO result = mediaService.getImage("m-1");

        assertThat(result).isEqualTo(new MediaFileDTO(new byte[]{1, 2, 3}, "image/png"));
    }

    @Test
    void getImage_throwsWhenMediaIsMissing() {
        when(mediaRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> mediaService.getImage("missing"))
                .isInstanceOf(MediaNotFoundException.class);
    }

    @Test
    void getImage_throwsWhenStorageFails() throws Exception {
        when(mediaRepository.findById("m-1")).thenReturn(Optional.of(media("m-1", "owner")));
        when(minioClient.getObject(any(GetObjectArgs.class))).thenThrow(new RuntimeException("down"));

        assertThatThrownBy(() -> mediaService.getImage("m-1"))
                .isInstanceOf(MediaStorageException.class);
    }

    @Test
    void getImageForProduct_mapsMediaToUrls() {
        when(mediaRepository.findByProductId("product-1")).thenReturn(List.of(media("m-1", "owner")));

        List<MediaResponseDTO> result = mediaService.getImageForProduct("product-1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUrl()).isEqualTo("/media/images/m-1");
    }

    @Test
    void uploadImage_storesPngAndReplacesPreviousImages() throws Exception {
        Media previous = media("old", "owner");
        when(mediaRepository.findByProductId("product-1")).thenReturn(List.of(previous));
        when(mediaRepository.save(any(Media.class))).thenAnswer(invocation -> {
            Media saved = invocation.getArgument(0);
            saved.setId("new");
            return saved;
        });
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", PNG_HEADER);

        MediaResponseDTO result = mediaService.uploadImage(file, "product-1", "owner");

        assertThat(result.getId()).isEqualTo("new");
        assertThat(result.getUrl()).isEqualTo("/media/images/new");
        verify(minioClient).putObject(any(PutObjectArgs.class));
        verify(minioClient).removeObject(any(RemoveObjectArgs.class));
        verify(mediaRepository).deleteAll(List.of(previous));
    }

    @Test
    void uploadImage_rejectsEmptyFile() {
        MockMultipartFile empty = new MockMultipartFile("file", "a.png", "image/png", new byte[0]);

        assertThatThrownBy(() -> mediaService.uploadImage(empty, "product-1", "owner"))
                .isInstanceOf(InvalidImageException.class);
    }

    @Test
    void uploadImage_rejectsFilesOverTwoMegabytes() {
        MockMultipartFile big = new MockMultipartFile("file", "a.png", "image/png", new byte[2 * 1024 * 1024 + 1]);

        assertThatThrownBy(() -> mediaService.uploadImage(big, "product-1", "owner"))
                .isInstanceOf(MaxUploadSizeExceededException.class);
    }

    @Test
    void uploadImage_rejectsNonImageContentEvenWithImageContentType() {
        MockMultipartFile fake = new MockMultipartFile("file", "a.png", "image/png", "just text".getBytes());

        assertThatThrownBy(() -> mediaService.uploadImage(fake, "product-1", "owner"))
                .isInstanceOf(InvalidImageException.class);
    }

    @Test
    void deleteImage_removesFileAndRecordForOwner() throws Exception {
        Media stored = media("m-1", "owner");
        when(mediaRepository.findById("m-1")).thenReturn(Optional.of(stored));

        mediaService.deleteImage("m-1", "owner");

        verify(minioClient).removeObject(any(RemoveObjectArgs.class));
        verify(mediaRepository).delete(stored);
    }

    @Test
    void deleteImage_rejectsOtherUsers() throws Exception {
        when(mediaRepository.findById("m-1")).thenReturn(Optional.of(media("m-1", "owner")));

        assertThatThrownBy(() -> mediaService.deleteImage("m-1", "intruder"))
                .isInstanceOf(NotMediaOwnerException.class);

        verify(minioClient, never()).removeObject(any(RemoveObjectArgs.class));
    }

    @Test
    void deleteImagesEventTrigger_removesAllImagesOfProduct() {
        List<Media> images = List.of(media("m-1", "owner"), media("m-2", "owner"));
        when(mediaRepository.findByProductId("product-1")).thenReturn(images);

        mediaService.deleteImagesEventTrigger("product-1");

        verify(mediaRepository).deleteAll(images);
    }
}
