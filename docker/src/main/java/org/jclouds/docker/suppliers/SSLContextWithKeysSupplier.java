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
package org.jclouds.docker.suppliers;

import com.google.common.base.Charsets;
import com.google.common.base.Supplier;
import com.google.common.io.Files;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.jclouds.docker.DockerApiMetadata;
import org.jclouds.domain.Credentials;
import org.jclouds.http.HttpUtils;
import org.jclouds.http.config.SSLModule;
import org.jclouds.location.Provider;
import org.jclouds.util.Closeables2;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedKeyManager;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.base.Throwables.propagate;

@Singleton
public class SSLContextWithKeysSupplier implements Supplier<SSLContext> {
   private final TrustManager[] trustManager;
   private final Supplier<Credentials> creds;


   @Inject
   SSLContextWithKeysSupplier(@Provider Supplier<Credentials> creds, @Named(DockerApiMetadata.DOCKER_CA_CERT_PATH) String caCertPath, HttpUtils utils, SSLModule.TrustAllCerts insecureTrustManager) {
      this.creds = creds;
      this.trustManager = !isNullOrEmpty(caCertPath) ? getTrustManagerWithCaCert(caCertPath) : getDefaultTrustManager(utils, insecureTrustManager);
   }

   @Override
   public SSLContext get() {
      Credentials currentCreds = checkNotNull(creds.get(), "credential supplier returned null");
      try {
         SSLContext sslContext = SSLContext.getInstance("TLS");
         X509Certificate certificate = getCertificate(loadFile(currentCreds.identity));
         PrivateKey privateKey = getKey(loadFile(currentCreds.credential));
         KeyManager[] keyManagers = new KeyManager[]{new InMemoryKeyManager(certificate, privateKey)};
         sslContext.init(keyManagers, trustManager, new SecureRandom());
         return sslContext;
      } catch (GeneralSecurityException e) {
         throw propagate(e);
      } catch (IOException e) {
         throw propagate(e);
      }
   }

   private TrustManager[] getTrustManagerWithCaCert(String caCertPath) {
      try {
         X509Certificate caCert = getCertificate(loadFile(caCertPath));
         KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
         trustStore.load(null, null);
         trustStore.setCertificateEntry("ca", caCert);
         TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
         tmf.init(trustStore);
         return tmf.getTrustManagers();
      } catch (GeneralSecurityException e) {
         throw propagate(e);
      } catch (IOException e) {
         throw propagate(e);
      }
   }

   private TrustManager[] getDefaultTrustManager(HttpUtils utils, SSLModule.TrustAllCerts insecureTrustManager) {
      TrustManager[] trustManagers = null;
      if (utils.trustAllCerts()) {
         trustManagers = new TrustManager[]{insecureTrustManager};
      }
      return trustManagers;
   }


   private static X509Certificate getCertificate(String certificate) {
      try {
         return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(
                 new ByteArrayInputStream(certificate.getBytes(Charsets.UTF_8)));
      } catch (CertificateException ex) {
         throw new RuntimeException("Invalid certificate", ex);
      }
   }

   private static PrivateKey getKey(String privateKey) {
      PEMParser pemParser = new PEMParser(new StringReader(privateKey));
      try {
         Object object = pemParser.readObject();
         if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
         }
         JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider("BC");
         KeyPair keyPair = converter.getKeyPair((PEMKeyPair) object);
         return keyPair.getPrivate();
      } catch (IOException ex) {
         throw new RuntimeException("Invalid private key", ex);
      } finally {
          Closeables2.closeQuietly(pemParser);
      }
   }

   private static String loadFile(final String filePath) throws IOException{
      return Files.toString(new File(filePath), Charsets.UTF_8);
   }

   private static class InMemoryKeyManager extends X509ExtendedKeyManager {
      private static final String DEFAULT_ALIAS = "docker";

      private final X509Certificate certificate;

      private final PrivateKey privateKey;

      public InMemoryKeyManager(final X509Certificate certificate, final PrivateKey privateKey)
              throws IOException, CertificateException {
         this.certificate = certificate;
         this.privateKey = privateKey;
      }

      @Override
      public String chooseClientAlias(final String[] keyType, final Principal[] issuers,
                                      final Socket socket) {
         return DEFAULT_ALIAS;
      }

      @Override
      public String chooseServerAlias(final String keyType, final Principal[] issuers,
                                      final Socket socket) {
         return DEFAULT_ALIAS;
      }

      @Override
      public X509Certificate[] getCertificateChain(final String alias) {
         return new X509Certificate[]{certificate};
      }

      @Override
      public String[] getClientAliases(final String keyType, final Principal[] issuers) {
         return new String[]{DEFAULT_ALIAS};
      }

      @Override
      public PrivateKey getPrivateKey(final String alias) {
         return privateKey;
      }

      @Override
      public String[] getServerAliases(final String keyType, final Principal[] issuers) {
         return new String[]{DEFAULT_ALIAS};
      }
   }

}
