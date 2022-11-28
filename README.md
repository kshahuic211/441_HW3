# 441_HW3 Karan Shah

Introduction

This homework deals with creating cloud simulators for evaluating executions of applications in cloud datacenters with different characteristics and deployment models using the the Cloudsim framework.

How to Run

The program can be run using sbt run and tested with sbt test, without any other requirements except for scala 3.0.2
After giving command sbt run, there are two options given- 1) MultiDataCenter1 and 2) MultiDataCenter2
So any of the above two simulations can be run by entering 1 or 2 as needed

Project structure

The five important files are CreateLogger, MultiDataCenter1, MultiDataCenter2, SimulationsTest and application.conf

- CreateLogger- helper for Creating logger
- MultiDataCenter1- contains all the code needed for running simulation 1
- MultiDataCenter2- contains all the code needed for running simulation 2
- SimulationsTest- performs testing of the config file and various simulation helper functions used in both simulations
- application.conf- contains various configuartion parameters for both the simulations like host and vm capacity and pricing



The following Simulations are provided:

Simulation1: Simulation with Data Locality policy and Time Sharing.
Simulation1b: Simulation without Data Locality policy and Time Sharing.
Simulation2: Simulation with Data Locality policy and Space Sharing.
Simulation2b: Simulation without Data Locality policy and Space Sharing.
Simulation2low: Simulation with Data Locality policy, Space Sharing and reduced number of VMs, Hosts.
Simulation2blow: Simulation without Data Locality policy, Space Sharing and reduced number of VMs, Hosts.
Tests

The following test classes are provided:

Simulation1Test
MyBrokerTest
Simulation1Test

The following methods are tested:

createDatacenter: the test checks that the created Datacenter is not null and that it contains the correct number of hosts.
createDatacenters: the test checks that upon a recursive call to this method the right number of datacenters is created.
createHostImpl: the test checks that upon a recursive call to this method the right number of hosts is created.
createVM: the test checks that upon a recursive call to this method the right number of VMs is created.
createCloudlet: the test checks that upon a recursive call to this method, the cloudlets created are of the right type and number.
MyBrokerTest

The following methods are tested:

submitMapperList: the test checks that all mappers are actually submitted to the broker and that the CloudLet type (Mapper) is still correct when retrieved.
submitReducerList: the test checks that all reducers are actually submitted to the broker - and therefore queued for execution - and that the CloudLet type (Reducer) is still correct when retrieved.
processEvent: the test runs a mock simulation with 3 mappers and 1 reducer and tests for the behaviour of the implemented submission policy. In particular it is tested that once 3 mappers finish execution, a new reducer is started and ready for execution.
Configuration parameters

The use of hardcoded values is limited in the source code as they limit code reuse and readability; instead a configuration file contains all the configuration parameters for the simulations. The configuration file is "application.conf".

Configuration blocks are identified by curly braces and they can be nested if some configuration options are valid only within a certain context.

Configuration options for the following entities are provided:

Datacenter
Host
VM
Cloudlet
Those are valid in the context of a certain simulation.

Relevant parameters that are the same across all the simulations provided except Simulation2low and Simulation2blow:

Number of users: 1
Number of VMs: 50
Number of Datacenters: 2
Number of Mappers: 120
Number of hosts in datacenter 0: 5
Number of hosts in datacenter 1: 50
Max Number of Reducers: 120
Host disk delay (diskSpeed): 0.01 seconds (100 MB/s -> 1/100 seconds to transfer 1 MB of data)
Cloudlet file size in/out: 300 MB
Cloudlet length: 1000
Relevant parameters that are the same for Simulation2low and Simulation2blow:

Number of users: 1
Number of VMs: 10
Number of Datacenters: 2
Number of Mappers: 12
Number of hosts in datacenter 0: 5
Number of hosts in datacenter 1: 5
Max Number of Reducers: 12
Host disk delay (diskSpeed): 0.01 seconds (100 MB/s -> 1/100 seconds to transfer 1 MB of data)
Cloudlet file size in/out: 300 MB
Cloudlet length: 1000
Implemented policy

In this section the implemented policy is described in detail.

Firstly, it is important to note that a new parameter was added to the host entity, the disk speed. This allows us to model the delay in loading data from disk when starting, for example, a map/reduce job.

Upon submission we have that data chunks need to be transferred to the hosts in order to be processed. This data transfer is modeled through a delay that is added to the cloudlet startup time.

The implemented policy tried to reduce the delay caused by a limited disk speed by avoiding excessive data transfers between hosts; let's consider a scenario to better understand how this is implemented.

Let's say we have a map/reduce job running on the cloud, data is initially split in chunks and sent to the various mappers. (Mappers are a special type of cloudlets in this context) This causes a certain delay, indeed data needs to be transferred and the transfer speed is bounded by the disk speed.

After some mappers finish executing, we can now send their output to a reducer, however, two possible scenario may now occur:

