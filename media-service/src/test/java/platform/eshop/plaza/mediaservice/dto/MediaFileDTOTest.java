package platform.eshop.plaza.mediaservice.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MediaFileDTOTest {

    @Test
    void equalsAndHashCodeCompareArrayContent() {
        MediaFileDTO first = new MediaFileDTO(new byte[]{1, 2, 3}, "image/png");
        MediaFileDTO second = new MediaFileDTO(new byte[]{1, 2, 3}, "image/png");

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
    }

    @Test
    void differentContentOrTypeAreNotEqual() {
        MediaFileDTO file = new MediaFileDTO(new byte[]{1, 2, 3}, "image/png");

        assertNotEquals(file, new MediaFileDTO(new byte[]{9}, "image/png"));
        assertNotEquals(file, new MediaFileDTO(new byte[]{1, 2, 3}, "image/jpeg"));
        assertNotEquals(file, "not a dto");
    }

    @Test
    void toStringShowsArrayContent() {
        MediaFileDTO file = new MediaFileDTO(new byte[]{1, 2}, "image/png");

        assertTrue(file.toString().contains("[1, 2]"));
        assertTrue(file.toString().contains("image/png"));
    }
}
