package io.avaje.spi.test;

import java.util.List;

import io.avaje.http.api.Client;
import io.avaje.http.api.Get;
import io.avaje.http.client.HttpException;

@Client
public interface Simple {


  @Get("users/{user}/repos")
  List<String> listRepos(String user, String other) throws HttpException;
}
