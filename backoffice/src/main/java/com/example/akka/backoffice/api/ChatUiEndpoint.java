package com.example.akka.backoffice.api;

import akka.NotUsed;
import akka.actor.Cancellable;
import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.agent.SessionHistory;
import akka.javasdk.agent.SessionMemoryEntity;
import akka.javasdk.agent.SessionMessage;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.HttpResponses;
import akka.stream.javadsl.Source;
import com.example.akka.backoffice.application.BackOfficeAssistentAgent;

import java.awt.*;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;


@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
@HttpEndpoint("/chat")
public class ChatUiEndpoint {
  @Get()
  public HttpResponse index() {
    return HttpResponses.staticResource("chat-ui/index.html");
  }
  @Get("style.css")
  public HttpResponse style() {
    return HttpResponses.staticResource("chat-ui/style.css");
  }


    public record QueryRequest(String userId, String question) {}

    private final ComponentClient componentClient;

    public ChatUiEndpoint(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

    @Post("/ask")
    public HttpResponse askStream(QueryRequest request) throws ExecutionException, InterruptedException {
        Source<String, NotUsed> responseStream = componentClient
                .forAgent()
                .inSession(request.userId())
                .tokenStream(BackOfficeAssistentAgent::ask)
                .source(request.question);
        return HttpResponses.serverSentEvents(responseStream);
    }

    public record Dialog(String userId, DialogSource dialogSource, String text, Instant timestamp) {
        public static Dialog empty() {
            return new Dialog(null, DialogSource.USER, null, null);
        }

        public enum DialogSource{USER, AI}
    }

    private record DialogListState(List<Dialog> dialogs, Instant lastTimestamp) {
        public static DialogListState empty() {
            return new DialogListState(List.of(), null);
        }

        public boolean isEmpty() {
            return lastTimestamp == null;
        }
    }

    @Get("/{userId}/history-stream")
    public HttpResponse streamCustomerChanges(String userId) {
        Source<SessionHistory, Cancellable> stateEverySeconds =
                Source.tick(Duration.ZERO, Duration.ofSeconds(1), "tick")
                        .mapAsync(1, __ ->
                                componentClient.forEventSourcedEntity(userId)
                                        .method(SessionMemoryEntity::getHistory)
                                        .invokeAsync(new SessionMemoryEntity.GetHistoryCmd(Optional.empty()))
                                        .handle((SessionHistory history, Throwable error) -> {
                                            if (error == null) {
                                                return Optional.of(history);
                                            } else if (error instanceof IllegalArgumentException) {
                                                return Optional.<SessionHistory>empty();
                                            } else {
                                                throw new RuntimeException("Unexpected error polling customer state", error);
                                            }
                                        })
                        )
                        .filter(Optional::isPresent).map(Optional::get);

        // deduplicate, so that we don't emit if the state did not change from last time
        Source<DialogListState, Cancellable> streamOfChanges =
                stateEverySeconds.scan(DialogListState.empty(),
                        (state, sessionHistory) -> {
                            var dialogs = fromSessionHistory(userId,sessionHistory);
                            if (!state.isEmpty()) {
                                dialogs = dialogs.stream().filter(dm ->  dm.timestamp().isAfter(state.lastTimestamp)).toList();
//                                    logger.info("Last timestamp: {} size: {} new: {}", state.lastTimestamp, state.dialogs().size(), dialogs.size());

                                if(dialogs.isEmpty()) {
                                    return new DialogListState(List.of(), state.lastTimestamp());
                                }
                                var lastDialog = dialogs.get(dialogs.size() - 1);
//                                    logger.info("New dialogs #1: {}, {}", dialogs.size(), lastDialog.timestamp());

                                return new DialogListState(dialogs, lastDialog.timestamp());
                            } else if(dialogs.isEmpty()){
                                return new DialogListState(List.of(), state.lastTimestamp());
                            } else {
                                var lastDialog = dialogs.get(dialogs.size() - 1);
//                                    logger.info("New dialogs #2: {}, {}", dialogs.size(), lastDialog.timestamp());
                                return new DialogListState(dialogs, lastDialog.timestamp());
                            }

                        }
                ).filterNot(state -> state.dialogs().isEmpty());

        Source<Dialog, Cancellable> streamOfChangesAsApiType =
                streamOfChanges.filter(state -> !state.dialogs.isEmpty()).flatMapConcat(dialogListState -> Source.from(dialogListState.dialogs()));
        return HttpResponses.serverSentEvents(streamOfChangesAsApiType);
    }

    private List<Dialog> fromSessionHistory(String userId, SessionHistory history) {
        return history.messages().stream().filter(sm ->
                switch (sm){
                    case SessionMessage.UserMessage m-> true;
                    case SessionMessage.AiMessage m -> true;
                    default -> false;
                }
        ).map(sm -> fromSessionHistory(userId,sm)).toList();
    }
    private Dialog fromSessionHistory(String userId, SessionMessage sm){
        return  switch (sm){
            case SessionMessage.UserMessage um -> new Dialog(userId, Dialog.DialogSource.USER,um.text(), um.timestamp());
            case SessionMessage.AiMessage aim -> new Dialog(userId, Dialog.DialogSource.AI,aim.text(), aim.timestamp());
            default -> null;
        };
    }

}
