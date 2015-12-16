/*
 * Copyright 2015 National Bank of Belgium
 * 
 * Licensed under the EUPL, Version 1.1 or - as soon they will be approved 
 * by the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 * 
 * http://ec.europa.eu/idabc/eupl
 * 
 * Unless required by applicable law or agreed to in writing, software 
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and 
 * limitations under the Licence.
 */
package be.nbb.cli.util;

import com.google.common.net.MediaType;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.annotation.Nonnull;
import lombok.experimental.UtilityClass;

/**
 *
 * @author Philippe Charles
 */
@UtilityClass
public class Utils {

    public static void printVersion(Class<?> clazz, PrintStream stream) {
        stream.println(clazz.getSimpleName() + " " + getAPIVersion(clazz));
    }

    public static String getAPIVersion(Class<?> clazz) {
        String path = "/META-INF/maven/be.nbb.demetra/demetra-cli/pom.properties";
        try (InputStream stream = clazz.getResourceAsStream(path)) {
            if (stream == null) {
                return "UNKNOWN";
            }
            Properties result = new Properties();
            result.load(stream);
            return (String) result.get("version");
        } catch (IOException e) {
            return "UNKNOWN";
        }
    }

    public static MediaType getMediaType(Optional<String> mediaType, Optional<File> file) {
        if (mediaType.isPresent()) {
            MediaType result = MediaType.parse(mediaType.get());
            if (MediaType.XML_UTF_8.is(result)) {
                return MediaType.XML_UTF_8;
            }
            if (MediaType.JSON_UTF_8.is(result)) {
                return MediaType.JSON_UTF_8;
            }
        }
        return file.isPresent() && file.get().getName().toLowerCase(Locale.ROOT).endsWith(".json")
                ? MediaType.JSON_UTF_8
                : MediaType.XML_UTF_8;
    }

    @Nonnull
    public static Optional<MediaType> getMediaType(@Nonnull File file) {
        Optional<MediaType> result = probeMediaType(file);
        return result.isPresent() ? result : getMediaTypeByExtension(file);
    }

    private static Optional<MediaType> probeMediaType(File file) {
        try {
            String contentType = Files.probeContentType(file.toPath());
            if (contentType != null) {
                return Optional.of(MediaType.parse(contentType));
            }
        } catch (IOException | IllegalArgumentException ex) {
        }
        return Optional.empty();
    }

    private static Optional<MediaType> getMediaTypeByExtension(File file) {
        String fileName = file.getName().toLowerCase(Locale.ROOT);
        if (fileName.endsWith(".json")) {
            return Optional.of(MediaType.JSON_UTF_8);
        }
        if (fileName.endsWith(".xml")) {
            return Optional.of(MediaType.XML_UTF_8);
        }
        if (fileName.endsWith(".png")) {
            return Optional.of(MediaType.PNG);
        }
        if (fileName.endsWith(".svg")) {
            return Optional.of(MediaType.SVG_UTF_8);
        }
        return Optional.empty();
    }

    public static MediaType[] supportedMediaTypes() {
        return new MediaType[]{MediaType.XML_UTF_8, MediaType.JSON_UTF_8};
    }

    static final Function<String, File> TO_FILE = (String input) -> new File(input);

    static final Function<File, String> FROM_FILE = File::toString;

    private static final class ProgressHandler {

        final int size;
        final AtomicInteger cpt = new AtomicInteger(0);
        final AtomicInteger previous = new AtomicInteger(0);

        public ProgressHandler(int size) {
            this.size = size;
        }

        public void inc(int value) {
            int percent = 100 * cpt.addAndGet(value) / size;
            int old = previous.getAndSet(percent);
            if (old != percent) {
                synchronized (System.err) {
                    System.err.println("Processed: " + percent + "%");
                }
            }
        }
    }

    public static <X, Y> Supplier<Function<X, Y>> withProgress(final Supplier<Function<X, Y>> supplier, final int size) {
        return new Supplier<Function<X, Y>>() {
            final ProgressHandler progressHandler = new ProgressHandler(size);

            @Override
            public Function<X, Y> get() {
                Function<X, Y> func = supplier.get();
                return (X input) -> {
                    Y result = func.apply(input);
                    progressHandler.inc(1);
                    return result;
                };
            }
        };
    }

    public static <X, Y> Function<List<X>, List<Y>> withProgress(final Function<List<X>, List<Y>> func, final int size) {
        return new Function<List<X>, List<Y>>() {
            final ProgressHandler progressHandler = new ProgressHandler(size);

            @Override
            public List<Y> apply(List<X> input) {
                List<Y> result = func.apply(input);
                progressHandler.inc(input.size());
                return result;
            }
        };
    }
}