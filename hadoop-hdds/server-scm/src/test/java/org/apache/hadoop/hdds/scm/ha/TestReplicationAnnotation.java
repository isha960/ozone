/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership.  The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.hadoop.hdds.scm.ha;

import org.apache.hadoop.hdds.protocol.proto.HddsProtos;
import org.apache.hadoop.hdds.protocol.proto.SCMRatisProtocol;
import org.apache.hadoop.hdds.protocol.proto.SCMRatisProtocol.RequestType;
import org.apache.hadoop.hdds.scm.AddSCMRequest;
import org.apache.hadoop.hdds.scm.RemoveSCMRequest;
import org.apache.hadoop.hdds.scm.container.ContainerStateManager;
import org.apache.ratis.grpc.GrpcTlsConfig;
import org.apache.ratis.protocol.exceptions.NotLeaderException;
import org.apache.ratis.server.RaftServer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * Tests on {@link org.apache.hadoop.hdds.scm.metadata.Replicate}.
 */
public class TestReplicationAnnotation {
  private SCMHAInvocationHandler scmhaInvocationHandler;
  private SCMRatisServer scmRatisServer;

  @BeforeEach
  public void setup() {
    scmRatisServer = new SCMRatisServer() {
      @Override
      public void start() throws IOException {
      }

      @Override
      public void registerStateMachineHandler(
          SCMRatisProtocol.RequestType handlerType, Object handler) {
      }

      @Override
      public SCMRatisResponse submitRequest(SCMRatisRequest request)
          throws IOException, ExecutionException, InterruptedException {
        throw new IOException("submitRequest is called.");
      }

      @Override
      public boolean triggerSnapshot() throws IOException {
        throw new IOException("submitSnapshotRequest is called.");
      }

      @Override
      public void stop() throws IOException {
      }

      @Override
      public boolean isStopped() {
        return false;
      }

      @Override
      public RaftServer.Division getDivision() {
        return null;
      }

      @Override
      public List<String> getRatisRoles() {
        return null;
      }

      @Override
      public NotLeaderException triggerNotLeaderException() {
        return null;
      }

      @Override
      public boolean addSCM(AddSCMRequest request)
          throws IOException {
        return false;
      }

      @Override
      public boolean removeSCM(RemoveSCMRequest request) throws IOException {
        return false;
      }

      @Override
      public SCMStateMachine getSCMStateMachine() {
        return null;
      }

      @Override
      public GrpcTlsConfig getGrpcTlsConfig() {
        return null;
      }
    };
  }

  @Test
  public void testReplicateAnnotationBasic() throws Throwable {

    scmhaInvocationHandler = new SCMHAInvocationHandler(
        RequestType.CONTAINER, null, scmRatisServer);

    ContainerStateManager proxy =
        (ContainerStateManager) Proxy.newProxyInstance(
            SCMHAInvocationHandler.class.getClassLoader(),
            new Class<?>[]{ContainerStateManager.class},
            scmhaInvocationHandler);

    try {
      proxy.addContainer(HddsProtos.ContainerInfoProto.getDefaultInstance());
      Assertions.fail("Cannot reach here: should have seen a IOException");
    } catch (IOException e) {
      Assertions.assertNotNull(e.getMessage());
      Assertions.assertTrue(e.getMessage().contains("submitRequest is called"));
    }
  }
}
