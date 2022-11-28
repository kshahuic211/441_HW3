# 441_HW3 
Karan Shah

Introduction

This homework deals with creating cloud simulators for evaluating executions of applications in cloud datacenters with different characteristics and deployment models using the the Cloudsim framework.

How to Run

The program can be run using sbt run and tested with sbt test, without any other requirements except for scala 3.0.2.
After giving command sbt run, there are two options given- 1) MultiDataCenter1 and 2) MultiDataCenter2
So any of the above two simulations can be run by entering 1 or 2 as needed

Project structure

The Aws-like architecture can be found in the Architecture directory

The five important files are CreateLogger.scala, MultiDataCenter1.scala, MultiDataCenter2.scala, SimulationsTest.scala and application.conf

- CreateLogger- helper for Creating logger
- MultiDataCenter1- contains all the code needed for running simulation 1
- MultiDataCenter2- contains all the code needed for running simulation 2
- SimulationsTest- performs testing of the config file and various simulation helper functions used in both simulations
- application.conf- contains various configuartion parameters for both the simulations like host and vm capacity and pricing

MultiDataCenter1 Description- This MultiDataCenter simulates multiple datacenters created across multiple time zones with cloudlets being run in different virtual machines allocated to their closest datacenters (vm allocation policy). Each datacenter has 2 hosts and each host should have 3 vms. A Ring network topology was used to connect the different datacenters with the broker.

MultiDataCenter2 Description- This MultiDataCenter simulates multiple datacenters created across multiple time zones with cloudlets being run in different virtual machines allocated using a round-robin policy. Each datacenter has 3 hosts and each host should have 2 vms. A Tree network topology was used to connect the different datacenters with the broker.

Both of them have policies in place to allocate more Vms (horizontal) when overuse gets detected

Analysis-

In order to statistically anaylze the two MultiDataCenters, four simulations have been run-

1a- Simulation with MultiDataCenter1 with time sharing and without cloudlet having inital targetVm

1b- Simulation with MultiDataCenter1 without time sharing but with cloudlet having inital targetVm

1a- Simulation with MultiDataCenter2 with time sharing and without cloudlet having inital targetVm

1a- Simulation with MultiDataCenter2 without time sharing but with cloudlet having inital targetVm

The executution summary of one run of these simulations can be found in the csv directory

1a Cost Summary- 
Total Execution Time- 14181.119999999992 seconds
Total Cost- 3197.755199999999 units
Total Processing Cost- 53.17920000000002 units
Total Storage Cost- 53.17920000000002 units

1b Cost Summary- 
Total Execution Time- 14400.96000000001 seconds
Total Cost- 3198.5796000000014 units
Total Processing Cost- 54.0036 units
Total Storage Cost- 54.0036 units

2a Cost Summary- 
Total Execution Time- 13104.960000000008 seconds
Total Cost- 3193.7195999999994 units
Total Processing Cost- 49.143599999999985 units
Total Storage Cost- 49.143599999999985 units

2b Cost Summary- 
Total Execution Time- 14513.580000000004 seconds
Total Cost- 3199.0019249999987 units
Total Processing Cost- 54.42592499999999 units
Total Storage Cost- 54.42592499999999 units

All Simulations-
Total Memory Cost- 1920.0 units
Total Bandwidth Cost- 1200.0 units

- First of all, we can see that the Memory Cost and the Bandwidth cost is the same for all simulations, something that we can expect in a simulation like this as the cloudlets assigned to all the simulations are similar
- Both the MultiDataCenters have certain properties which can make them execute faster or slower (using more resources) depending on the input configurations. For example, MultiDataCenter1's VM allocation policy is to assign that Vm to the closest data possible. So, if the cloudLets specify a target Vm in a particular order, the speed of the execution can slow down as it can seen in the results of 1b (another reason for the slower execution time of 1b is no time sharing). Although in these simulations, the cloudLets have been assigned in order to the Vms.
- As far as MultiDataCenter2 is considered, its policy is to allocate Vms based on Round-Robin algorithm. Again, if the cloudLets specify a target Vm, the speed of the execution will slow down as it can seen in the results of 2b with another reason for the slowdown being the lack of time-sharing.
- The interesting part is that 2a's execution time is the fastest, while 2b's execution time is the slowest. One reason for this can be the Topologies used to connect the datacenters. The policies used in these simulations will definitely favour the Tree network topology compared to the Ring network Topology, as connection time is slower for the Tree network topology, but the drawback of using it is the cost of having more complex connections. Another reason could be the fact that MultiDataCenter2 has more hosts but less vms per host as compared to MultiDataCenter1, which could make alloation faster.
- It should be noted that both of the polcies used in these simulations aren't completely ideal for getting the best-performance, as their Vm allocation policy doesn't make the full use of all the information that can be collected to perform better scheduling and allocation of Vms. Also, no one of the two can be said to perform better when considering all possible configurations.















