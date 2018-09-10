// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.logging;

import com.google.common.collect.ImmutableList;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.NodeSpec;
import com.yahoo.vespa.hosted.node.admin.component.Environment;
import com.yahoo.vespa.hosted.node.admin.config.ConfigServerConfig;
import com.yahoo.vespa.hosted.provision.Node;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.*;

/**
 * @author mortent
 */
public class FilebeatConfigProviderTest {

    private static final String tenant = "vespa";
    private static final String application = "music";
    private static final String instance = "default";
    private static final String environment = "prod";
    private static final String region = "us-north-1";
    private static final String system = "main";
    private static final List<String> logstashNodes = ImmutableList.of("logstash1", "logstash2");

    @Test
    public void it_replaces_all_fields_correctly() {
        FilebeatConfigProvider filebeatConfigProvider = new FilebeatConfigProvider(getEnvironment(logstashNodes));

        Optional<String> config = filebeatConfigProvider.getConfig(createNodeRepositoryNode(tenant, application, instance));

        assertTrue(config.isPresent());
        String configString = config.get();
        assertThat(configString, not(containsString("%%")));
    }

    @Test
    public void it_does_not_generate_config_when_no_logstash_nodes() {
        Environment env = getEnvironment(Collections.emptyList());

        FilebeatConfigProvider filebeatConfigProvider = new FilebeatConfigProvider(env);
        Optional<String> config = filebeatConfigProvider.getConfig(createNodeRepositoryNode(tenant, application, instance));
        assertFalse(config.isPresent());
    }

    @Test
    public void it_does_not_generate_config_for_nodes_wihout_owner() {
        FilebeatConfigProvider filebeatConfigProvider = new FilebeatConfigProvider(getEnvironment(logstashNodes));
        NodeSpec node = new NodeSpec.Builder()
                .flavor("flavor")
                .state(Node.State.active)
                .nodeType(NodeType.tenant)
                .hostname("hostname")
                .minCpuCores(1)
                .minMainMemoryAvailableGb(1)
                .minDiskAvailableGb(1)
                .build();
        Optional<String> config = filebeatConfigProvider.getConfig(node);
        assertFalse(config.isPresent());
    }

    @Test
    public void it_generates_correct_index_source() {
        assertThat(getConfigString(), containsString("index_source: \"hosted-instance_vespa_music_us-north-1_prod_default\""));
    }

    @Test
    public void it_sets_logstash_nodes_properly() {
        assertThat(getConfigString(), containsString("hosts: [\"logstash1\",\"logstash2\"]"));
    }

    @Test
    public void it_does_not_add_double_quotes() {
        Environment environment = getEnvironment(ImmutableList.of("unquoted", "\"quoted\""));
        FilebeatConfigProvider filebeatConfigProvider = new FilebeatConfigProvider(environment);
        Optional<String> config = filebeatConfigProvider.getConfig(createNodeRepositoryNode(tenant, application, instance));
        assertThat(config.get(), containsString("hosts: [\"unquoted\",\"quoted\"]"));
    }

    @Test
    public void it_generates_correct_spool_size() {
        // 2 nodes, 3 workers, 2048 buffer size -> 12288
        assertThat(getConfigString(), containsString("spool_size: 12288"));
    }

    private String getConfigString() {
        FilebeatConfigProvider filebeatConfigProvider = new FilebeatConfigProvider(getEnvironment(logstashNodes));
        NodeSpec node = createNodeRepositoryNode(tenant, application, instance);
        return filebeatConfigProvider.getConfig(node).orElseThrow(() -> new RuntimeException("Failed to get filebeat config"));
    }

    private Environment getEnvironment(List<String> logstashNodes) {
        return new Environment.Builder()
                .configServerConfig(new ConfigServerConfig(new ConfigServerConfig.Builder()))
                .environment(environment)
                .region(region)
                .system(system)
                .logstashNodes(logstashNodes)
                .cloud("mycloud")
                .dockerNetworkName("mynetwork")
                .build();
    }

    private NodeSpec createNodeRepositoryNode(String tenant, String application, String instance) {
        NodeSpec.Owner owner = new NodeSpec.Owner(tenant, application, instance);
        return new NodeSpec.Builder()
                .owner(owner)
                .flavor("flavor")
                .state(Node.State.active)
                .nodeType(NodeType.tenant)
                .hostname("hostname")
                .minCpuCores(1)
                .minMainMemoryAvailableGb(1)
                .minDiskAvailableGb(1)
                .build();
    }

}
