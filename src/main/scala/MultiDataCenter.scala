import DataCenter1.{VM_BW, VM_MIP_CAPACITY, VM_PE, VM_RAM, startMigration}
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

//This program simulates creation of multiple datacenters across different time zones.
// It also simulates creation of a network topology by creating links between each datacenter and broker.
// The hosts are connected to each other in the datacenters using Edge switches


object DataCenter1 extends App {
  /**
   * The percentage of host CPU usage that trigger VM migration
   * due to under utilization (in scale from 0 to 1, where 1 is 100%).
   */
  val HOST_UNDER_UTILIZATION_THRESHOLD_FOR_VM_MIGRATION = 0.1

  /**
   * The percentage of host CPU usage that trigger VM migration
   * due to over utilization (in scale from 0 to 1, where 1 is 100%).
   */
  val HOST_OVER_UTILIZATION_THRESHOLD_FOR_VM_MIGRATION = 0.7

  val applicationConf: Config = ConfigFactory.load("application.conf")
  val logger = CreateLogger(classOf[DataCenter1.type])

  val INIT_UTILIZATION_RATIO = applicationConf.getDouble("DataCenter1.initUtilizationRatio")
  val MAX_UTILIZATION_RATIO = applicationConf.getDouble("DataCenter1.maxUtilizationRatio")
  val SCHEDULING_INTERVAL = applicationConf.getDouble("DataCenter1.schedulingInterval")

  val SIMULATION_TIME = applicationConf.getInt("DataCenter1.simulationTime")
  val TIME_ZONES = applicationConf.getDoubleList("DataCenter1.timeZones")

  val BROKER_NETWORK_LATENCY = applicationConf.getInt("DataCenter1.brokerNetworkLatency")
  val DC_NETWORK_BANDWIDTH = applicationConf.getInt("DataCenter1.dcNetworkBandwidth")
  val DC_INTERNAL_NETWORK_LATENCY = applicationConf.getInt("DataCenter1.dcInternalNetworkLatency")
  val HOST_COUNT = applicationConf.getInt("DataCenter1.hostCount")
  val VMs_PER_HOST = applicationConf.getInt("DataCenter1.vmsPerHost")

  val HOST_PE = applicationConf.getInt("hostConfig.host.PE")
  val HOST_MIP_CAPACITY = applicationConf.getInt("hostConfig.host.mipsCapacity")
  val HOST_RAM = applicationConf.getInt("hostConfig.host.RAMInMBs")
  val HOST_STORAGE = applicationConf.getInt("hostConfig.host.StorageInMBs")
  val HOST_BW = applicationConf.getInt("hostConfig.host.BandwidthInMBps")

  val VM_PE = applicationConf.getInt("hostConfig.vm.PE")
  val VM_MIP_CAPACITY = applicationConf.getInt("hostConfig.vm.mipsCapacity")
  val VM_RAM = applicationConf.getInt("hostConfig.vm.RAMInMBs")
  val VM_STORAGE = applicationConf.getInt("hostConfig.vm.StorageInMBs")
  val VM_BW = applicationConf.getInt("hostConfig.vm.BandwidthInMBps")

  val CLOUDLETS = applicationConf.getInt("DataCenter1.cloudLets")
  val CLOUD_LEN = applicationConf.getInt("DataCenter1.cloudLet.length")
  val CLOUD_PE = applicationConf.getInt("DataCenter1.cloudLet.pe")

  val CPU_COST = applicationConf.getDouble("DataCenter1.cost.cpu")
  val MEM_COST = applicationConf.getDouble("DataCenter1.cost.mem")
  val STORAGE_COST = applicationConf.getDouble("DataCenter1.cost.storage")
  val BW_COST = applicationConf.getDouble("DataCenter1.cost.bw")

  var migrationsNumber = 0;


  startSimulation();

