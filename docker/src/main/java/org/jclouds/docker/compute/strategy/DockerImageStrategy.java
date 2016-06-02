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
package org.jclouds.docker.compute.strategy;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.NoSuchElementException;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.google.common.base.Function;

import org.jclouds.compute.strategy.GetImageStrategy;
import org.jclouds.docker.DockerApi;
import org.jclouds.docker.domain.Image;

@Singleton
public class DockerImageStrategy implements GetImageStrategy {

   private final DockerApi client;
   private final Function<Image, org.jclouds.compute.domain.Image> imageToImage;

   @Inject
   protected DockerImageStrategy(DockerApi client, Function<Image, org.jclouds.compute.domain.Image> imageToImage) {
      this.client = checkNotNull(client, "client");
      this.imageToImage = checkNotNull(imageToImage, "imageToImage");
   }

   @Override
   public org.jclouds.compute.domain.Image getImage(String id) {
      checkNotNull(id, "id");
      try {
         Image image = client.getImageApi().inspectImage(id);
         return imageToImage.apply(image);
      } catch (NoSuchElementException e) {
         return null;
      }
   }

}

