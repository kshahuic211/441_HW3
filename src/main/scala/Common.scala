import org.cloudbus.cloudsim.allocationpolicies.{VmAllocationPolicyBestFit, VmAllocationPolicyRoundRobin, VmAllocationPolicySimple}
import org.cloudbus.cloudsim.brokers.{DatacenterBroker, DatacenterBrokerBestFit, DatacenterBrokerHeuristic, DatacenterBrokerSimple}
import org.cloudbus.cloudsim.core.CloudSim
import org.cloudbus.cloudsim.datacenters.{Datacenter, DatacenterSimple}
import org.cloudbus.cloudsim.distributions.UniformDistr
import org.cloudbus.cloudsim.hosts.{Host, HostSimple}
import org.cloudbus.cloudsim.resources.PeSimple
import org.cloudbus.cloudsim.schedulers.cloudlet.{CloudletSchedulerCompletelyFair, CloudletSchedulerSpaceShared, CloudletSchedulerTimeShared}
import org.cloudbus.cloudsim.schedulers.vm.{VmSchedulerSpaceShared, VmSchedulerTimeShared}
import org.cloudbus.cloudsim.vms.{Vm, VmCost, VmSimple}
import org.cloudsimplus.heuristics.CloudletToVmMappingSimulatedAnnealing
import org.slf4j.Logger

import scala.collection.JavaConverters.*

object Common {
  def createDataCenter(sim: CloudSim, count: Int, pe: Int, mips: Int, ram: Int, bw: Int, storage: Int, scheduler: String,vmAllocation:String): DatacenterSimple = {
    val hostList = List.fill(count) {
      createHost(pe, mips, ram, bw, storage, scheduler)
    }
    val datacenterSimple = new DatacenterSimple(sim, hostList.asJava,vmAllocation match {
      case "best" => new VmAllocationPolicyBestFit()
      case "round" => new VmAllocationPolicyRoundRobin()
      case "simple" => new VmAllocationPolicySimple()
      case default => new VmAllocationPolicySimple()
    })

    return datacenterSimple
  }

  def addCharacteristics(dc: Datacenter, cpu: Double, mem: Double, bw: Double, storage: Double) = {
    dc.getCharacteristics
      .setCostPerSecond(cpu)
      .setCostPerMem(mem)
      .setCostPerStorage(storage)
      .setCostPerBw(bw)
  }

  def createHost(pe: Int, mips: Int, ram: Int, bw: Int, storage: Int, scheduler: String): Host = {
    val peList = List.fill(pe) {
      new PeSimple(mips)
    }
    return new HostSimple(ram,
      bw,
      storage,
      (peList).asJava).setVmScheduler(scheduler match {
      case "space" => new VmSchedulerSpaceShared()
      case "time" => new VmSchedulerTimeShared()
    })
  }

  def createBroker(sim: CloudSim,brokerFit:String): DatacenterBroker = {
    val broker = brokerFit match {
      case "simple" => new DatacenterBrokerSimple(sim)
      case "best" => new DatacenterBrokerBestFit(sim)
      case "annealing" => {
        val SA_INITIAL_TEMPERATURE = 1.0;
        val SA_COLD_TEMPERATURE = 0.0001;
        val SA_COOLING_RATE = 0.003;
        val SA_NUMBER_OF_NEIGHBORHOOD_SEARCHES = 50;
        val heuristic = new CloudletToVmMappingSimulatedAnnealing(SA_INITIAL_TEMPERATURE, new UniformDistr(0, 1));
        heuristic.setColdTemperature(SA_COLD_TEMPERATURE)
        heuristic.setCoolingRate(SA_COOLING_RATE)
        heuristic.setNeighborhoodSearchesByIteration(SA_NUMBER_OF_NEIGHBORHOOD_SEARCHES)
        val broker = new DatacenterBrokerHeuristic(sim)
        broker.setHeuristic(heuristic)
        broker
      }
    }
    return broker
  }

  def createVMs(count: Int, mips: Int, pe: Int, ram: Int, bw: Int, storage: Int, scheduler: String): List[Vm] = {
    val vmList = List.fill(count) {
      val vm = new VmSimple(mips, pe)
        .setCloudletScheduler(scheduler match {
          case "space" => new CloudletSchedulerSpaceShared()
          case "time" => new CloudletSchedulerTimeShared()
          case "fair" => new CloudletSchedulerCompletelyFair()
        })
      vm.setRam(ram).setBw(bw).setSize(storage)
      vm
    }
    return vmList
  }

  def printCosts(broker: DatacenterBroker,logger: Logger) = {
    var totalCost = 0.0
    var totalNonIdleVms = 0
    var processingTotalCost = 0.0
    var memoryTotaCost: Double = 0
    var storageTotalCost: Double = 0
    var bwTotalCost: Double = 0
    var totalExecutionTime: Double = 0
    broker.getVmCreatedList.forEach({ vm =>
      val cost: VmCost = new VmCost(vm)
      processingTotalCost = processingTotalCost + cost.getProcessingCost
      memoryTotaCost = memoryTotaCost + cost.getMemoryCost
      storageTotalCost = storageTotalCost + cost.getStorageCost
      bwTotalCost = bwTotalCost + cost.getBwCost
      totalCost = totalCost + cost.getTotalCost
      totalExecutionTime = totalExecutionTime+ cost.getVm.getTotalExecutionTime
      totalNonIdleVms = totalNonIdleVms + (if (vm.getTotalExecutionTime > 0) 1 else 0)
    })
    logger.info(s"Total execution time = ${totalExecutionTime} seconds")
    logger.info(s"Total cost = ${totalCost} cost units")
    //    logger.info(s"Total non idle vms = ${totalNonIdleVms}")
    //    logger.info(s"Total processing cost = ${processingTotalCost}")
    //    logger.info(s"Total memory cost = ${memoryTotaCost}")
    //    logger.info(s"Total BW cost = ${bwTotalCost}")
    //    logger.info(s"Total storage cost = ${storageTotalCost}")
  }
}