  def startSimulation(): Unit = {
    val simulation = new CloudSim()

    val broker0 = createBroker(simulation)
    broker0.setSelectClosestDatacenter(true)

    val datacenterList = createDatacenters(simulation)
    configureNetwork(simulation, datacenterList, broker0)

    val vmList = createAndSubmitVms(broker0)
    createAndSubmitCloudlets(broker0, vmList)

    simulation.terminateAt(SIMULATION_TIME)
    simulation.start()

    val finishedCloudlet = broker0.getCloudletFinishedList();
    new CloudletsTableBuilder(finishedCloudlet).build();
    printCosts(broker0)
  }

  //Creates broker depending on the BROKER_FIT configuration parameter.
  def createBroker(simulation: CloudSim): DatacenterBroker = {
    return new DatacenterBrokerSimple(simulation)
  }

  def createDatacenters(simulation: CloudSim): List[NetworkDatacenter] = {
    val datacenterList = TIME_ZONES.map(timeZone => {
      val hostList = createHosts()
//      val networkDatacenter = new NetworkDatacenter(simulation, hostList.asJava, new VmAllocationPolicyBestFit())
      val allocationPolicy = new VmAllocationPolicyMigrationStaticThreshold(
          new VmSelectionPolicyMinimumUtilization(),
          HOST_OVER_UTILIZATION_THRESHOLD_FOR_VM_MIGRATION + 0.2);
      val networkDatacenter = new NetworkDatacenter(simulation, hostList.asJava, allocationPolicy);

      networkDatacenter.setTimeZone(timeZone)
        .setSchedulingInterval(SCHEDULING_INTERVAL)
        .getCharacteristics
        .setCostPerMem(MEM_COST)
        .setCostPerStorage(STORAGE_COST)
        .setCostPerSecond(CPU_COST)
        .setCostPerBw(BW_COST)
      createHostNetwork(networkDatacenter, simulation)
      networkDatacenter
    }).toList
    return datacenterList
  }

  def createHosts() : List[Host] = {
    return List.fill(HOST_COUNT) {
      val pe = List.fill(HOST_PE) {
        new PeSimple(HOST_MIP_CAPACITY)
      }
      val networkHost = new NetworkHost(HOST_RAM, HOST_BW, HOST_STORAGE, (pe).asJava)
      networkHost.setVmScheduler(new VmSchedulerSpaceShared())
      networkHost
    }
  }

  def createHostNetwork(networkDatacenter: NetworkDatacenter, simulation: CloudSim): Unit = {
    val edgeSwitch = new EdgeSwitch(simulation, networkDatacenter)
    networkDatacenter.addSwitch(edgeSwitch)
    networkDatacenter.getHostList[NetworkHost].forEach(host => {
      edgeSwitch.connectHost(host)
    })
  }


  def configureNetwork(simulation: CloudSim, datacenterList: List[NetworkDatacenter], broker0: DatacenterBroker): Unit = {
    val networkTopology = new BriteNetworkTopology()
    simulation.setNetworkTopology(networkTopology)
    // Add link between each datacenter and broker
    datacenterList.foreach {datacenter => {
        networkTopology.addLink(datacenter, broker0, DC_NETWORK_BANDWIDTH, BROKER_NETWORK_LATENCY)
      }
    }
    //Add link between the 4 datacenters
    networkTopology.addLink(datacenterList(0), datacenterList(1), DC_NETWORK_BANDWIDTH, DC_INTERNAL_NETWORK_LATENCY)
    networkTopology.addLink(datacenterList(1), datacenterList(2), DC_NETWORK_BANDWIDTH, DC_INTERNAL_NETWORK_LATENCY)
    networkTopology.addLink(datacenterList(2), datacenterList(3), DC_NETWORK_BANDWIDTH, DC_INTERNAL_NETWORK_LATENCY)
    networkTopology.addLink(datacenterList(3), datacenterList(0), DC_NETWORK_BANDWIDTH, DC_INTERNAL_NETWORK_LATENCY)
  }


