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
package org.jclouds.docker.compute.extensions;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.jclouds.compute.config.ComputeServiceProperties.TIMEOUT_IMAGE_AVAILABLE;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.Resource;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Splitter;
import com.google.common.base.Supplier;
import com.google.common.base.Throwables;
import com.google.common.io.ByteStreams;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.UncheckedTimeoutException;

import org.jclouds.Constants;
import org.jclouds.collect.Memoized;
import org.jclouds.compute.domain.CloneImageTemplate;
import org.jclouds.compute.domain.Image;
import org.jclouds.compute.domain.ImageTemplate;
import org.jclouds.compute.domain.ImageTemplateBuilder;
import org.jclouds.compute.extensions.ImageExtension;
import org.jclouds.compute.reference.ComputeServiceConstants;
import org.jclouds.docker.DockerApi;
import org.jclouds.docker.options.CreateImageOptions;
import org.jclouds.domain.Location;
import org.jclouds.logging.Logger;
import org.jclouds.util.Closeables2;

import autovalue.shaded.com.google.common.common.collect.Iterables;

/**
 * Docker implementation of {@link ImageExtension}
 */
@Singleton
public class DockerImageExtension implements ImageExtension {

   @Resource
   @Named(ComputeServiceConstants.COMPUTE_LOGGER)
   protected Logger logger = Logger.NULL;

   private final DockerApi dockerApi;
   private final ListeningExecutorService userExecutor;
   private final Predicate<AtomicReference<Image>> imageAvailablePredicate;

   @Inject
   public DockerImageExtension(DockerApi dockerApi,
         @Named(Constants.PROPERTY_USER_THREADS) ListeningExecutorService userExecutor,
         @Named(TIMEOUT_IMAGE_AVAILABLE) Predicate<AtomicReference<Image>> imageAvailablePredicate) {
      this.dockerApi = checkNotNull(dockerApi, "dockerApi");
      this.userExecutor = checkNotNull(userExecutor, "userExecutor");
      this.imageAvailablePredicate = checkNotNull(imageAvailablePredicate, "imageAvailablePredicate");
   }

   @Override
   public ImageTemplate buildImageTemplateFromNode(String name, final String id) {
      CloneImageTemplate template = new ImageTemplateBuilder.CloneImageTemplateBuilder().nodeId(id).name(name).build();
      return template;
   }

   @Override
   public ListenableFuture<Image> createImage(ImageTemplate template) {
      String imageName = Preconditions.checkNotNull(template.getName());
      InputStream output = dockerApi.getImageApi().createImage(CreateImageOptions.Builder.fromImage(imageName));
       try {
           ByteStreams.copy(output, ByteStreams.nullOutputStream());
       } catch (IOException ioe) {
           throw Throwables.propagate(ioe);
       } finally {
           Closeables2.closeQuietly(output);
       }
      return userExecutor.submit(new Callable<Image>() {
         @Override
         public Image call() throws Exception {
            if (imageAvailablePredicate.apply(image))
           return dockerApi.getImageApi().inspectImage(imageName);
               return image.get();
            // TODO: get rid of the expectation that the image will be available, as it is very brittle
            throw new UncheckedTimeoutException("Image was not created within the time limit: " + image.get());
         }
      });
   }

   @Override
   public boolean deleteImage(String id) {
      return true;
   }

}
