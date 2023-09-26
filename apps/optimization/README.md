# Optimization Algorithm using Multi-Level Monte Carlo

The following file describes our optimization algorithm based on the Multi-Level Monte Carlo (MLMC) method. It provides an overview of the implemented functions required for the algorithm, along with additional modules used for testing purposes. The modules included in this file are as follows:

- **mlmcSim:** This module contains the main function (MLMC) and its dependencies.
- **sampleFunctions:** It offers various objective functions such as Ackley, Levy N.13, and Schwefel, which are commonly used for testing these types of problems. It also includes basic functions like a linear and an exponential function. These functions can be tested by passing them as the third parameter to the MLMC function.
- **convergenceTest:** This module verifies whether our implementation correctly identifies the appropriate minimum sample size for the Levy N.13, Ackley, and Schwefel functions, based on their theoretical results (https://www.sfu.ca/~ssurjano/index.html).
- **dimensionTest:** This module assesses the scalability of the problem by checking various dimensions.
- **granularityTest:** This module examines the scalability of the infrastructure.
- **marenostrumTest:** This script shows an example of an execution in MareNostrum4.

### Running the Application

Users can execute the application by running any of the provided test files (convergenceTest, dimensionTest, or granularityTest) as follows:

**Python3 execution**
```shell
python3 convergenceTest.py
```
**COMPSs local execution**
```shell
runcompss --pythonpath="/path/to/root/hp2cdt/apps/optimization/source":"/path/to/root/hp2cdt/apps/optimization/tests" convergenceTest.py

```
**MareNostrum4 Execution**
```shell
./marenostrumTest.sh
```
>**Note:** Remember to change path to root in marenostrumTest script.

However, new tests can be defined by specifying the following parameters for the MLMC method:

- Number of levels
- Variables: A list of objects comprising the boundaries (minimum and maximum values), type ('c' for continuous, 'd' for discrete), error, and splits in that dimension. Refer to the mainAv file for examples.
- Function: Refer to the mainAv module for examples of defining functions.
- splits_per_level (Optional): A fixed number of splits for each cell.
- lambda prune (Optional): Weighting factor for cell pruning.

MLMC returns the minimum f(x*) found and the corresponding x*.



