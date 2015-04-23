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
package org.jclouds.docker.compute.options;

import static org.testng.Assert.assertEquals;

import org.jclouds.compute.options.TemplateOptions;
import org.testng.annotations.Test;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/**
 * Unit tests for the {@link DockerTemplateOptions} class.
 */
@Test(groups = "unit", testName = "DockerTemplateOptionsTest")
public class DockerTemplateOptionsTest {

   @Test
   public void testHostname() {
      TemplateOptions options = DockerTemplateOptions.Builder.hostname("hostname");
      assertEquals(options.as(DockerTemplateOptions.class).getHostname(), Optional.of("hostname"));
   }

   @Test
   public void testMemory() {
      TemplateOptions options = DockerTemplateOptions.Builder.memory(1024);
      assertEquals(options.as(DockerTemplateOptions.class).getMemory(), Optional.of(1024));
   }

   @Test
   public void testCpuShares() {
      TemplateOptions options = DockerTemplateOptions.Builder.cpuShares(2);
      assertEquals(options.as(DockerTemplateOptions.class).getCpuShares(), Optional.of(2));
   }

   @Test
   public void testVolumes() {
      TemplateOptions options = DockerTemplateOptions.Builder.volumes(ImmutableMap.of("/tmp", "/tmp"));
      assertEquals(options.as(DockerTemplateOptions.class).getVolumes(), Optional.of(ImmutableMap.of("/tmp", "/tmp")));
   }

   @Test
   public void testDns() {
      TemplateOptions options = DockerTemplateOptions.Builder.dns("8.8.8.8");
      assertEquals(options.as(DockerTemplateOptions.class).getDns(), Optional.of("8.8.8.8"));
   }

   @Test
   public void testCommands() {
      TemplateOptions options = DockerTemplateOptions.Builder.commands("chmod 666 /etc/*", "rm -rf /var/run");
      assertEquals(options.as(DockerTemplateOptions.class).getDns(), Optional.of(ImmutableList.of("chmod 666 /etc/*", "rm -rf /var/run")));
   }

   @Test
   public void testEnv() {
      TemplateOptions options = DockerTemplateOptions.Builder.env("A=b", "C=d");
      assertEquals(options.as(DockerTemplateOptions.class).getEnv(), Optional.of(ImmutableList.of("A=b", "C=d")));
   }

   @Test
   public void testPortBindings() {
      TemplateOptions options = DockerTemplateOptions.Builder.portBindings(ImmutableMap.<Integer, Integer>builder().put(8443,  443).put(8080, 80).build());
      assertEquals(options.as(DockerTemplateOptions.class).getPortBindings(), Optional.of(ImmutableMap.<Integer, Integer>builder().put(8443,  443).put(8080, 80).build()));
   }

   @Test
   public void testNonDockerOptions() {
      TemplateOptions options = DockerTemplateOptions.Builder.userMetadata(ImmutableMap.of("key", "value")).cpuShares(1);
      assertEquals(options.as(DockerTemplateOptions.class).getUserMetadata(), Optional.of(ImmutableMap.of("key", "value")));
      assertEquals(options.as(DockerTemplateOptions.class).getCpuShares(), Optional.of(1));
   }

   @Test
   public void testMultipleOptions() {
      TemplateOptions options = DockerTemplateOptions.Builder.memory(512).cpuShares(4);
      assertEquals(options.as(DockerTemplateOptions.class).getMemory(), Optional.of(512));
      assertEquals(options.as(DockerTemplateOptions.class).getCpuShares(), Optional.of(4));
   }
}
