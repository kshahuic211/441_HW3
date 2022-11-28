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

// This program simulates multiple datacenters distributed across different time zones connected using Ring Topology
// Vms are allocated as close to their DataCenters as possible
// The hosts are connected to each other in the datacenters using Edge switches

object MultiDataCenter1 extends App {
  /**
   * The percentage of host CPU usage that trigger VM migration
   * due to under utilization (in scale from 0 to 1, where 1 is 100%).
   */

  /**
   * The percentage of host CPU usage that trigger VM migration
   * due to over utilization (in scale from 0 to 1, where 1 is 100%).
   */

  val applicationConf: Config = ConfigFactory.load("application.conf")
  val logger = CreateLogger(classOf[MultiDataCenter1.type])

  val HOST_OVER_UTILIZATION_THRESHOLD_FOR_VM_MIGRATION = applicationConf.getDouble("DataCenter1.host_over_util")
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
  val HOST_RAM = applicationConf.getInt("hostConfig.host.RAM")
  val HOST_STORAGE = applicationConf.getInt("hostConfig.host.Storage")
  val HOST_BW = applicationConf.getInt("hostConfig.host.Bandwidth")
  val VM_SCHEDULER = applicationConf.getBoolean("hostConfig.host.scheduling")
  val DATA_LOCALITY_ENABLED = applicationConf.getBoolean("hostConfig.host.locality")


  val VM_PE = applicationConf.getInt("hostConfig.vm.PE")
  val VM_MIP_CAPACITY = applicationConf.getInt("hostConfig.vm.mipsCapacity")
  val VM_RAM = applicationConf.getInt("hostConfig.vm.RAM")
  val VM_BW = applicationConf.getInt("hostConfig.vm.Bandwidth")

  val CLOUDLETS = applicationConf.getInt("DataCenter1.cloudLets")
  val CLOUD_LEN = applicationConf.getInt("DataCenter1.cloudLet.length")
  val CLOUD_PE = applicationConf.getInt("DataCenter1.cloudLet.pe")

  val CPU_COST = applicationConf.getDouble("DataCenter1.cost.cpu")
  val MEM_COST = applicationConf.getDouble("DataCenter1.cost.mem")
  val STORAGE_COST = applicationConf.getDouble("DataCenter1.cost.storage")
  val BW_COST = applicationConf.getDouble("DataCenter1.cost.bw")


  // calls startSimulation()
  startSimulation();

  def startSimulation(): Unit = {
    logger.info("The Simulation of MultiDataCenter1 has started")

    val simulation = new CloudSim()
    
    val broker0 = createBroker(simulation)
    logger.info("The broker for MultiDataCenter1 has been created")
    broker0.setSelectClosestDatacenter(true)  // assigns vms to the closest possible datacenter

    val datacenterList = createDatacenters(simulation)
    // creates the Network Topology
    configureNetwork(simulation, datacenterList, broker0)

    val vmList = createAndSubmitVms(broker0)
    createAndSubmitCloudlets(broker0, vmList)

    //simulation.terminateAt(SIMULATION_TIME)  // simulation can be terminated after certain time
    simulation.start()

    val finishedCloudlet = broker0.getCloudletFinishedList();
    new CloudletsTableBuilder(finishedCloudlet).build();
    printCosts(broker0);
    logger.info("The Simulation of MultiDataCenter1 has ended")
  }

  //Creates and returns simple broker
  def createBroker(simulation: CloudSim): DatacenterBroker = {
    return new DatacenterBrokerSimple(simulation)
  }