The reducer is started on the same host as the mappers: In this case no data needs to be transferred (as it's already available on the local disk).
The reducer is started on a different host than at least one of the mappers: In this case data needs to be transferred among the hosts and the disk speed comes into play.
Therefore, the policy will try to allocate a reducer on the same host of the mappers in order to minimize the total execution time.

Implementation details

Delving deeper, the proposed implementation waits for at least two mappers to finish executing and then checks whether they run on the same host, if so, allocates a new reducer on that host on any available VM. Otherwise, these mappers are queued and a new iteration is performed; when the next two mappers finish running, it will now check if at least 2 among the 4 mappers have run on the same host.

Limitations

The policy in ineffective if all mappers are run on different hosts, furthermore, once all mappers have finished executing the remaining ones must be scheduled anyways, irrespective of their host assignment, this is suboptimal and therefore the delay is not avoided in this case.

Please see classes MyBroker.java and MyCloudlet.java.

Note: Please note that the number of reducers specified in the configuration file is only a maximum number and does not imply that all reducers will be scheduled. This is due to the fact that is multiple mappers all run on the same host a fewer number of reducers will be run. This is expected behaviour.

Analysis of results

In this section the results of the different simulations are reported and analyzed.

When making comparisons across different scenarios, one usually wants to focus on a specific parameter (instead of making multiple changes), so that the change in the output can be linked to the single change in the input. This helps understand how a specific parameter change on the input side produces output changes; in other words, we build an input/output relation. This is the approch followed as part of this homework.

Four main simulations are present: Simulation1 and Simulation2 use the Data Locality policy described above, while Simulation1b and Simulation2b have the same configuration of simulation 1 and 2 respectively, but with the default policy. Furthermore, Simulation1 uses a TimeShared cloudlet allocation policy, while Simulation2 uses a SpaceShared allocation policy.

Two additional simulations Simulation2low and Simulation2blow show the performance in a context with a reduced number of VMs and Hosts.

Metric: cost

When comparing the simulation performance it is relevant to choose an appropriate metric, it this context, the monetary cost is the chosen metric for the comparison. Indeed, this is a key factor of cloud computing and can effectively guide our choice of a cloud architecture vs. another.

The cost is computed as the cost per second multiplied by the actual run time of the cloudlet plus the time taken to transfer data between the cloudlets: cost_per_sec * (cpu_time + delay_data_loading)

The total cost of the main simulations is as follows:

Simulation1: 774,69 cost units
Simulation1b: 927,25 cost units
Simulation2: 640,20 cost units
Simulation2b: 816,00 cost units
The generated cloudlet schedule along with each individual cost is reported in the respective .csv files. (simulation1.csv,simulation1b.csv, simulation2.csv, simulation2b.csv)

Just by looking at the total cost number we immediately notice that Simulation2 yields the lowest cost across all the simulations; in particular both Simulation2 and Simulation2b which use a SpaceShared policy end up being more efficient than Simulation1 and Simulation1b.

Identical patterns are found for Simulation2low and Simulation2blow.

SpaceShared vs TimeShared

One can look at the individual costs of each cloudlet, it can be immediately noticed that costs in the TimeShared environment are higher than in the SpaceShared one; this is due to the fact that whenever there are not enough VMs to run all the cloudlets independently, the TimeShared policy allocates cloudlets on the same VM and runs than concurrently, therefore yelding to a higher cost. We can think of it as if the computational resources (i.e. CPUs) are used by the two cloudlets alternatively. (shared CPUs)

DataLocality policy vs default policy

Another pattern that can be consistently found in the results is that the cost of the simulations in which the data locality policy is applied is lower than those in which the default Cloudsim policy is used. This is a key factor.

This can be easily explained: In our simulation we assume that whenever the mapper(s) and the respective reducer (the one that will further process the output of the mapper) are run on the same host, no data transfer is required between the two and therefore we don't incur in any delay. On the other hand, when mapper(s) and reducer run on different hosts, we need to transfer data between, which, due to the limited disk speed, takes some time. (delay is present)

It is therefore reasonable - and this is the point of the whole implementation - to observe lower execution times (and lower costs) when the data locality policy is used as the default policy just allocates cloudlets on the first available VM. (Please see CloudSim source code for further details)

Please see Gantt charts (/charts) that show the schedules produced when using the Data Locality policy vs when not using it. It is clear how the delay in submitting the reducers (Green) is significantly lower when using the policy, given that no data transfer is necessary before the reduce job can be started. Mappers are reported in red in the charts.

CPU usage

In the scenario being analyzed the CPU is the bottleneck of the system when we don't incur in a disk loading delay. Also, we assume that after the data loading phase, data can be accessed instantly. This can be easily proved by looking at the equation that links the cloudlet length parameter and the mips of the VM on which the cloudlet runs, by dividing one by the other we obtain the execution time shown in the simulation results.

Therefore, CPU usage (given that it's the bottleneck) is always 100%.

Map/Reduce architecture

In this section the chosen map/reduce architecture is briefly explained.

Key details:

Chunks of equal size
2-layer architecture (mapper + reducer layer)
Dynamic number of reducers
Firstly, data is split among equal chunks of 300 MB each; each individual chunk is then fed to a mapper cloudlet. The mapper generates an intermediate result which needs to be processed by a reducer.

The architecture prescribes that reducers shall wait for at least two mappers to finish executing before starting to work on their outputs, however, in order to guarantee data locality principles, mappers can be queued and their output left in the intermediate state for longer. (For more details on how this works see the Policy implementation section)

Furthermore, the number of reducers is not fixed, but is adjusted at runtime. (the user only provides a maximum number of reducers they wish to run)

In principle, a reducer is submitted every two mappers, but less reducers may be submitted during the final stage when mappers for which data locality policies can't be applied are handled.
