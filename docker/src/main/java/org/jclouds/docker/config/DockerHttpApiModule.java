/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jclouds.docker.config;

import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Scopes;
import com.squareup.okhttp.ConnectionSpec;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.TlsVersion;
import org.jclouds.docker.DockerApi;
import org.jclouds.docker.handlers.DockerErrorHandler;
import org.jclouds.docker.suppliers.SSLContextWithKeysSupplier;
import org.jclouds.http.HttpCommandExecutorService;
import org.jclouds.http.HttpErrorHandler;
import org.jclouds.http.HttpUtils;
import org.jclouds.http.annotation.ClientError;
import org.jclouds.http.annotation.Redirection;
import org.jclouds.http.annotation.ServerError;
import org.jclouds.http.config.ConfiguresHttpCommandExecutorService;
import org.jclouds.http.config.SSLModule;
import org.jclouds.http.okhttp.OkHttpClientSupplier;
import org.jclouds.http.okhttp.OkHttpCommandExecutorService;
import org.jclouds.rest.ConfiguresHttpApi;
import org.jclouds.rest.config.HttpApiModule;

import javax.inject.Named;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import java.util.concurrent.TimeUnit;

/**
 * Configures the Docker connection.
 */
@ConfiguresHttpApi
@ConfiguresHttpCommandExecutorService
public class DockerHttpApiModule extends HttpApiModule<DockerApi> {

   @Override
   protected void bindErrorHandlers() {
      bind(HttpErrorHandler.class).annotatedWith(Redirection.class).to(DockerErrorHandler.class);
      bind(HttpErrorHandler.class).annotatedWith(ClientError.class).to(DockerErrorHandler.class);
      bind(HttpErrorHandler.class).annotatedWith(ServerError.class).to(DockerErrorHandler.class);
   }

   /**
    * This configures SSL certificate authentication when the Docker daemon is set to use an encrypted TCP socket
    */
   @Override
   protected void configure() {
      super.configure();
      install(new SSLModule());
      bind(HttpCommandExecutorService.class).to(OkHttpCommandExecutorService.class).in(Scopes.SINGLETON);
      bind(OkHttpClient.class).toProvider(DockerOkHttpClientProvider.class).in(Scopes.SINGLETON);
  }

   private static final class DockerOkHttpClientProvider implements Provider<OkHttpClient> {
      private final HttpUtils utils;
      private final Supplier<SSLContext> sslContextWithKeysSupplier;
      private final OkHttpClientSupplier clientSupplier;
      private final HostnameVerifier hostnameVerifier;

      @Inject
      DockerOkHttpClientProvider(OkHttpClientSupplier clientSupplier, HttpUtils utils, SSLContextWithKeysSupplier sslContextWithKeysSupplier, @Named("untrusted") HostnameVerifier hostnameVerifier) {
         this.clientSupplier = clientSupplier;
         this.utils = utils;
         this.sslContextWithKeysSupplier = sslContextWithKeysSupplier;
         this.hostnameVerifier = hostnameVerifier;
      }

      @Override
      public OkHttpClient get() {
         OkHttpClient client = clientSupplier.get();
         client.setConnectTimeout(utils.getConnectionTimeout(), TimeUnit.MILLISECONDS);
         client.setReadTimeout(utils.getSocketOpenTimeout(), TimeUnit.MILLISECONDS);
         client.setFollowRedirects(false);
         ConnectionSpec tlsSpec = new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                 .tlsVersions(TlsVersion.TLS_1_0, TlsVersion.TLS_1_1, TlsVersion.TLS_1_2)
                 .build();
         ConnectionSpec cleartextSpec = new ConnectionSpec.Builder(ConnectionSpec.CLEARTEXT)
                 .build();
         client.setConnectionSpecs(ImmutableList.of(tlsSpec, cleartextSpec));
         client.setSslSocketFactory(sslContextWithKeysSupplier.get().getSocketFactory());

         if (utils.relaxHostname()) {
            client.setHostnameVerifier(hostnameVerifier);
         }
         return client;
      }
   }


}
