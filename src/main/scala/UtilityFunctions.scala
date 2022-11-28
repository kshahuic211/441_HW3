import ch.qos.logback.classic.Level
import org.cloudbus.cloudsim.allocationpolicies.VmAllocationPolicy
import org.cloudbus.cloudsim.brokers.DatacenterBroker
import org.cloudbus.cloudsim.datacenters.Datacenter
import org.cloudbus.cloudsim.schedulers.cloudlet.CloudletScheduler
import org.cloudsimplus.util.Log

object UtilityFunctions {
  def configureLogs(): Unit = { //Enables just some level of log messages for all entities.
    //    Log.setLevel(Level.INFO)
    //Enable different log levels for specific classes of objects
    Log.setLevel(DatacenterBroker.LOGGER, Level.WARN)
    Log.setLevel(Datacenter.LOGGER, Level.WARN)
    Log.setLevel(VmAllocationPolicy.LOGGER, Level.OFF)
    Log.setLevel(CloudletScheduler.LOGGER, Level.ERROR)
  }

  def switchOffCloudSimLogs(): Unit = {
    Log.setLevel(Level.INFO)
    //Enable different log levels for specific classes of objects
    Log.setLevel(DatacenterBroker.LOGGER, Level.ERROR)
    Log.setLevel(Datacenter.LOGGER, Level.ERROR)
    Log.setLevel(VmAllocationPolicy.LOGGER, Level.ERROR)
    Log.setLevel(CloudletScheduler.LOGGER, Level.ERROR)

  }

}