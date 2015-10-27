package akkatesthttp;

import static akka.pattern.Patterns.pipe;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.Status;
import akka.actor.UntypedActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.http.javadsl.Http;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.japi.Procedure;
import akka.stream.ActorMaterializer;
import akka.stream.javadsl.Sink;
import net.minidev.json.JSONObject;
import net.minidev.json.JSONValue;
import net.minidev.json.parser.ParseException;
import scala.concurrent.Future;

public class HttpActor2 extends UntypedActor {
  final LoggingAdapter log = Logging.getLogger(getContext().system(), this);

  ActorMaterializer mat = ActorMaterializer.create(getContext());

  Data data = new Data();

  @Override
  public void preStart() {
    gotoState(this::idle);
  }

  @Override
  public void onReceive(Object message) throws Exception {
    unhandled(message);
  }

  public static Props props() {
    return Props.create(HttpActor2.class, HttpActor2::new);
  }

  // States
  private void idle(Object message) {
    log.debug("idle: {}", message);

    if (message instanceof StartIndication) {
      onStartIndication((StartIndication) message);

    } else {
      httpAbort();
    }
  }

  private void waitHttp(Object message) {
    log.debug("waitHttp: {}", message);

    if (message instanceof HttpResponse) {
      onHttpResponse((HttpResponse) message);

    } else if (message instanceof HttpChunk) {
      data.update((HttpChunk) message);

    } else if (message instanceof HttpComplete) {
      onHttpComplete();

    } else if (message instanceof Status.Failure) {
      onHttpFailure((Status.Failure) message);

    } else {
      httpAbort();
    }
  }

  private void onStartIndication(StartIndication startIndication) {
    data.update(sender());

    String url = String.format("http://localhost:4567/%s/%s",
        startIndication.num1, startIndication.num2);

    Future<HttpResponse> response = Http.get(context().system())
        .singleRequest(HttpRequest.create(url), mat);

    pipe(response, context().dispatcher()).to(self()); // receive response as a message

    gotoState(this::waitHttp);
  }

  private void onHttpResponse(HttpResponse response) {
    log.debug("HTTP Response status {}", response.status().intValue());

    data.update(response);

    // receive response chunks as messages and HttpFinished message signaling the end
    response.entity().getDataBytes().map(byteStr -> new HttpChunk(byteStr.utf8String()))
        .runWith(Sink.actorRef(self(), new HttpComplete()), mat);
  }

  private void onHttpComplete() {
    try {
      log.debug("HTTP request complete");
      if (data.getHttpStatus() != 200) {
        log.warning("HTTP Response status {}, aborting dialog...", data.getHttpStatus());
        httpAbort();
        return;
      }

      JSONObject res = (JSONObject) JSONValue.parseWithException(data.getHttpResponseData());

      String msisdn = res.getAsString("msisdn");

      if (msisdn != null) {
        log.info("HTTP OK, msisdn={}", msisdn);
        httpOk();

      } else {
        log.error("Missing 'msisdn' field in json response");
        httpAbort();
      }

    } catch (ParseException e) {
      log.error("", e);
      httpAbort();

    } finally {
      mat.shutdown();
    }
  }

  private void onHttpFailure(Status.Failure failure) {
    log.error("HTTP failure ", failure.cause());
    httpAbort();
  }

  private void gotoState(Procedure<Object> proc) {
    getContext().become(proc);
  }

  private void httpAbort() {
    data.getSender().tell(new HttpAbort(), self());
    endHttpActor();
  }

  private void httpOk() {
    data.getSender().tell(new HttpOk(), self());
    endHttpActor();
  }

  private void endHttpActor() {
    getContext().stop(getSelf());
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

  private static class Data {
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
}
