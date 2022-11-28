import MultiDataCenter1.{CLOUDLETS, CLOUD_LEN, CLOUD_PE, DATA_LOCALITY_ENABLED, HOST_BW, HOST_PE, HOST_RAM, HOST_STORAGE, INIT_UTILIZATION_RATIO, MAX_UTILIZATION_RATIO}
import com.typesafe.config.{Config, ConfigFactory}
import org.cloudbus.cloudsim.hosts.network.NetworkHost
import org.cloudbus.cloudsim.schedulers.cloudlet.CloudletSchedulerSpaceShared
import org.cloudbus.cloudsim.vms.Vm
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.cloudbus.cloudsim.resources.PeSimple
import com.typesafe.config.{Config, ConfigFactory}
import org.cloudbus.cloudsim.allocationpolicies.migration.VmAllocationPolicyMigrationStaticThreshold
import org.cloudbus.cloudsim.allocationpolicies.{VmAllocationPolicyBestFit, VmAllocationPolicyRoundRobin, VmAllocationPolicySimple}
import org.cloudbus.cloudsim.brokers.{DatacenterBroker, DatacenterBrokerBestFit, DatacenterBrokerHeuristic, DatacenterBrokerSimple}
import org.cloudbus.cloudsim.cloudlets.CloudletSimple
import org.cloudbus.cloudsim.cloudlets.network.{CloudletExecutionTask, CloudletReceiveTask, CloudletSendTask, NetworkCloudlet}
import org.cloudbus.cloudsim.core.{CloudSim, SimEntity}
import org.cloudbus.cloudsim.datacenters.network.NetworkDatacenter
import org.cloudbus.cloudsim.distributions.UniformDistr
import org.cloudbus.cloudsim.hosts.network.NetworkHost
import org.cloudbus.cloudsim.hosts.{Host, network}
import org.cloudbus.cloudsim.network.switches.EdgeSwitch
import org.cloudbus.cloudsim.network.topologies.{BriteNetworkTopology, NetworkTopology}
import org.cloudbus.cloudsim.resources.PeSimple
import org.cloudbus.cloudsim.schedulers.cloudlet.{CloudletSchedulerCompletelyFair, CloudletSchedulerSpaceShared, CloudletSchedulerTimeShared}
import org.cloudbus.cloudsim.schedulers.vm.{VmSchedulerSpaceShared, VmSchedulerTimeShared}
import org.cloudbus.cloudsim.selectionpolicies.VmSelectionPolicyMinimumUtilization
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModelDynamic
import org.cloudbus.cloudsim.vms.VmCost
import org.cloudbus.cloudsim.vms.network.NetworkVm
import org.cloudsimplus.builders.tables.CloudletsTableBuilder
import org.cloudsimplus.heuristics.CloudletToVmMappingSimulatedAnnealing
import org.cloudsimplus.listeners.EventInfo
import org.cloudsimplus.listeners.VmHostEventInfo

import scala.collection.JavaConverters.*
import scala.collection.convert.ImplicitConversions.*
import scala.collection.immutable.TreeMap
import scala.collection.mutable.ListBuffer
import scala.util.Random
import org.cloudbus.cloudsim.vms.Vm
import org.cloudsimplus.autoscaling.HorizontalVmScaling
import org.cloudsimplus.autoscaling.HorizontalVmScalingSimple

import java.util.function.Supplier


class SimulationsTest extends AnyFlatSpec with Matchers {

  // testing config parameters
  val applicationConf: Config = ConfigFactory.load("application.conf")

  it should "get the number of PEs in hosts" in {
    applicationConf.getDouble("hostConfig.host.PE") shouldBe 12
  }

  it should "get the scheduling interval of DataCenter 1" in {
    applicationConf.getInt("DataCenter1.schedulingInterval") shouldBe 1
  }

  it should "get the simulation time of DataCenter 1" in {
    applicationConf.getInt("DataCenter2.simulationTime") shouldBe 100
  }

  it should "Create the datacenters for multidtatacenter 1" in {
    val simulation = new CloudSim()
    val dc_lst = MultiDataCenter1.createDatacenters(simulation)
    dc_lst shouldBe a[List[NetworkDatacenter]]
    dc_lst(0).getHostList.size() shouldBe 2
  }

  it should "Create the datacenters for multidtatacenter 2" in {
    val simulation = new CloudSim()
    val dc_lst = MultiDataCenter2.createDatacenters(simulation)
    dc_lst shouldBe a[List[NetworkDatacenter]]
    dc_lst(0).getHostList.size() shouldBe 3
  }

  it should "Create a broker using createBroker and test its properties " in {
    val simulation = new CloudSim()
    val broker = MultiDataCenter1.createBroker(simulation)
    broker shouldBe a[DatacenterBrokerSimple]
  }

  it should "create a vm and get and set their properties" in {
    val vm = MultiDataCenter1.createVm(2.0)
    vm shouldBe a[Vm]
    vm.getRam.getCapacity shouldBe applicationConf.getInt("hostConfig.vm.RAM")
    vm.getBw.getCapacity shouldBe applicationConf.getInt("hostConfig.vm.Bandwidth")
    vm.getTimeZone shouldBe 2.0
    vm.getCloudletScheduler.isInstanceOf[CloudletSchedulerSpaceShared] shouldBe true
  }

  it should "verify the creation of cloudlets" in {
    val utilizationModel: UtilizationModelDynamic = new UtilizationModelDynamic(0.2).setMaxResourceUtilization(0.8)
    val cloudletList = List.tabulate(4) { n =>
      val cloudlet = new CloudletSimple(1000, 1)
      cloudlet.setUtilizationModel(utilizationModel)
      cloudlet
    }
    cloudletList.size shouldBe 4
    cloudletList(0).isInstanceOf[CloudletSimple] shouldBe true
  }

  it should "test functioning of basic example host creation " in {
    val pes = List.fill(2) {
      new PeSimple(32000)
    }
    val host = new NetworkHost(HOST_RAM, HOST_BW, HOST_STORAGE, pes.asJava)
    assert(host.isInstanceOf[NetworkHost])
  }

}
