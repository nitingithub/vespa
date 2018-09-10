// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.maintenance;

import com.yahoo.collections.Pair;
import com.yahoo.config.provision.NodeType;
import com.yahoo.metrics.simple.MetricReceiver;
import com.yahoo.system.ProcessExecuter;
import com.yahoo.test.ManualClock;
import com.yahoo.vespa.hosted.dockerapi.ContainerName;
import com.yahoo.vespa.hosted.dockerapi.metrics.MetricReceiverWrapper;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.NodeSpec;
import com.yahoo.vespa.hosted.node.admin.config.ConfigServerConfig;
import com.yahoo.vespa.hosted.node.admin.docker.DockerOperations;
import com.yahoo.vespa.hosted.node.admin.component.Environment;
import com.yahoo.vespa.hosted.node.admin.component.PathResolver;
import com.yahoo.vespa.hosted.provision.Node;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author dybis
 */
public class StorageMaintainerTest {
    private final ManualClock clock = new ManualClock();
    private final Environment environment = new Environment.Builder()
            .configServerConfig(new ConfigServerConfig(new ConfigServerConfig.Builder()))
            .region("us-east-1")
            .environment("prod")
            .system("main")
            .cloud("mycloud")
            .pathResolver(new PathResolver())
            .dockerNetworkName("mynetwork")
            .coredumpFeedEndpoint("http://domain.tld/docid")
            .build();
    private final DockerOperations docker = mock(DockerOperations.class);
    private final ProcessExecuter processExecuter = mock(ProcessExecuter.class);
    private final StorageMaintainer storageMaintainer = new StorageMaintainer(docker, processExecuter,
            new MetricReceiverWrapper(MetricReceiver.nullImplementation), environment, clock);

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void testDiskUsed() throws IOException, InterruptedException {
        int writeSize = 10000;
        writeNBytesToFile(folder.newFile(), writeSize);

        long usedBytes = storageMaintainer.getDiskUsedInBytes(folder.getRoot().toPath());
        if (usedBytes * 4 < writeSize || usedBytes > writeSize * 4)
            fail("Used bytes is " + usedBytes + ", but wrote " + writeSize + " bytes, not even close.");
    }

    @Test
    public void testNonExistingDiskUsed() throws IOException, InterruptedException {
        long usedBytes = storageMaintainer.getDiskUsedInBytes(folder.getRoot().toPath().resolve("doesn't exist"));
        assertEquals(0L, usedBytes);
    }

    @Test
    public void testMaintenanceThrottlingAfterSuccessfulMaintenance() {
        String hostname = "node-123.us-north-3.test.yahoo.com";
        ContainerName containerName = ContainerName.fromHostname(hostname);
        NodeSpec node = new NodeSpec.Builder()
                .hostname(hostname)
                .state(Node.State.ready)
                .nodeType(NodeType.tenant)
                .flavor("docker")
                .minCpuCores(1)
                .minMainMemoryAvailableGb(1)
                .minDiskAvailableGb(1)
                .build();

        try {
            when(processExecuter.exec(any(String[].class))).thenReturn(new Pair<>(0, ""));
        } catch (IOException ignored) { }
        storageMaintainer.removeOldFilesFromNode(containerName);
        verifyProcessExecuterCalled(1);
        // Will not actually run maintenance job until an hour passes
        storageMaintainer.removeOldFilesFromNode(containerName);
        verifyProcessExecuterCalled(1);

        clock.advance(Duration.ofMinutes(61));
        storageMaintainer.removeOldFilesFromNode(containerName);
        verifyProcessExecuterCalled(2);

        // Coredump handling is unthrottled
        storageMaintainer.handleCoreDumpsForContainer(containerName, node);
        verifyProcessExecuterCalled(3);

        storageMaintainer.handleCoreDumpsForContainer(containerName, node);
        verifyProcessExecuterCalled(4);

        // cleanupNodeStorage is unthrottled and it should reset previous times
        storageMaintainer.cleanupNodeStorage(containerName, node);
        verifyProcessExecuterCalled(5);
        storageMaintainer.cleanupNodeStorage(containerName, node);
        verifyProcessExecuterCalled(6);
    }

    @Test
    public void testMaintenanceThrottlingAfterFailedMaintenance() {
        String hostname = "node-123.us-north-3.test.yahoo.com";
        ContainerName containerName = ContainerName.fromHostname(hostname);

        try {
            when(processExecuter.exec(any(String[].class)))
                    .thenThrow(new RuntimeException("Something went wrong"))
                    .thenReturn(new Pair<>(0, ""));
        } catch (IOException ignored) { }

        try {
            storageMaintainer.removeOldFilesFromNode(containerName);
            fail("Maintenance job should've failed!");
        } catch (RuntimeException ignored) { }
        verifyProcessExecuterCalled(1);

        // Maintenance job failed, we should be able to immediately re-run it
        storageMaintainer.removeOldFilesFromNode(containerName);
        verifyProcessExecuterCalled(2);
    }

    private static void writeNBytesToFile(File file, int nBytes) throws IOException {
        Files.write(file.toPath(), new byte[nBytes]);
    }

    private void verifyProcessExecuterCalled(int times) {
        try {
            verify(processExecuter, times(times)).exec(any(String[].class));
        } catch (IOException ignored) { }
    }
}
