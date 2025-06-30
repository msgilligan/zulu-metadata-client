///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 25+
//DEPS com.fasterxml.jackson.core:jackson-databind:2.17.1
//DEPS com.fasterxml.jackson.core:jackson-annotations:2.17.1

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

// See https://docs.azul.com/core/install/metadata-api for API documentation

static final String API_URL = "https://api.azul.com/metadata/v1/zulu/packages";
static final String DEFAULT_VERSION = "24";
static final String EA_VERSION = "25";

boolean verbose = false;

void main(String[] args) throws Exception {
    if (args.length == 0) {
        IO.println("Usage: ZuluQuery <jdk-version> <os> <arch> <javafx-bundled>");
        IO.println("   Defaults are: jdk-version-is-required linux-glibc x64 false");
        IO.println("   os     = linux-glibc | macos");
        IO.println("   arch   = x64 | aarch64");
        IO.println("   javafx = false | true");
        System.exit(1);
    }
    String reqVersion = args.length >= 1 ? args[0] : DEFAULT_VERSION;
    String reqOS = args.length >= 2 ? args[1] : "linux-glibc";
    String reqArch = args.length >= 3 ? args[2] : "x64";
    String reqJavaFX = args.length >= 4 ? args[3] : "false";
    String release_status = reqVersion.equals(EA_VERSION) ? "ea" : "ga";

    // Build query
    String query = API_URL
            + "?java_version=" + reqVersion
            + "&os=" + reqOS
            + "&arch=" + reqArch
            + "&hw_bitness=64"
            + "&javafx_bundled=" + reqJavaFX
            + "&crac_supported=false"
            + "&archive_type=tar.gz"
            + "&release_status=" + release_status
            + "&java_package_type=jdk"
            + "&latest=true";

    if (verbose) {
        IO.println("Querying " + query);
    }

    // Make HTTP request using built-in Java HttpClient
    HttpClient client = HttpClient.newHttpClient();
    HttpRequest request = HttpRequest.newBuilder()
            .uri(new URI(query))
            .GET()
            .build();

    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

    if (response.statusCode() != 200) {
        IO.println("HTTP error: " + response.statusCode());
        System.exit(1);
    }

    // Parse JSON using Jackson
    ObjectMapper mapper = new ObjectMapper();
    JsonNode root = mapper.readTree(response.body());

    if (root.size() == 0) {
        IO.println("No packages found.");
    }

    for (JsonNode pkg : root) {

        if (verbose) {
            IO.println(pkg + "\n");
        }        

        String product = pkg.path("product").asText();
        boolean latest = pkg.path("latest").asBoolean();
        String name = pkg.path("name").asText();
        String uuid = pkg.path("package_uuid").asText();
        String javaVersion = joinIntArrayWithDots(pkg.path("java_version"));
        String distroVersion = joinIntArrayWithDots(pkg.path("distro_version"));
//         String os = pkg.path("os").asText();
//         String arch = pkg.path("arch").asText();
        String sha256 = pkg.path("sha256_hash").asText();
        String url = pkg.path("download_url").asText();

        System.out.printf("""
            Product:        %s
            Is latest:      %b
            Name:           %s
            UUID:           %s
            Java Version:   %s
            Distro Version: %s
            Platform:       %s
            Arch:           %s
            SHA256:         %s
            URL:            %s

            """, product, latest, name, uuid, javaVersion, distroVersion, reqOS, reqArch, sha256, url);
    }
}

public static String joinIntArrayWithDots(JsonNode arrayNode) {
    if (!arrayNode.isArray()) {
        throw new IllegalArgumentException("Expected an array node");
    }

    return StreamSupport.stream(arrayNode.spliterator(), false)
            .map(JsonNode::asText) // or use .map(JsonNode::intValue) and then String::valueOf
            .collect(Collectors.joining("."));
}
