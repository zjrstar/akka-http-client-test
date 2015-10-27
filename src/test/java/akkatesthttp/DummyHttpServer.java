package akkatesthttp;

import spark.Spark;

import java.net.URISyntaxException;
import java.net.URL;

public class DummyHttpServer {
  private final int port;
  private SecureMode secureMode;

  public DummyHttpServer() {
    this.port = 0;
    secureMode = SecureMode.HTTP;
  }

  public DummyHttpServer(int port) {
    this.port = port;
    secureMode = SecureMode.HTTP;
  }

  public DummyHttpServer(SecureMode secureMode) {
    port = 0;
    this.secureMode = secureMode;
  }

  public DummyHttpServer(int port, SecureMode secureMode) {
    this.port = port;
    this.secureMode = secureMode;
  }

  public void start() {
    if (port > 0) {
      Spark.port(port);
    }

    if (secureMode == SecureMode.HTTPS) {
      Spark.secure(getResourcePath("keystore.jks"), "123456", null, null);
    }

    Spark.get("/404404/404404", (req, res) -> {

      res.body("BAD REQUEST");

      res.type("text/plain");

      res.status(404);

      return res.body();
    });

    Spark.get("/:calling/:called", (req, res) -> {

      res.body(String.format("{\"msisdn\" : \"%s\"}", "12341234"));

      res.type("application/json");

      res.status(200);

      return res.body();
    });
  }

  public void stop() {
    Spark.stop();
  }

  private String getResourcePath(String resource) {
    try {
      URL resUrl = this.getClass().getClassLoader().getResource(resource);
      if (resUrl != null) {
        return resUrl.toURI().getPath();

      } else {
        throw new RuntimeException(String.format("Resource %s not resolved", resource));
      }

    } catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }
  }

  public enum SecureMode {
    HTTP, HTTPS
  }
}
