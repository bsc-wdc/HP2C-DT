#  Copyright 2002-2023 Barcelona Supercomputing Center (www.bsc.es)

#   Licensed under the Apache License, Version 2.0 (the "License");
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at

#       http://www.apache.org/licenses/LICENSE-2.0

#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an "AS IS" BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.


"""Convergence test file.

This module verifies whether our implementation correctly identifies the appropriate minimum 
sample size for the Levy N.13, Ackley, and Schwefel functions, based on their theoretical results.
"""


import sys
import time

sys.path.append("../source")

from sampleFunctions import levy, schwefel, ackley, createVariables
from mlmcSim import MLMC


print("Convergence test:")
t = time.time()
var = createVariables(1)
opt_levy, min_sample_levy = MLMC(10, var, levy, splits_per_level=2, lambda_prune=1)
print("Optimum:", opt_levy)
print("Minimum Sample:", min_sample_levy)
print("Execution time:", time.time() - t)
