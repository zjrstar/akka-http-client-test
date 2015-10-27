package akkatesthttp;

import static akkatesthttp.DummyHttpServer.SecureMode.HTTP;

import akka.actor.ActorSystem;
import akka.actor.Terminated;
import akka.testkit.JavaTestKit;
import akka.testkit.TestActorRef;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import scala.concurrent.duration.Duration;

import java.util.concurrent.TimeUnit;

public class HttpActor2Test {
  private static ActorSystem system;
  private static DummyHttpServer dummyHttpServer = new DummyHttpServer(HTTP);

  private TestActorRef<HttpActor2> httpActor2;


  @BeforeClass
  public static void beforeClass() throws Exception {
    dummyHttpServer.start();
    system = ActorSystem.create("test");
  }

  @AfterClass
  public static void afterClass() throws Exception {
    JavaTestKit.shutdownActorSystem(system);
    dummyHttpServer.stop();
  }

  @Test
  public void testHttpActorOk() {
    new JavaTestKit(system) {{
      JavaTestKit sup = new JavaTestKit(system);
      initHttpActor2();

      watch(httpActor2);

      sup.send(httpActor2, new HttpActor2.StartIndication());

      sup.expectMsgClass(Duration.create(5, TimeUnit.SECONDS), HttpActor2.HttpOk.class);

      expectMsgClass(Terminated.class);
    }};
  }

  @Test
  public void testMoCallError404() {
    new JavaTestKit(system) {{
      JavaTestKit sup = new JavaTestKit(system);
      initHttpActor2();

      watch(httpActor2);

      sup.send(httpActor2, new HttpActor2.StartIndication("404404","404404"));

      sup.expectMsgClass(Duration.create(5, TimeUnit.SECONDS), HttpActor2.HttpAbort.class);

      expectMsgClass(Terminated.class);
    }};
  }

  private void initHttpActor2() {
    httpActor2 = TestActorRef.create(system,
        HttpActor2.props());
  }
}
