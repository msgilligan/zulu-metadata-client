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

record CommandArgs(String javaVersion, String os, String arch, String hasJavaFX) {};
record ZuluProduct(String product, boolean latest, String name, String uuid, String javaVersion, String distroVersion, String url) {};

void main(String[] args) throws Exception {
    CommandArgs commandArgs  = parseArgs(args);

    // Make HTTP request using built-in Java HttpClient
    HttpClient client = HttpClient.newHttpClient();

    JsonNode root = getProducts(client, commandArgs);

    if (root.size() == 0) {
        IO.println("No packages found.");
    }

    for (JsonNode pkg : root) {

        if (verbose) {
            IO.println(pkg + "\n");
        }

        ZuluProduct zuluProduct = productFromJson(pkg);
        
        String sha256 = getSha256(client, zuluProduct);

        System.out.printf("""
            Product:        %s
            Is latest:      %b
            Name:           %s
            UUID:           %s
            Java Version:   %s
            Distro Version: %s
            OS:             %s
            Arch:           %s
            SHA256:         %s
            URL:            %s

            """, zuluProduct.product, zuluProduct.latest, zuluProduct.name, zuluProduct.uuid, zuluProduct.javaVersion, zuluProduct.distroVersion, commandArgs.os, commandArgs.arch, sha256, zuluProduct.url);
    }
}

CommandArgs parseArgs(String[] args) {
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

    return new CommandArgs(reqVersion, reqOS, reqArch, reqJavaFX);
}

HttpRequest productRequest(CommandArgs commandArgs) throws URISyntaxException {
    String release_status = commandArgs.javaVersion.equals(EA_VERSION) ? "ea" : "ga";

    // Build query
    String query = API_URL
            + "?java_version=" + commandArgs.javaVersion
            + "&os=" + commandArgs.os
            + "&arch=" + commandArgs.arch
            + "&hw_bitness=64"
            + "&javafx_bundled=" + commandArgs.hasJavaFX
            + "&crac_supported=false"
            + "&archive_type=tar.gz"
            + "&release_status=" + release_status
            + "&java_package_type=jdk"
            + "&latest=true";

    if (verbose) {
        IO.println("Querying " + query);
    }
    return HttpRequest.newBuilder()
            .uri(new URI(query))
            .GET()
            .build();
}

JsonNode getProducts(HttpClient client, CommandArgs commandArgs) throws IOException, InterruptedException, URISyntaxException {
    // Make HTTP request using client
    HttpRequest request = productRequest(commandArgs);

    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

    if (response.statusCode() != 200) {
        IO.println("HTTP error: " + response.statusCode());
        System.exit(1);
    }

    // Parse JSON using Jackson
    ObjectMapper mapper = new ObjectMapper();
    return mapper.readTree(response.body());
}

String getSha256(HttpClient client, ZuluProduct product) throws URISyntaxException, IOException, InterruptedException {
    String query = API_URL + "/" + product.uuid;
    HttpRequest request = HttpRequest.newBuilder()
            .uri(new URI(query))
            .GET()
            .build();

    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
    ObjectMapper mapper = new ObjectMapper();

    JsonNode json = mapper.readTree(response.body());
    String shaHex = json.path("sha256_hash").asText();
    byte[] sha256Hash = hexStringToByteArray(shaHex);
    return "sha256-" + Base64.getEncoder().encodeToString(sha256Hash);
}

private static byte[] hexStringToByteArray(String hex) {
    int len = hex.length();
    if (len % 2 != 0)
        throw new IllegalArgumentException("Hex string must have even length");

    byte[] data = new byte[len / 2];
    for (int i = 0; i < len; i += 2)
        data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                + Character.digit(hex.charAt(i+1), 16));
    return data;
}

ZuluProduct productFromJson(JsonNode pkg) {
    return new ZuluProduct(pkg.path("product").asText(),
            pkg.path("latest").asBoolean(),
            pkg.path("name").asText(),
            pkg.path("package_uuid").asText(),
            joinIntArrayWithDots(pkg.path("java_version")),
            joinIntArrayWithDots(pkg.path("distro_version")),
            pkg.path("download_url").asText());
}


public static String joinIntArrayWithDots(JsonNode arrayNode) {
    if (!arrayNode.isArray()) {
        throw new IllegalArgumentException("Expected an array node");
    }

    return StreamSupport.stream(arrayNode.spliterator(), false)
            .map(JsonNode::asText) // or use .map(JsonNode::intValue) and then String::valueOf
            .collect(Collectors.joining("."));
}