  private def startMigration(info: VmHostEventInfo): Unit = {
    logger.info("Migration Started")
  }


  def createVm(timezone : Double) = {
    new NetworkVm(VM_MIP_CAPACITY, VM_PE).setCloudletScheduler(new CloudletSchedulerSpaceShared())
    .setRam(VM_RAM).setBw(VM_BW)
    .setTimeZone(timezone)
    .addOnMigrationStartListener(startMigration)
  }

  def createAndSubmitVms(broker:DatacenterBroker) = {
    val vmList = new ListBuffer[NetworkVm]()
    TIME_ZONES.map(timezone => {
      val vms = List.tabulate(VMs_PER_HOST*HOST_COUNT) { n =>
        val vm = createVm(timezone)
        vmList.add(vm.asInstanceOf[NetworkVm])
        createHorizontalVmScaling(vm);
        vm
      }
      logger.info(s"In create vms. finished creating ${vms.size} vms for datacenter ${timezone} datacenter")
      broker.submitVmList(vms.asJava)
    })
    vmList.toList
  }

  def isVmOverloaded(vm: Vm) = {
    vm.getCpuPercentUtilization > 0.7
  }


  def createVm1() = {
    new NetworkVm(VM_MIP_CAPACITY, VM_PE).setCloudletScheduler(new CloudletSchedulerSpaceShared())
      .setRam(VM_RAM).setBw(VM_BW)
      .addOnMigrationStartListener(startMigration)
  }

 def createHorizontalVmScaling(vm: Vm): Unit = {
    val horizontalScaling = new HorizontalVmScalingSimple();
    horizontalScaling.setVmSupplier(() => createVm1()).setOverloadPredicate(isVmOverloaded);
    vm.setHorizontalScaling(horizontalScaling);
  }

  def createAndSubmitCloudlets(broker:DatacenterBroker,vmList:List[NetworkVm]) ={
    val utilizationModel: UtilizationModelDynamic = new UtilizationModelDynamic(INIT_UTILIZATION_RATIO).setMaxResourceUtilization(MAX_UTILIZATION_RATIO)
    val cloudletList = List.tabulate(CLOUDLETS) { n =>
      val cloudlet = new CloudletSimple(CLOUD_LEN, CLOUD_PE)
      cloudlet.setUtilizationModel(utilizationModel)
      val targetVM = vmList.get(n % vmList.size)
      cloudlet.setVm(targetVM)
      cloudlet.setBroker(targetVM.getBroker)
      cloudlet
    }
    broker.submitCloudletList(cloudletList)
  }


  def printCosts(broker: DatacenterBroker) = {
    val executionTime = broker.getVmCreatedList.map({ vm =>
      new VmCost(vm).getVm.getTotalExecutionTime
    }).sum

    val totalCost = broker.getVmCreatedList.map({ vm =>
      new VmCost(vm).getTotalCost
    }).sum

    val processingCost = broker.getVmCreatedList.map({ vm =>
      new VmCost(vm).getProcessingCost
    }).sum

    val storageCost = broker.getVmCreatedList.map({ vm =>
      new VmCost(vm).getStorageCost
    }).sum

    val memoryCost = broker.getVmCreatedList.map({ vm =>
      new VmCost(vm).getMemoryCost
    }).sum

    val bandwidthCost = broker.getVmCreatedList.map({ vm =>
      new VmCost(vm).getBwCost
    }).sum

    logger.info("Total Execution Time- " + executionTime + " seconds")
    logger.info("Total Cost- " + totalCost + " units")
    logger.info("Total Processing Cost- " + processingCost + " units")
    logger.info("Total Storage Cost- " + processingCost + " units")
    logger.info("Total Memory Cost- " + memoryCost + " units")
    logger.info("Total Bandwidth Cost- " + bandwidthCost + " units")
  }
}



