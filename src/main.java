import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.*;
import java.time.Duration;
import java.time.Instant;
import java.util.stream.Stream;

@SpringBootApplication
@EnableScheduling
public class FileServer {

    private static final long DELETE_DELAY = 259200; // 3 days in seconds
    private static final String API_KEY = "hello";
    private static final String PAGES_DIRECTORY = "pages";

    public static void main(String[] args) {
        SpringApplication.run(FileServer.class, args);
    }

    @PostConstruct
    public void init() throws IOException {
        Path pagesDir = Paths.get(PAGES_DIRECTORY);
        if (!Files.exists(pagesDir)) {
            Files.createDirectory(pagesDir);
        }
    }

    public static String getTimestamp() {
        return String.valueOf(Instant.now().getEpochSecond());
    }

    @RestController
    public static class FileController {

        @PostMapping("/upload_page")
        public ResponseEntity<String> uploadPage(
                @RequestHeader(value = "api_key", required = false) String apiKey,
                @RequestHeader(value = "user_id", required = false) String userId,
                @RequestBody byte[] body) {

            // Authorize request
            if (apiKey == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("no api key");
            }
            if (!apiKey.equals(API_KEY)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("invalid api key");
            }

            // Check user ID
            if (userId == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("no user_id");
            }

            String path = getTimestamp() + "_" + userId + ".html";
            Path filePath = Paths.get(PAGES_DIRECTORY, path);

            try {
                Files.write(filePath, body);
                return ResponseEntity.ok(path);
            } catch (IOException e) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.toString());
            }
        }

        @GetMapping("/pages/{filename}")
        public ResponseEntity<Resource> serveFile(@PathVariable String filename) {
            try {
                Path filePath = Paths.get(PAGES_DIRECTORY, filename);
                Resource resource = new UrlResource(filePath.toUri());

                if (resource.exists() && resource.isReadable()) {
                    return ResponseEntity.ok().body(resource);
                } else {
                    return ResponseEntity.notFound().build();
                }
            } catch (IOException e) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        }
    }

    @Component
    public static class FileCleanupTask {

        @Scheduled(fixedDelay = 10000) // 10 seconds
        public void cleanupFiles() {
            try (Stream<Path> paths = Files.list(Paths.get(PAGES_DIRECTORY))) {
                paths.forEach(file -> {
                    try {
                        BasicFileAttributes attr = Files.readAttributes(file, BasicFileAttributes.class);
                        Instant creationTime = attr.creationTime().toInstant();
                        long fileAge = Duration.between(creationTime, Instant.now()).getSeconds();

                        if (fileAge > DELETE_DELAY) {
                            System.out.println("deleting file " + file.toString());
                            Files.delete(file);
                        }
                    } catch (IOException e) {
                        System.err.println("Error processing file " + file + ": " + e.getMessage());
                    }
                });
            } catch (IOException e) {
                System.err.println("Error listing directory: " + e.getMessage());
            }
        }
    }
}
