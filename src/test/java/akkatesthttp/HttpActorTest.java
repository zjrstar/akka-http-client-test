package akkatesthttp;

import static akkatesthttp.DummyHttpServer.SecureMode.HTTP;

import akka.actor.ActorSystem;
import akka.actor.Terminated;
import akka.stream.ActorMaterializer;
import akka.stream.Materializer;
import akka.testkit.JavaTestKit;
import akka.testkit.TestActorRef;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import scala.concurrent.duration.Duration;

import java.util.concurrent.TimeUnit;

public class HttpActorTest {
  private static ActorSystem system;
  private static Materializer materializer;
  private static DummyHttpServer dummyHttpServer = new DummyHttpServer(HTTP);

  private TestActorRef<HttpActor.HttpActorFSM> fsmActor;


  @BeforeClass
  public static void beforeClass() throws Exception {
    dummyHttpServer.start();
    system = ActorSystem.create("test");
    materializer = ActorMaterializer.create(system);
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
      initFsmActor();

      watch(fsmActor);

      sup.send(fsmActor, new HttpActor.StartIndication());

      sup.expectMsgClass(Duration.create(5, TimeUnit.SECONDS), HttpActor.HttpOk.class);

      expectMsgClass(Terminated.class);
    }};
  }

  @Test
  public void testMoCallError404() {
    new JavaTestKit(system) {{
      JavaTestKit sup = new JavaTestKit(system);
      initFsmActor();

      watch(fsmActor);

      sup.send(fsmActor, new HttpActor.StartIndication("404404","404404"));

      sup.expectMsgClass(Duration.create(5, TimeUnit.SECONDS), HttpActor.HttpAbort.class);

      expectMsgClass(Terminated.class);
    }};
  }

  private void initFsmActor() {
    fsmActor = TestActorRef.create(system,
        HttpActor.props(materializer));
  }
}
