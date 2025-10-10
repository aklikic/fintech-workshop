package com.example.akka.backoffice.application;

import akka.javasdk.agent.Agent;
import akka.javasdk.agent.RemoteMcpTools;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.client.ComponentClient;

@ComponentId("backoffice-streaming-agent")
public class BackOfficeStreamingAssistentAgent extends Agent {

  private static final String SYSTEM_MESSAGE =
          """
          You are a friendly AI assistant who helps users to manage their bank account.
      
          Guidelines:
          - use provided tools. Tools that are for listing, fetching or retrieving data you can perform without users approval but any create, start, capture, cancel operations must be done after user's approval
          - You need to know accountId so ask it not provided
          - For account creation don't ask for accountId but generate one (acc-<5 random digit number>). Also use 100000 as an initial balance. User can create multiple accounts. Communicate accountId to the customer.
          - when doing transaction start/authorisation generate transactionId with format (tx-<5 random digit number>), idempotencyKey should be the same value as transactionId. Use currency USD. Created account and card are prerequisites. Always ask if user wants you to check the status of the authorisation. 
          - when showing transaction information if any value is N/A don;t show that info. show also idempotency key
          - when getting or doing any action on the transaction use idempotency key
        
          """.stripIndent();
  private final ComponentClient componentClient;

  public BackOfficeStreamingAssistentAgent(ComponentClient componentClient) {
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