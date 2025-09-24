package com.example.akka.backoffice;

import akka.javasdk.DependencyProvider;
import akka.javasdk.ServiceSetup;
import akka.javasdk.annotations.Setup;
import akka.javasdk.grpc.GrpcClientProvider;
import com.example.akka.account.api.AccountGrpcEndpointClient;
import com.example.akka.payments.api.CardGrpcEndpointClient;
import com.example.akka.payments.api.TransactionGrpcEndpointClient;

@Setup
public class Bootstrap implements ServiceSetup {
  
    private final GrpcClientProvider grpcClientProvider;
  
    public Bootstrap(GrpcClientProvider grpcClientProvider) {
    this.grpcClientProvider = grpcClientProvider;
  }

    @Override
    public DependencyProvider createDependencyProvider() {
        AccountGrpcEndpointClient accountClient = grpcClientProvider.grpcClientFor(AccountGrpcEndpointClient.class, "corebanking");
        CardGrpcEndpointClient cardClient = grpcClientProvider.grpcClientFor(CardGrpcEndpointClient.class, "payments");
        TransactionGrpcEndpointClient transactionClient = grpcClientProvider.grpcClientFor(TransactionGrpcEndpointClient.class, "payments");
        return new DependencyProvider() {
            @SuppressWarnings("unchecked")
            @Override
            public <T> T getDependency(Class<T> clazz) {
                if (clazz == AccountGrpcEndpointClient.class) {
                    return (T) accountClient;
                }else if (clazz == CardGrpcEndpointClient.class) {
                    return (T) cardClient;
                }else if (clazz == TransactionGrpcEndpointClient.class) {
                    return (T) transactionClient;
                }
                return null;
            }
        };
    }

}