  // creates all the datacenters (one per timezone)
  def createDatacenters(simulation: CloudSim): List[NetworkDatacenter] = {
    val datacenterList = TIME_ZONES.map(timeZone => {
      logger.info("Creating Hosts for TimeZone " + timeZone)
      val hostList = createHosts()
      val allocationPolicy = new VmAllocationPolicyMigrationStaticThreshold(
          new VmSelectionPolicyMinimumUtilization(),
          HOST_OVER_UTILIZATION_THRESHOLD_FOR_VM_MIGRATION);
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

  // creates all the hosts for a datacenter
  def createHosts() : List[Host] = {
    return List.fill(HOST_COUNT) {
      val pes = List.fill(HOST_PE) {
        new PeSimple(HOST_MIP_CAPACITY)
      }
      val networkHost = new NetworkHost(HOST_RAM, HOST_BW, HOST_STORAGE, pes.asJava)
      if (VM_SCHEDULER) {  // if time sharing is used or not
        networkHost.setVmScheduler(new VmSchedulerTimeShared())
      } else {
        networkHost.setVmScheduler(new VmSchedulerSpaceShared())
      }
      networkHost
    }
  }

  // connects all the hosts of a datacenter to the dataCenter using a switch
  def createHostNetwork(networkDatacenter: NetworkDatacenter, simulation: CloudSim): Unit = {
    val edgeSwitch = new EdgeSwitch(simulation, networkDatacenter)
    networkDatacenter.addSwitch(edgeSwitch)
    networkDatacenter.getHostList[NetworkHost].forEach(host => {
      edgeSwitch.connectHost(host)
    })
  }

  // creates the Ring Network Topology
  def configureNetwork(simulation: CloudSim, datacenterList: List[NetworkDatacenter], broker0: DatacenterBroker): Unit = {
    val networkTopology = new BriteNetworkTopology()
    simulation.setNetworkTopology(networkTopology)
    // Add a link between each datacenter1 and broker
    networkTopology.addLink(broker0, datacenterList(0), DC_NETWORK_BANDWIDTH, BROKER_NETWORK_LATENCY)
    // Add links between the 4 datacenters
    networkTopology.addLink(datacenterList(0), datacenterList(1), DC_NETWORK_BANDWIDTH, DC_INTERNAL_NETWORK_LATENCY)
    networkTopology.addLink(datacenterList(1), datacenterList(2), DC_NETWORK_BANDWIDTH, DC_INTERNAL_NETWORK_LATENCY)
    networkTopology.addLink(datacenterList(2), datacenterList(3), DC_NETWORK_BANDWIDTH, DC_INTERNAL_NETWORK_LATENCY)
    networkTopology.addLink(datacenterList(3), datacenterList(0), DC_NETWORK_BANDWIDTH, DC_INTERNAL_NETWORK_LATENCY)
  }


  // handler for migration
  private def startMigration(info: VmHostEventInfo): Unit = {
    logger.info("Migration Started")
  }

  // function for creating a NetworkVm
  def createVm(timezone : Double) = {
    new NetworkVm(VM_MIP_CAPACITY, VM_PE).setCloudletScheduler(new CloudletSchedulerSpaceShared())
    .setRam(VM_RAM).setBw(VM_BW)
    .setTimeZone(timezone)
    .addOnMigrationStartListener(startMigration)
  }

  // creates VMs_PER_HOST*HOST_COUNT vms for each dataCenter and submits them
  def createAndSubmitVms(broker:DatacenterBroker) = {
    val vmList = new ListBuffer[NetworkVm]()
    TIME_ZONES.map(timeZone => {
      val vms = List.tabulate(VMs_PER_HOST*HOST_COUNT) { n =>
        val vm = createVm(timeZone)
        vmList.add(vm.asInstanceOf[NetworkVm])
        createHorizontalVmScaling(vm);
        vm
      }
      logger.info("Inside of createandSubmitVms. Created " +  vms.size + " vms for TimeZone " + timeZone)
      broker.submitVmList(vms.asJava)
    })
    vmList.toList
  }

  // predicate for scaling
  def isVmOverloaded(vm: Vm) = {
    vm.getCpuPercentUtilization > 0.7
  }


  // supplier of vms for scaling
  def createVm1() = {
    new NetworkVm(VM_MIP_CAPACITY, VM_PE).setCloudletScheduler(new CloudletSchedulerSpaceShared())
      .setRam(VM_RAM).setBw(VM_BW)
      .addOnMigrationStartListener(startMigration)
  }

  // adds scaling mechanism to a vm
 def createHorizontalVmScaling(vm: Vm): Unit = {
    val horizontalScaling = new HorizontalVmScalingSimple();
    horizontalScaling.setVmSupplier(() => createVm1()).setOverloadPredicate(isVmOverloaded);
    vm.setHorizontalScaling(horizontalScaling);
  }

  // creates cloudlets inorder and submits them
  def createAndSubmitCloudlets(broker:DatacenterBroker,vmList:List[NetworkVm]) ={
    val utilizationModel: UtilizationModelDynamic = new UtilizationModelDynamic(INIT_UTILIZATION_RATIO).setMaxResourceUtilization(MAX_UTILIZATION_RATIO)
    val cloudletList = List.tabulate(CLOUDLETS) { n =>
      val cloudlet = new CloudletSimple(CLOUD_LEN, CLOUD_PE)
      cloudlet.setUtilizationModel(utilizationModel)
      if (DATA_LOCALITY_ENABLED) { // whether the cloudlets have a targetVm or not can be specified
        val targetVM = vmList.get(n % vmList.size)
        cloudlet.setVm(targetVM)
        cloudlet.setBroker(targetVM.getBroker)
      }
      cloudlet
    }
    broker.submitCloudletList(cloudletList)
  }
  
  // prints the execution time and the cost summary
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



