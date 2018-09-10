// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.docker;

import com.google.common.net.InetAddresses;
import com.yahoo.collections.Pair;
import com.yahoo.system.ProcessExecuter;
import com.yahoo.vespa.hosted.dockerapi.Container;
import com.yahoo.vespa.hosted.dockerapi.ContainerName;
import com.yahoo.vespa.hosted.dockerapi.Docker;
import com.yahoo.vespa.hosted.dockerapi.DockerImage;
import com.yahoo.vespa.hosted.dockerapi.ProcessResult;
import com.yahoo.vespa.hosted.node.admin.component.Environment;
import com.yahoo.vespa.hosted.node.admin.config.ConfigServerConfig;
import com.yahoo.vespa.hosted.node.admin.nodeagent.ContainerData;
import org.junit.Test;
import org.mockito.InOrder;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Paths;
import java.util.Optional;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyVararg;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class DockerOperationsImplTest {
    private final Environment environment = new Environment.Builder()
            .configServerConfig(new ConfigServerConfig(new ConfigServerConfig.Builder()))
            .region("us-east-1")
            .environment("prod")
            .system("main")
            .cloud("mycloud")
            .dockerNetworkName("mynetwork")
            .build();
    private final Docker docker = mock(Docker.class);
    private final ProcessExecuter processExecuter = mock(ProcessExecuter.class);
    private final DockerOperationsImpl dockerOperations
            = new DockerOperationsImpl(docker, environment, processExecuter);

    @Test
    public void processResultFromNodeProgramWhenSuccess() {
        final ContainerName containerName = new ContainerName("container-name");
        final ProcessResult actualResult = new ProcessResult(0, "output", "errors");
        final String programPath = "/bin/command";
        final String[] command = new String[]{programPath, "arg"};

        when(docker.executeInContainerAsRoot(any(), anyVararg()))
                .thenReturn(actualResult); // output from node program

        ProcessResult result = dockerOperations.executeCommandInContainer(containerName, command);

        final InOrder inOrder = inOrder(docker);
        inOrder.verify(docker, times(1)).executeInContainerAsRoot(
                eq(containerName),
                eq(command[0]),
                eq(command[1]));

        assertThat(result, is(actualResult));
    }

    @Test(expected = RuntimeException.class)
    public void processResultFromNodeProgramWhenNonZeroExitCode() {
        final ContainerName containerName = new ContainerName("container-name");
        final ProcessResult actualResult = new ProcessResult(3, "output", "errors");
        final String programPath = "/bin/command";
        final String[] command = new String[]{programPath, "arg"};

        when(docker.executeInContainerAsRoot(any(), anyVararg()))
                .thenReturn(actualResult); // output from node program

        dockerOperations.executeCommandInContainer(containerName, command);
    }

    @Test
    public void runsCommandInNetworkNamespace() {
        Container container = makeContainer("container-42", Container.State.RUNNING, 42);

        try {
            when(processExecuter.exec(aryEq(new String[]{"sudo", "nsenter", "--net=/host/proc/42/ns/net", "--", "iptables", "-nvL"})))
                    .thenReturn(new Pair<>(0, ""));
        } catch (IOException e) {
            e.printStackTrace();
        }

        dockerOperations.executeCommandInNetworkNamespace(container.name, "iptables", "-nvL");
    }

    private Container makeContainer(String name, Container.State state, int pid) {
        final Container container = new Container(name + ".fqdn", new DockerImage("mock"), null,
                new ContainerName(name), state, pid);
        when(docker.getContainer(eq(container.name))).thenReturn(Optional.of(container));
        return container;
    }

    @Test
    public void verifyEtcHosts() {
        ContainerData containerData = mock(ContainerData.class);
        String hostname = "hostname";
        InetAddress ipV6Local = InetAddresses.forString("::1");
        InetAddress ipV4Local = InetAddresses.forString("127.0.0.1");

        DockerOperationsImpl dockerOperations = new DockerOperationsImpl(
                docker,
                environment,
                processExecuter);
        dockerOperations.addEtcHosts(containerData, hostname, Optional.empty(), ipV6Local);

        verify(containerData, times(1)).addFile(
                Paths.get("/etc/hosts"),
                "# This file was generated by com.yahoo.vespa.hosted.node.admin.docker.DockerOperationsImpl\n" +
                        "127.0.0.1	localhost\n" +
                        "::1	localhost ip6-localhost ip6-loopback\n" +
                        "fe00::0	ip6-localnet\n" +
                        "ff00::0	ip6-mcastprefix\n" +
                        "ff02::1	ip6-allnodes\n" +
                        "ff02::2	ip6-allrouters\n" +
                        "0:0:0:0:0:0:0:1	hostname\n");

        dockerOperations.addEtcHosts(containerData, hostname, Optional.of(ipV4Local), ipV6Local);

        verify(containerData, times(1)).addFile(
                Paths.get("/etc/hosts"),
                "# This file was generated by com.yahoo.vespa.hosted.node.admin.docker.DockerOperationsImpl\n" +
                        "127.0.0.1	localhost\n" +
                        "::1	localhost ip6-localhost ip6-loopback\n" +
                        "fe00::0	ip6-localnet\n" +
                        "ff00::0	ip6-mcastprefix\n" +
                        "ff02::1	ip6-allnodes\n" +
                        "ff02::2	ip6-allrouters\n" +
                        "0:0:0:0:0:0:0:1	hostname\n" +
                        "127.0.0.1	hostname\n");
    }
}
