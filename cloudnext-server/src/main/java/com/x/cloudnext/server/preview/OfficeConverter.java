package com.x.cloudnext.server.preview;

import java.nio.file.Path;

public interface OfficeConverter extends AutoCloseable {
    void convert(Path input, Path output);
    @Override void close();
}
