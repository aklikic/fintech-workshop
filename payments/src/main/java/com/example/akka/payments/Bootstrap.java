package com.example.akka.payments;

import akka.javasdk.DependencyProvider;
import akka.javasdk.ServiceSetup;
import akka.javasdk.annotations.Setup;
import akka.javasdk.grpc.GrpcClientProvider;
import com.example.akka.account.api.AccountGrpcEndpointClient;

@Setup
public class Bootstrap implements ServiceSetup {
  
  private final GrpcClientProvider grpcClientProvider;
  
  public Bootstrap(GrpcClientProvider grpcClientProvider) {
    this.grpcClientProvider = grpcClientProvider;
  }
  
  @Override
  public DependencyProvider createDependencyProvider() {
    AccountGrpcEndpointClient accountClient = grpcClientProvider.grpcClientFor(AccountGrpcEndpointClient.class, "corebanking");
    return DependencyProvider.single(accountClient);
    
  }

}