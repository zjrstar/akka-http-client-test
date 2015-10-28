package com.emnify.esc.scf;

import static akka.pattern.Patterns.pipe;
import static com.emnify.esc.scf.DummyHttpServer.SecureMode.HTTP;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import akka.actor.ActorSystem;
import akka.http.javadsl.HostConnectionPool;
import akka.http.javadsl.Http;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.japi.Pair;
import akka.stream.ActorMaterializer;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.testkit.JavaTestKit;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import scala.Tuple2;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;
import scala.runtime.BoxedUnit;
import scala.util.Success;
import scala.util.Try;

public class AkkaHttpTest {
  private static ActorSystem system;
  private static DummyHttpServer dummyHttpServer;

  @BeforeClass
  public static void beforeClass() throws Exception {
    dummyHttpServer = new DummyHttpServer(HTTP);
    dummyHttpServer.start();
    system = ActorSystem.create("test");
  }

  @AfterClass
  public static void afterClass() throws Exception {
    dummyHttpServer.stop();
    JavaTestKit.shutdownActorSystem(system);
  }

  @Ignore
  @Test
  public void testHttpCall() throws Exception {
    final ActorMaterializer mat = ActorMaterializer.create(system);
    new JavaTestKit(system) {{

      Future<HttpResponse> response = Http.get(system)
          .singleRequest(HttpRequest.create("http://localhost:4567/123/456"), mat);

      pipe(response, getSystem().dispatcher()).to(getRef());

      HttpResponse httpResponse = expectMsgClass(HttpResponse.class);

      StringBuilder res = new StringBuilder();
      Future<BoxedUnit> fBu =
          httpResponse.entity().getDataBytes().runForeach(a -> res.append(a.utf8String()), mat);
      Await.result(fBu, Duration.create("1s"));

      assertEquals(String.format("{\"msisdn\" : \"%s\"}", "12341234"), res.toString());
    }};
  }

  @Ignore
  @Test
  public void testHttpHostConnectionPoolCall() throws Exception {
    final ActorMaterializer mat = ActorMaterializer.create(system);
    new JavaTestKit(system) {{

      final Flow<
                Tuple2<HttpRequest, Integer>,
                Tuple2<Try<HttpResponse>, Integer>,
                HostConnectionPool> poolClientFlow =
          Http.get(system).<Integer>cachedHostConnectionPool("localhost", 4567, mat);

      final Future<Tuple2<Try<HttpResponse>, Integer>> responseFuture =
          Source
              .single(Pair.create(HttpRequest.create("/123/456"), 42).toScala())
              .via(poolClientFlow)
              .runWith(Sink.<Tuple2<Try<HttpResponse>, Integer>>head(), mat);

      pipe(responseFuture, getSystem().dispatcher()).to(getRef());

      Tuple2<Try<HttpResponse>, Integer> tuple2 = expectMsgClass(Tuple2.class);
      assertTrue(tuple2._1 instanceof Success);

      StringBuilder res = new StringBuilder();
      Future<BoxedUnit> fBu =
          tuple2._1.get().entity().getDataBytes().runForeach(a -> res.append(a.utf8String()), mat);
      Await.result(fBu, Duration.create("1s"));

      assertEquals(String.format("{\"msisdn\" : \"%s\"}", "12341234"), res.toString());
    }};
  }
}
