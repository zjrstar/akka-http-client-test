package com.emnify.esc.scf;

import static akka.pattern.Patterns.pipe;
import static com.emnify.esc.scf.DummyHttpServer.SecureMode.HTTPS;
import static org.junit.Assert.assertEquals;

import akka.actor.ActorSystem;
import akka.http.javadsl.Http;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.scaladsl.HttpsContext;
import akka.stream.ActorMaterializer;
import akka.stream.javadsl.Sink;
import akka.testkit.JavaTestKit;
import com.typesafe.config.ConfigFactory;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import scala.concurrent.Future;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class AkkaHttpsTest {
  private static ActorSystem system;
  private static DummyHttpServer dummyHttpServer;

  @BeforeClass
  public static void beforeClass() throws Exception {
    dummyHttpServer = new DummyHttpServer(HTTPS);
    dummyHttpServer.start();
    system = ActorSystem.create("test", ConfigFactory.parseResources("mo-call-handling.conf"));
    setHttpsContext(system);
  }

  @AfterClass
  public static void afterClass() throws Exception {
    JavaTestKit.shutdownActorSystem(system);
    dummyHttpServer.stop();
  }

  @Ignore
  @Test
  public void testHttpsCall() throws Exception {
    final ActorMaterializer mat = ActorMaterializer.create(system);
    new JavaTestKit(system) {{

      JavaTestKit moCallProbe = new JavaTestKit(system);

      Future<HttpResponse> response = Http.get(system)
          .singleRequest(HttpRequest.create("https://localhost:4567/123/456"), mat);
      pipe(response, getSystem().dispatcher()).to(moCallProbe.getRef());

      HttpResponse httpResponse = moCallProbe.expectMsgClass(HttpResponse.class);

      StringBuilder res = new StringBuilder();
      httpResponse.entity().getDataBytes().map(byteStr -> new HttpChunk(byteStr.utf8String()))
          .runWith(Sink.actorRef(moCallProbe.getRef(), new HttpFinished()), mat);

      HttpChunk httpChunk = moCallProbe.expectMsgClass(HttpChunk.class);
      res.append(httpChunk.chunk);

      moCallProbe.expectMsgClass(HttpFinished.class);

      assertEquals(String.format("{\"msisdn\" : \"%s\"}", "12341234"), res.toString());
    }};
  }

  @Ignore
  @Test
  public void testHttpsCall2() throws Exception {
    final ActorMaterializer mat = ActorMaterializer.create(system);
    new JavaTestKit(system) {{

      JavaTestKit moCallProbe = new JavaTestKit(system);

      Future<HttpResponse> response = Http.get(system)
          .singleRequest(HttpRequest.create("https://localhost:4567/404404/404404"), mat);
      pipe(response, getSystem().dispatcher()).to(moCallProbe.getRef());

      HttpResponse httpResponse = moCallProbe.expectMsgClass(HttpResponse.class);

      StringBuilder res = new StringBuilder();
      httpResponse.entity().getDataBytes().map(byteStr -> new HttpChunk(byteStr.utf8String()))
          .runWith(Sink.actorRef(moCallProbe.getRef(), new HttpFinished()), mat);

      HttpChunk httpChunk = moCallProbe.expectMsgClass(HttpChunk.class);
      res.append(httpChunk.chunk);

      moCallProbe.expectMsgClass(HttpFinished.class);

      assertEquals("BAD REQUEST", res.toString());
    }};
  }

  private static void setHttpsContext(ActorSystem system)
      throws KeyManagementException, NoSuchAlgorithmException {

    SSLContext sslContext = SSLContext.getInstance("TLS");
    TrustManager[] trustManagers = {new X509TrustManager() {
      @Override
      public void checkClientTrusted(X509Certificate[] x509Certificates, String s)
          throws CertificateException {
      }

      @Override
      public void checkServerTrusted(X509Certificate[] x509Certificates, String s)
          throws CertificateException {
      }

      @Override
      public X509Certificate[] getAcceptedIssuers() {
        return new X509Certificate[0];
      }
    }};

    sslContext.init(new KeyManager[0], trustManagers, null);

    HttpsContext httpsContext = new akka.http.scaladsl.HttpsContext(
        sslContext,
        scala.Option.empty(),
        scala.Option.empty(),
        scala.Option.empty(),
        scala.Option.empty()
    );

    Http.get(system).setDefaultClientHttpsContext(httpsContext);
  }

  private final class HttpChunk {
    public final String chunk;

    private HttpChunk(String chunk) {
      this.chunk = chunk;
    }
  }

  private final class HttpFinished {
  }
}
