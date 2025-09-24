package com.example.akka.backoffice.application;

import akka.javasdk.agent.Agent;
import akka.javasdk.agent.RemoteMcpTools;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.client.ComponentClient;

@ComponentId("greeting-agent")
public class BackOfficeAssistentAgent extends Agent {

  private static final String SYSTEM_MESSAGE =
          """
          You are a friendly AI assistant who helps users to manage their bank account.
      
          Guidelines:
          - use provided tools. Tools that are for listing, fetching or retrieving data you can perform without users approval but any create, start, capture, cancel operations must be done after user's approval
          - You need to know accountId so ask it not provided
          """.stripIndent();
  private final ComponentClient componentClient;

  public BackOfficeAssistentAgent(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public StreamEffect ask(String question) {
    return streamEffects()
      .systemMessage(SYSTEM_MESSAGE)
      .mcpTools(RemoteMcpTools.fromService("corebanking"), RemoteMcpTools.fromService("payments"))
      .userMessage(question)
      .thenReply();
  }
}