package com.example.akka.payments;

import akka.javasdk.DependencyProvider;
import akka.javasdk.ServiceSetup;
import akka.javasdk.annotations.Setup;
import akka.javasdk.grpc.GrpcClientProvider;
import com.example.akka.account.api.AccountGrpcEndpointClient;

@Setup
public class Bootstrap implements ServiceSetup {

    private final AccountGrpcEndpointClient accountClient;

    public Bootstrap(GrpcClientProvider grpcClientProvider) {
        this.accountClient = grpcClientProvider.grpcClientFor(AccountGrpcEndpointClient.class, "corebanking");
    }

    @Override
    public DependencyProvider createDependencyProvider() {
        return new DependencyProvider() {
            @Override
            public <T> T getDependency(Class<T> cls) {
                if (cls.equals(AccountGrpcEndpointClient.class)) {
                    return (T) accountClient;
                }
                return null;
            }
        };
    }
}