package akkatesthttp;

import static akka.pattern.Patterns.pipe;

import akka.actor.AbstractLoggingFSM;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.Status;
import akka.http.javadsl.Http;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.stream.ActorMaterializer;
import akka.stream.javadsl.Sink;
import net.minidev.json.JSONObject;
import net.minidev.json.JSONValue;
import net.minidev.json.parser.ParseException;
import scala.concurrent.Future;
import scala.concurrent.duration.FiniteDuration;

import java.util.concurrent.TimeUnit;

public class HttpActor {

  public enum AS {
    Idle,
    WaitHttp
  }

  public static class Data {
    ActorRef sender;
    StringBuilder httpResponseData = new StringBuilder();
    int httpStatus;

    public Data update(ActorRef sender) {
      this.sender = sender;
      return this;
    }

    public Data update(HttpChunk chunk) {
      httpResponseData.append(chunk.chunk);
      return this;
    }

    public String getHttpResponseData() {
      return httpResponseData.toString();
    }

    public Data update(HttpResponse response) {
      httpStatus = response.status().intValue();
      return this;
    }

    public int getHttpStatus() {
      return httpStatus;
    }

    public ActorRef getSender() {
      return sender;
    }
  }

  public static Props props() {
    return Props.create(HttpActorFSM.class, HttpActorFSM::new);
  }

  public static class HttpActorFSM extends AbstractLoggingFSM<AS, Data> {
    final ActorMaterializer mat = ActorMaterializer.create(context());

    public HttpActorFSM() {

      FiniteDuration timeout = new FiniteDuration(10, TimeUnit.SECONDS);

      // FSM definition
      startWith(AS.Idle, new Data(), timeout);

      when(AS.Idle, timeout,
          matchEvent(StartIndication.class, this::onStart)
      );

      when(AS.WaitHttp, timeout,
          matchEvent(HttpResponse.class, this::onHttpResponse)
              .event(HttpChunk.class, (event, data) -> stay().using(data.update(event)))
              .event(HttpComplete.class, (event, data) -> onHttpComplete(data))
              .event(Status.Failure.class, this::onHttpFailure)
      );

      whenUnhandled(matchEventEquals(StateTimeout(), (event, data) -> onTimeout(data)));

      initialize();
    }

    private State<AS, Data> onStart(StartIndication startIndication, Data data) {

      String url = String.format("http://localhost:4567/%s/%s",
          startIndication.num1, startIndication.num2);

      Future<HttpResponse> response = Http.get(context().system())
          .singleRequest(HttpRequest.create(url), mat);

      pipe(response, context().dispatcher()).to(self()); // receive response as a message

      return goTo(AS.WaitHttp).using(data.update(sender()));
    }

    private State<AS, Data> onHttpResponse(HttpResponse response, Data data) {
      log().debug("HTTP Response status {}", response.status().intValue());

      // receive response chunks as messages and HttpFinished message signaling the end
      response.entity().getDataBytes().map(byteStr -> new HttpChunk(byteStr.utf8String()))
          .runWith(Sink.actorRef(self(), new HttpComplete()), mat);

      return stay().using(data.update(response));
    }

    private State<AS, Data> onHttpComplete(Data data) {
      try {
        log().debug("HTTP request complete");
        if (data.getHttpStatus() != 200) {
          log().warning("HTTP Response status {}, aborting dialog...", data.getHttpStatus());
          return httpAbort(data);
        }

        JSONObject res = (JSONObject) JSONValue.parseWithException(data.getHttpResponseData());

        String msisdn = res.getAsString("msisdn");

        if (msisdn != null) {
          log().info("HTTP OK, msisdn={}", msisdn);
          return httpOk(data);

        } else {
          log().error("Missing 'msisdn' field in json response");
          return httpAbort(data);
        }

      } catch (ParseException e) {
        log().error("", e);
        return httpAbort(data);

      } finally {
        mat.shutdown();
      }
    }

    private State<AS, Data> onHttpFailure(Status.Failure failure, Data data) {
      log().error("HTTP failure ", failure.cause());
      return httpAbort(data);
    }

    protected State<AS, Data> onTimeout(Data data) {
      log().warning("State {} timeout expired", stateName());
      return httpAbort(data);
    }

    private State<AS, Data>  httpAbort(Data data) {
      data.getSender().tell(new HttpAbort(), self());
      return stop();
    }

    private State<AS, Data>  httpOk(Data data) {
      data.getSender().tell(new HttpOk(), self());
      return stop();
    }
  }

  private static class HttpChunk {
    public final String chunk;

    private HttpChunk(String chunk) {
      this.chunk = chunk;
    }
  }

  public static class StartIndication {
    public final String num1;
    public final String num2;

    public StartIndication() {
      num1 = "123";
      num2 = "456";
    }

    public StartIndication(String num1, String num2) {
      this.num1 = num1;
      this.num2 = num2;
    }

  }

  public static class HttpComplete {
  }

  public static class HttpAbort {
  }

  public static class HttpOk {
  }
}
