package net.corda.applications.workers.rest

import net.corda.applications.workers.rest.utils.E2eCluster
import net.corda.applications.workers.rest.utils.E2eClusterAConfig
import net.corda.applications.workers.rest.utils.E2eClusterBConfig
import net.corda.applications.workers.rest.utils.E2eClusterCConfig
import net.corda.applications.workers.rest.utils.E2eClusterFactory
import net.corda.applications.workers.rest.utils.E2eClusterMember
import net.corda.applications.workers.rest.utils.assertAllMembersAreInMemberList
import net.corda.applications.workers.rest.utils.disableLinkManagerCLRChecks
import net.corda.applications.workers.rest.utils.generateGroupPolicy
import net.corda.applications.workers.rest.utils.getGroupId
import net.corda.applications.workers.rest.utils.getMemberName
import net.corda.applications.workers.rest.utils.onboardMembers
import net.corda.applications.workers.rest.utils.onboardMgm
import net.corda.applications.workers.rest.utils.setSslConfiguration
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

@Tag("MultiCluster")
class SessionCertificateTest {
    @TempDir
    lateinit var tempDir: Path

    private val clusterA = E2eClusterFactory.getE2eCluster(E2eClusterAConfig).apply {
        addMember(createTestMember("Alice"))
    }

    private val clusterB = E2eClusterFactory.getE2eCluster(E2eClusterBConfig).apply {
        addMember(createTestMember("Bob"))
    }

    private val clusterC = E2eClusterFactory.getE2eCluster(E2eClusterCConfig).apply {
        addMember(createTestMember("Mgm"))
    }

    private val memberClusters = listOf(clusterA, clusterB)

    @BeforeEach
    fun validSetup() {
        // Verify that test clusters are actually configured with different endpoints.
        // If not, this test isn't testing what it should.
        Assertions.assertThat(clusterA.clusterConfig.p2pHost)
            .isNotEqualTo(clusterB.clusterConfig.p2pHost)
            .isNotEqualTo(clusterC.clusterConfig.p2pHost)
        Assertions.assertThat(clusterB.clusterConfig.p2pHost)
            .isNotEqualTo(clusterC.clusterConfig.p2pHost)

        // For the purposes of this test, the MGM cluster is
        // expected to have only one MGM (in reality there can be more on a cluster).
        Assertions.assertThat(clusterC.members).hasSize(1)
    }

    @Test
    fun `Create mgm and allow members to join the group`() {
        onboardMultiClusterGroup()
    }

    /**
     * Onboard group and return group ID.
     */
    private fun onboardMultiClusterGroup(): String {
        val mgm = clusterC.members[0]

        clusterC.setSslConfiguration(false)
        clusterC.disableLinkManagerCLRChecks()
        clusterC.onboardMgm(mgm, tempDir, useSessionCertificate = true)

        val memberGroupPolicy = clusterC.generateGroupPolicy(mgm.holdingId)

        memberClusters.forEach { cordaCluster ->
            cordaCluster.setSslConfiguration(false)
            cordaCluster.disableLinkManagerCLRChecks()
            cordaCluster.onboardMembers(mgm, memberGroupPolicy, tempDir, useSessionCertificate = true)
        }

        // Assert all members can see each other in their member lists.
        val allMembers = memberClusters.flatMap { it.members } + mgm
        (memberClusters + clusterC).forEach { cordaCluster ->
            cordaCluster.members.forEach {
                cordaCluster.assertAllMembersAreInMemberList(it, allMembers)
            }
        }
        return clusterC.getGroupId(mgm.holdingId)
    }

    private fun E2eCluster.createTestMember(
        namePrefix: String
    ): E2eClusterMember {
        return E2eClusterMember(getMemberName<SessionCertificateTest>(namePrefix))
    }
}