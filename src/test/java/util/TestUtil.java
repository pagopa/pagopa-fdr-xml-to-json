package util;

import lombok.experimental.UtilityClass;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Objects;

@UtilityClass
public class TestUtil {

    public String readStringFromFile(String relativePath) throws IOException {
        ClassLoader classLoader = TestUtil.class.getClassLoader();
        File file = new File(Objects.requireNonNull(classLoader.getResource(relativePath)).getPath());
        return Files.readString(file.toPath());
    }
}
