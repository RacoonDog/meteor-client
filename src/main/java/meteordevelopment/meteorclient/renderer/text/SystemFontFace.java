package meteordevelopment.meteorclient.renderer.text;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class SystemFontFace extends FontFace {
    private final Path path;

    public SystemFontFace(FontInfo info, Path path) {
        super(info);

        this.path = path;
    }

    @Override
    public InputStream toStream() {
        if (!Files.isRegularFile(path)) {
            throw new RuntimeException("Tried to load font that no longer exists.");
        }

        try {
            return Files.newInputStream(path);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load font from " + path + ".", e);
        }
    }

    @Override
    public String toString() {
        return super.toString() + " (" + path.toString() + ")";
    }
}
