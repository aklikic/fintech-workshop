package com.example.akka.ui.api;

import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.http.HttpResponses;

@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint
public class StaticResourcesEndpoint {

  @Get("/")
  public HttpResponse index() {
    return HttpResponses.staticResource("index.html");
  }

  @Get("styles.css")
  public HttpResponse styles() {
    return HttpResponses.staticResource("styles.css");
  }

  @Get("app.js")
  public HttpResponse js() {
    return HttpResponses.staticResource("app.js");
  }
}